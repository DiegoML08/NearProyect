package com.near.api.modules.chat.service;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.chat.document.Conversation;
import com.near.api.modules.chat.document.Conversation.ConversationStatus;
import com.near.api.modules.chat.document.Conversation.ParticipantRole;
import com.near.api.modules.chat.document.Message;
import com.near.api.modules.chat.document.Message.MediaLockStatus;
import com.near.api.modules.chat.document.Message.MessageStatus;
import com.near.api.modules.chat.document.Message.MessageType;
import com.near.api.modules.chat.document.Message.SystemEventType;
import com.near.api.modules.chat.dto.request.SendMediaMessageRequest;
import com.near.api.modules.chat.dto.request.SendMessageRequest;
import com.near.api.modules.chat.dto.response.ConversationListResponse;
import com.near.api.modules.chat.dto.response.ConversationResponse;
import com.near.api.modules.chat.dto.response.MessageResponse;
import com.near.api.modules.chat.repository.ConversationRepository;
import com.near.api.modules.chat.repository.MessageRepository;
import com.near.api.modules.wallet.dto.response.TransactionResponse;
import com.near.api.modules.wallet.service.WalletService;
import com.near.api.shared.exception.BadRequestException;
import com.near.api.shared.exception.ResourceNotFoundException;
import com.near.api.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.near.api.modules.notification.service.NotificationService;

import com.near.api.modules.request.entity.RequestMedia;
import com.near.api.modules.request.repository.RequestMediaRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final RequestMediaRepository requestMediaRepository;

    // Duración de la conversación: 5 horas
    private static final Duration CONVERSATION_DURATION = Duration.ofHours(5);

    // Tiempo antes de expirar para notificar: 1 hora
    private static final Duration EXPIRATION_WARNING_TIME = Duration.ofHours(1);

    // === Conversaciones ===

    @Override
    @Transactional
    public ConversationResponse createConversation(UUID requestId, UUID requesterId, UUID responderId,
                                                   Integer originalRewardNears) {
        // Verificar que no exista ya una conversación para esta request
        if (conversationRepository.existsByRequestId(requestId)) {
            throw new BadRequestException("Ya existe una conversación para esta request");
        }

        // Obtener usuarios
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester no encontrado"));
        User responder = userRepository.findById(responderId)
                .orElseThrow(() -> new ResourceNotFoundException("Responder no encontrado"));

        // Calcular expiración
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CONVERSATION_DURATION);

        // Crear participantes
        List<Conversation.Participant> participants = new ArrayList<>();

        participants.add(Conversation.Participant.builder()
                .userId(requesterId)
                .role(ParticipantRole.REQUESTER)
                .isAnonymous(requester.getIsAnonymous())
                .displayName(getDisplayName(requester))
                .profilePhotoUrl(requester.getIsAnonymous() ? null : requester.getProfilePhotoUrl())
                .unreadCount(0)
                .tipsEnabled(true)
                .build());

        participants.add(Conversation.Participant.builder()
                .userId(responderId)
                .role(ParticipantRole.RESPONDER)
                .isAnonymous(responder.getIsAnonymous())
                .displayName(getDisplayName(responder))
                .profilePhotoUrl(responder.getIsAnonymous() ? null : responder.getProfilePhotoUrl())
                .unreadCount(0)
                .tipsEnabled(true)
                .build());

        // Crear metadata
        Conversation.Metadata metadata = Conversation.Metadata.builder()
                .totalMessages(0)
                .totalMedia(0)
                .totalNearsTransferred(0)
                .originalRewardNears(originalRewardNears)
                .build();

        // Crear conversación
        Conversation conversation = Conversation.builder()
                .requestId(requestId)
                .participants(participants)
                .status(ConversationStatus.ACTIVE)
                .metadata(metadata)
                .expiresAt(expiresAt)
                .build();

        conversation = conversationRepository.save(conversation);

        log.info("Conversación creada: {} para request {} entre {} y {}",
                conversation.getId(), requestId, requesterId, responderId);

        // Enviar mensaje del sistema
        sendSystemMessage(conversation.getId(), SystemEventType.CONVERSATION_STARTED,
                "La conversación estará disponible por 5 horas");

        try {
            List<RequestMedia> requestMediaList = requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(requestId);

            for (RequestMedia media : requestMediaList) {
                // Determinar tipo de mensaje según tipo de media
                MessageType msgType = media.getMediaType() == RequestMedia.MediaType.VIDEO
                        ? MessageType.VIDEO
                        : MessageType.IMAGE;

                // Generar thumbnail si no existe
                String thumbnailUrl = media.getThumbnailUrl();
                if (thumbnailUrl == null && media.getUrl() != null) {
                    if (msgType == MessageType.IMAGE) {
                        thumbnailUrl = media.getUrl().replace("/upload/", "/upload/c_thumb,w_400,h_400/");
                    } else {
                        thumbnailUrl = media.getUrl()
                                .replace("/upload/", "/upload/c_thumb,w_400/")
                                .replace(".mp4", ".jpg")
                                .replace(".mov", ".jpg");
                    }
                }

                // Crear objeto Media para el mensaje
                Message.Media messageMedia = Message.Media.builder()
                        .url(media.getUrl())
                        .publicId(media.getPublicId())
                        .thumbnailUrl(thumbnailUrl)
                        .blurredUrl(null) // No blurred — es contenido gratuito del delivery
                        .mediaType(msgType == MessageType.IMAGE
                                ? Message.MediaType.IMAGE
                                : Message.MediaType.VIDEO)
                        .sizeBytes(media.getFileSizeBytes())
                        .width(media.getWidth())
                        .height(media.getHeight())
                        .durationSeconds(media.getDurationSeconds())
                        .priceNears(0)
                        .lockStatus(MediaLockStatus.UNLOCKED)
                        .build();

                // Crear el mensaje — el sender es el RESPONDER (quien entregó el contenido)
                Message mediaMessage = Message.builder()
                        .conversationId(conversation.getId())
                        .senderId(responderId)
                        .messageType(msgType)
                        .content(Message.Content.builder()
                                .text(null)
                                .media(messageMedia)
                                .build())
                        .status(MessageStatus.SENT)
                        .hasTip(false)
                        .tipAmount(0)
                        .expiresAt(conversation.getExpiresAt())
                        .build();

                messageRepository.save(mediaMessage);

                // Actualizar metadata de la conversación
                updateConversationAfterMessage(conversation, mediaMessage, responderId);
                conversationRepository.incrementMediaCount(conversation.getId());
            }

            if (!requestMediaList.isEmpty()) {
                log.info("Se insertaron {} archivos multimedia de la request como mensajes iniciales en conversación {}",
                        requestMediaList.size(), conversation.getId());
            }

        } catch (Exception e) {
            log.error("Error insertando media de la request en la conversación {}: {}",
                    conversation.getId(), e.getMessage());
            // No lanzamos excepción para no afectar la creación de la conversación
        }

        return mapToConversationResponse(conversation, requesterId);
    }

    @Override
    public ConversationResponse getConversationById(String conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        validateParticipant(conversation, userId);

        return mapToConversationResponse(conversation, userId);
    }

    @Override
    public ConversationResponse getConversationByRequestId(UUID requestId, UUID userId) {
        Conversation conversation = conversationRepository.findByRequestId(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada para esta request"));

        validateParticipant(conversation, userId);

        return mapToConversationResponse(conversation, userId);
    }

    @Override
    public Page<ConversationListResponse> getMyConversations(UUID userId, Pageable pageable) {
        Page<Conversation> conversations = conversationRepository.findByParticipantUserId(userId, pageable);

        List<ConversationListResponse> responses = conversations.getContent().stream()
                .map(conv -> mapToConversationListResponse(conv, userId))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, conversations.getTotalElements());
    }

    @Override
    public List<ConversationListResponse> getActiveConversations(UUID userId) {
        List<Conversation> conversations = conversationRepository.findActiveConversationsByUserId(userId);

        return conversations.stream()
                .map(conv -> mapToConversationListResponse(conv, userId))
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadConversationsCount(UUID userId) {
        return conversationRepository.countWithUnreadMessages(userId);
    }

    // === Mensajes ===

    @Override
    @Transactional
    public MessageResponse sendTextMessage(String conversationId, UUID senderId, SendMessageRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        validateParticipant(conversation, senderId);

        // Verificar propinas
        Integer tipAmount = request.getTipAmount() != null ? request.getTipAmount() : 0;
        UUID tipTransactionId = null;

        if (tipAmount > 0) {
            // Verificar si el destinatario tiene propinas habilitadas
            Conversation.Participant recipient = getOtherParticipant(conversation, senderId);
            if (!recipient.getTipsEnabled()) {
                throw new BadRequestException("El usuario no acepta propinas");
            }

            // Verificar saldo y procesar propina
            if (!walletService.hasEnoughBalance(senderId, BigDecimal.valueOf(tipAmount))) {
                throw new BadRequestException("Saldo insuficiente para la propina");
            }

            // Procesar transferencia de propina
            tipTransactionId = processsTipTransfer(senderId, recipient.getUserId(), tipAmount, conversationId);
        }

        // Crear mensaje
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageType(MessageType.TEXT)
                .content(Message.Content.builder()
                        .text(request.getText())
                        .build())
                .status(MessageStatus.SENT)
                .hasTip(tipAmount > 0)
                .tipAmount(tipAmount)
                .tipTransactionId(tipTransactionId)
                .expiresAt(conversation.getExpiresAt())
                .build();

        message = messageRepository.save(message);

        // Actualizar conversación
        updateConversationAfterMessage(conversation, message, senderId);

        // Notificar vía WebSocket
        MessageResponse response = mapToMessageResponse(message, conversation, senderId);
        notifyNewMessage(conversationId, response);

        try {
            Conversation.Participant otherParticipant = getOtherParticipant(conversation, senderId);
            String senderName = getSenderDisplayName(conversation, senderId);

            notificationService.notifyNewMessage(
                    otherParticipant.getUserId(),
                    conversationId,
                    senderName,
                    request.getText(),
                    false
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación push NEW_MESSAGE: {}", e.getMessage());
        }

        log.info("Mensaje de texto enviado: {} en conversación {} por usuario {}",
                message.getId(), conversationId, senderId);

        return response;
    }

    @Override
    @Transactional
    public MessageResponse sendMediaMessage(String conversationId, UUID senderId, SendMediaMessageRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        validateParticipant(conversation, senderId);

        // Validar que solo el responder puede cobrar por media
        Integer priceNears = request.getPriceNears() != null ? request.getPriceNears() : 0;
        if (priceNears > 0) {
            Conversation.Participant sender = conversation.getParticipantByUserId(senderId);
            if (sender.getRole() != ParticipantRole.RESPONDER) {
                throw new BadRequestException("Solo el responder puede cobrar por contenido multimedia");
            }

            // Validar que el precio no exceda la recompensa original
            if (priceNears > conversation.getMetadata().getOriginalRewardNears()) {
                throw new BadRequestException("El precio no puede exceder la recompensa original de la request");
            }
        }

        // Verificar propinas
        Integer tipAmount = request.getTipAmount() != null ? request.getTipAmount() : 0;
        UUID tipTransactionId = null;

        if (tipAmount > 0) {
            Conversation.Participant recipient = getOtherParticipant(conversation, senderId);
            if (!recipient.getTipsEnabled()) {
                throw new BadRequestException("El usuario no acepta propinas");
            }

            if (!walletService.hasEnoughBalance(senderId, BigDecimal.valueOf(tipAmount))) {
                throw new BadRequestException("Saldo insuficiente para la propina");
            }

            tipTransactionId = processsTipTransfer(senderId, recipient.getUserId(), tipAmount, conversationId);
        }

        // Determinar estado de bloqueo
        MediaLockStatus lockStatus = priceNears > 0 ? MediaLockStatus.LOCKED : MediaLockStatus.UNLOCKED;

        // Crear contenido multimedia
        Message.Media media = Message.Media.builder()
                .url(request.getUrl())
                .publicId(request.getPublicId())
                .thumbnailUrl(request.getThumbnailUrl())
                .blurredUrl(request.getBlurredUrl())
                .mediaType(Message.MediaType.valueOf(request.getMediaType().name()))
                .sizeBytes(request.getSizeBytes())
                .width(request.getWidth())
                .height(request.getHeight())
                .durationSeconds(request.getDurationSeconds())
                .priceNears(priceNears)
                .lockStatus(lockStatus)
                .build();

        // Determinar tipo de mensaje
        MessageType messageType = request.getMediaType() == SendMediaMessageRequest.MediaType.IMAGE
                ? MessageType.IMAGE
                : MessageType.VIDEO;

        // Crear mensaje
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageType(messageType)
                .content(Message.Content.builder()
                        .text(request.getCaption())
                        .media(media)
                        .build())
                .status(MessageStatus.SENT)
                .hasTip(tipAmount > 0)
                .tipAmount(tipAmount)
                .tipTransactionId(tipTransactionId)
                .expiresAt(conversation.getExpiresAt())
                .build();

        message = messageRepository.save(message);

        // Actualizar conversación
        updateConversationAfterMessage(conversation, message, senderId);
        conversationRepository.incrementMediaCount(conversationId);

        // Notificar vía WebSocket
        MessageResponse response = mapToMessageResponse(message, conversation, senderId);
        notifyNewMessage(conversationId, response);

        try {
            Conversation.Participant otherParticipant = getOtherParticipant(conversation, senderId);
            String senderName = getSenderDisplayName(conversation, senderId);
            String mediaType = request.getMediaType() != null ? request.getMediaType().name() : "IMAGE";

            notificationService.notifyNewMessage(
                    otherParticipant.getUserId(),
                    conversationId,
                    senderName,
                    mediaType,
                    true
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación push NEW_MESSAGE: {}", e.getMessage());
        }

        log.info("Mensaje multimedia enviado: {} en conversación {} por usuario {} (precio: {} Nears)",
                message.getId(), conversationId, senderId, priceNears);

        return response;
    }

    /**
     * Obtiene el nombre para mostrar del sender en una conversación.
     */
    private String getSenderDisplayName(Conversation conversation, UUID senderId) {
        if (conversation == null || senderId == null) {
            return "Usuario";
        }

        // Buscar el participante que envía el mensaje
        Conversation.Participant sender = null;
        for (Conversation.Participant p : conversation.getParticipants()) {
            if (p.getUserId().equals(senderId)) {
                sender = p;
                break;
            }
        }

        if (sender == null) {
            return "Usuario";
        }

        // Si el participante está en modo anónimo
        if (sender.getIsAnonymous() != null && sender.getIsAnonymous()) {
            return "Usuario anónimo";
        }

        // Usar el displayName del participante
        if (sender.getDisplayName() != null && !sender.getDisplayName().isEmpty()) {
            String[] parts = sender.getDisplayName().split(" ");
            return parts[0]; // Solo el primer nombre
        }

        return "Usuario";
    }

    @Override
    @Transactional
    public MessageResponse unlockMedia(String conversationId, String messageId, UUID userId) {
        Conversation conversation = getActiveConversation(conversationId);
        validateParticipant(conversation, userId);

        // Obtener mensaje
        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje no encontrado"));

        // Validar que es un mensaje multimedia con precio
        if (!message.isMediaMessage() || !message.hasPaidMedia()) {
            throw new BadRequestException("Este mensaje no tiene contenido de pago");
        }

        // Validar que está bloqueado
        if (!message.hasLockedMedia()) {
            throw new BadRequestException("Este contenido ya fue desbloqueado");
        }

        // Validar que no es el propio sender
        if (message.getSenderId().equals(userId)) {
            throw new BadRequestException("No puedes desbloquear tu propio contenido");
        }

        Integer price = message.getContent().getMedia().getPriceNears();

        // Verificar saldo
        if (!walletService.hasEnoughBalance(userId, BigDecimal.valueOf(price))) {
            throw new BadRequestException("Saldo insuficiente para desbloquear este contenido");
        }

        // Procesar pago
        UUID transactionId = processsMediaUnlockPayment(userId, message.getSenderId(), price, conversationId, messageId);

        // Desbloquear media
        messageRepository.unlockMedia(messageId, userId, Instant.now(), transactionId);

        // Actualizar conversación
        conversationRepository.addNearsTransferred(conversationId, price);

        // Obtener mensaje actualizado
        message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje no encontrado"));

        // Enviar mensaje del sistema
        sendSystemMessage(conversationId, SystemEventType.MEDIA_UNLOCKED,
                "Contenido desbloqueado por " + price + " Nears");

        // Notificar vía WebSocket
        MessageResponse response = mapToMessageResponse(message, conversation, userId);
        notifyMediaUnlocked(conversationId, messageId, response);

        log.info("Media desbloqueada: mensaje {} por usuario {} ({} Nears)",
                messageId, userId, price);

        return response;
    }

    @Override
    public Page<MessageResponse> getMessages(String conversationId, UUID userId, Pageable pageable) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        validateParticipant(conversation, userId);

        Page<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        List<MessageResponse> responses = messages.getContent().stream()
                .map(msg -> mapToMessageResponse(msg, conversation, userId))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, messages.getTotalElements());
    }

    @Override
    @Transactional
    public void markConversationAsRead(String conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        validateParticipant(conversation, userId);

        Instant now = Instant.now();

        // Marcar todos los mensajes como leídos
        messageRepository.markAllAsRead(conversationId, userId, now);

        // Resetear contador de no leídos
        conversationRepository.markAsRead(conversationId, userId, now);

        // Notificar al otro usuario que los mensajes fueron leídos
        notifyMessagesRead(conversationId, userId);

        log.debug("Conversación {} marcada como leída por usuario {}", conversationId, userId);
    }

    @Override
    @Transactional
    public void markMessageAsRead(String conversationId, String messageId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        validateParticipant(conversation, userId);

        messageRepository.markAsRead(messageId, Instant.now());
    }

    // === Configuración ===

    @Override
    @Transactional
    public void updateTipsEnabled(String conversationId, UUID userId, Boolean enabled) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        validateParticipant(conversation, userId);

        conversationRepository.updateTipsEnabled(conversationId, userId, enabled);

        log.info("Usuario {} {} propinas en conversación {}",
                userId, enabled ? "habilitó" : "deshabilitó", conversationId);
    }

    // === Mensajes del sistema ===

    @Override
    @Transactional
    public MessageResponse sendSystemMessage(String conversationId, SystemEventType eventType, String eventData) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(null) // Mensajes del sistema no tienen sender
                .messageType(MessageType.SYSTEM)
                .content(Message.Content.builder()
                        .systemEvent(Message.SystemEvent.builder()
                                .eventType(eventType)
                                .eventData(eventData)
                                .build())
                        .build())
                .status(MessageStatus.SENT)
                .hasTip(false)
                .tipAmount(0)
                .expiresAt(conversation.getExpiresAt())
                .build();

        message = messageRepository.save(message);

        // Notificar vía WebSocket
        MessageResponse response = mapToMessageResponse(message, conversation, null);
        notifyNewMessage(conversationId, response);

        return response;
    }

    // === Tareas programadas ===

    @Override
    @Scheduled(fixedRate = 60000) // Cada minuto
    @Transactional
    public void expireOldConversations() {
        List<Conversation> expiredConversations = conversationRepository.findExpiredConversations(Instant.now());

        for (Conversation conversation : expiredConversations) {
            try {
                // Actualizar estado
                conversationRepository.updateStatus(conversation.getId(), ConversationStatus.EXPIRED);

                // Enviar mensaje del sistema
                sendSystemMessage(conversation.getId(), SystemEventType.CONVERSATION_EXPIRED,
                        "La conversación ha expirado");

                // Notificar a los participantes
                notifyConversationExpired(conversation);

                // Limpiar recursos de Cloudinary
                cleanupCloudinaryResources(conversation.getId());

                log.info("Conversación {} expirada", conversation.getId());
            } catch (Exception e) {
                log.error("Error expirando conversación {}: {}", conversation.getId(), e.getMessage());
            }
        }

        if (!expiredConversations.isEmpty()) {
            log.info("Se expiraron {} conversaciones", expiredConversations.size());
        }
    }

    @Override
    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    @Transactional
    public void notifyExpiringConversations() {
        Instant now = Instant.now();
        Instant warningTime = now.plus(EXPIRATION_WARNING_TIME);

        List<Conversation> expiringConversations = conversationRepository
                .findConversationsExpiringSoon(warningTime, now);

        for (Conversation conversation : expiringConversations) {
            try {
                sendSystemMessage(conversation.getId(), SystemEventType.CONVERSATION_EXPIRING_SOON,
                        "La conversación expirará en menos de 1 hora");

                log.debug("Notificación de expiración enviada para conversación {}", conversation.getId());
            } catch (Exception e) {
                log.error("Error notificando expiración de conversación {}: {}",
                        conversation.getId(), e.getMessage());
            }
        }
    }

    // === Validaciones ===

    @Override
    public boolean isParticipant(String conversationId, UUID userId) {
        return conversationRepository.findById(conversationId)
                .map(conv -> conv.isParticipant(userId))
                .orElse(false);
    }

    @Override
    public boolean isConversationActive(String conversationId) {
        return conversationRepository.findById(conversationId)
                .map(conv -> conv.getStatus() == ConversationStatus.ACTIVE && !conv.isExpired())
                .orElse(false);
    }

    // === Métodos privados auxiliares ===

    private Conversation getActiveConversation(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BadRequestException("Esta conversación ya no está activa");
        }

        if (conversation.isExpired()) {
            throw new BadRequestException("Esta conversación ha expirado");
        }

        return conversation;
    }

    private void validateParticipant(Conversation conversation, UUID userId) {
        if (!conversation.isParticipant(userId)) {
            throw new UnauthorizedException("No eres participante de esta conversación");
        }
    }

    private Conversation.Participant getOtherParticipant(Conversation conversation, UUID userId) {
        return conversation.getParticipants().stream()
                .filter(p -> !p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Otro participante no encontrado"));
    }

    private String getDisplayName(User user) {
        if (user.getIsAnonymous()) {
            return "Anónimo " + user.getAnonymousCode();
        }
        return user.getFullName() != null ? user.getFullName() : "Usuario";
    }

    private void updateConversationAfterMessage(Conversation conversation, Message message, UUID senderId) {
        // Actualizar último mensaje
        Conversation.LastMessage lastMessage = Conversation.LastMessage.builder()
                .contentPreview(message.getPreview(100))
                .messageType(Conversation.MessageType.valueOf(message.getMessageType().name()))
                .senderId(senderId)
                .sentAt(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now())
                .build();

        conversationRepository.updateLastMessage(conversation.getId(), lastMessage, Instant.now());

        // Incrementar contador de no leídos para el otro participante
        Conversation.Participant recipient = getOtherParticipant(conversation, senderId);
        conversationRepository.incrementUnreadCount(conversation.getId(), recipient.getUserId());

        // Actualizar nears transferidos si hubo propina
        if (message.getHasTip() && message.getTipAmount() > 0) {
            conversationRepository.addNearsTransferred(conversation.getId(), message.getTipAmount());
        }
    }

    private UUID processsTipTransfer(UUID senderId, UUID recipientId, Integer amount, String conversationId) {
        TransactionResponse response = walletService.processTipTransfer(
                senderId,
                recipientId,
                BigDecimal.valueOf(amount),
                conversationId
        );
        return response.getId();
    }

    private UUID processsMediaUnlockPayment(UUID buyerId, UUID sellerId, Integer amount,
                                            String conversationId, String messageId) {
        TransactionResponse response = walletService.processMediaPurchase(
                buyerId,
                sellerId,
                BigDecimal.valueOf(amount),
                conversationId,
                messageId
        );
        return response.getId();
    }
    private void cleanupCloudinaryResources(String conversationId) {
        // Obtener todos los mensajes con media de esta conversación
        List<Message> mediaMessages = messageRepository.findMessagesWithMediaByConversationId(conversationId);

        for (Message message : mediaMessages) {
            if (message.getContent() != null && message.getContent().getMedia() != null) {
                String publicId = message.getContent().getMedia().getPublicId();
                if (publicId != null) {
                    // TODO: Llamar a Cloudinary API para eliminar el recurso
                    log.info("TODO: Eliminar recurso de Cloudinary: {}", publicId);
                }
            }
        }
    }

    // === Notificaciones WebSocket ===

    private void notifyNewMessage(String conversationId, MessageResponse message) {
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId + "/messages", message);
    }

    private void notifyMediaUnlocked(String conversationId, String messageId, MessageResponse message) {
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId + "/media-unlocked", message);
    }

    private void notifyMessagesRead(String conversationId, UUID userId) {
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId + "/read",
                java.util.Map.of("userId", userId.toString(), "timestamp", Instant.now().toString()));
    }

    private void notifyConversationExpired(Conversation conversation) {
        for (Conversation.Participant participant : conversation.getParticipants()) {
            messagingTemplate.convertAndSendToUser(
                    participant.getUserId().toString(),
                    "/queue/conversation-expired",
                    java.util.Map.of("conversationId", conversation.getId())
            );
        }
    }

    // === Mappers ===

    private ConversationResponse mapToConversationResponse(Conversation conversation, UUID currentUserId) {
        Conversation.Participant currentUser = conversation.getParticipantByUserId(currentUserId);
        Conversation.Participant otherUser = getOtherParticipant(conversation, currentUserId);

        long remainingSeconds = 0;
        if (conversation.getExpiresAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(Instant.now(), conversation.getExpiresAt()).getSeconds());
        }

        ConversationResponse.LastMessageResponse lastMessageResponse = null;
        if (conversation.getLastMessage() != null) {
            Conversation.LastMessage lm = conversation.getLastMessage();
            lastMessageResponse = ConversationResponse.LastMessageResponse.builder()
                    .contentPreview(lm.getContentPreview())
                    .messageType(ConversationResponse.MessageType.valueOf(lm.getMessageType().name()))
                    .senderId(lm.getSenderId())
                    .sentAt(lm.getSentAt())
                    .isFromCurrentUser(lm.getSenderId() != null && lm.getSenderId().equals(currentUserId))
                    .build();
        }

        return ConversationResponse.builder()
                .id(conversation.getId())
                .requestId(conversation.getRequestId())
                .status(ConversationResponse.ConversationStatus.valueOf(conversation.getStatus().name()))
                .requester(mapToParticipantResponse(conversation.getRequester()))
                .responder(mapToParticipantResponse(conversation.getResponder()))
                .currentUser(mapToParticipantResponse(currentUser))
                .otherUser(mapToParticipantResponse(otherUser))
                .lastMessage(lastMessageResponse)
                .metadata(mapToMetadataResponse(conversation.getMetadata()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .expiresAt(conversation.getExpiresAt())
                .remainingSeconds(remainingSeconds)
                .isExpired(conversation.isExpired())
                .build();
    }

    private ConversationResponse.ParticipantResponse mapToParticipantResponse(Conversation.Participant participant) {
        if (participant == null) return null;

        return ConversationResponse.ParticipantResponse.builder()
                .userId(participant.getUserId())
                .role(ConversationResponse.ParticipantRole.valueOf(participant.getRole().name()))
                .isAnonymous(participant.getIsAnonymous())
                .displayName(participant.getDisplayName())
                .profilePhotoUrl(participant.getProfilePhotoUrl())
                .unreadCount(participant.getUnreadCount())
                .lastReadAt(participant.getLastReadAt())
                .tipsEnabled(participant.getTipsEnabled())
                .build();
    }

    private ConversationResponse.MetadataResponse mapToMetadataResponse(Conversation.Metadata metadata) {
        if (metadata == null) return null;

        return ConversationResponse.MetadataResponse.builder()
                .totalMessages(metadata.getTotalMessages())
                .totalMedia(metadata.getTotalMedia())
                .totalNearsTransferred(metadata.getTotalNearsTransferred())
                .originalRewardNears(metadata.getOriginalRewardNears())
                .build();
    }

    private ConversationListResponse mapToConversationListResponse(Conversation conversation, UUID currentUserId) {
        Conversation.Participant currentUser = conversation.getParticipantByUserId(currentUserId);
        Conversation.Participant otherUser = getOtherParticipant(conversation, currentUserId);

        long remainingSeconds = 0;
        if (conversation.getExpiresAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(Instant.now(), conversation.getExpiresAt()).getSeconds());
        }

        String lastMessagePreview = null;
        ConversationListResponse.MessageType lastMessageType = null;
        Instant lastMessageAt = null;
        Boolean lastMessageIsFromMe = null;

        if (conversation.getLastMessage() != null) {
            Conversation.LastMessage lm = conversation.getLastMessage();
            lastMessagePreview = lm.getContentPreview();
            lastMessageType = ConversationListResponse.MessageType.valueOf(lm.getMessageType().name());
            lastMessageAt = lm.getSentAt();
            lastMessageIsFromMe = lm.getSenderId() != null && lm.getSenderId().equals(currentUserId);
        }

        return ConversationListResponse.builder()
                .id(conversation.getId())
                .requestId(conversation.getRequestId())
                .status(ConversationListResponse.ConversationStatus.valueOf(conversation.getStatus().name()))
                .otherUserId(otherUser.getUserId())
                .otherUserDisplayName(otherUser.getDisplayName())
                .otherUserProfilePhotoUrl(otherUser.getProfilePhotoUrl())
                .otherUserIsAnonymous(otherUser.getIsAnonymous())
                .lastMessagePreview(lastMessagePreview)
                .lastMessageType(lastMessageType)
                .lastMessageAt(lastMessageAt)
                .lastMessageIsFromMe(lastMessageIsFromMe)
                .unreadCount(currentUser != null ? currentUser.getUnreadCount() : 0)
                .totalMessages(conversation.getMetadata() != null ? conversation.getMetadata().getTotalMessages() : 0)
                .createdAt(conversation.getCreatedAt())
                .expiresAt(conversation.getExpiresAt())
                .remainingSeconds(remainingSeconds)
                .isExpired(conversation.isExpired())
                .build();
    }

    private MessageResponse mapToMessageResponse(Message message, Conversation conversation, UUID currentUserId) {
        MessageResponse.ContentResponse contentResponse = null;

        if (message.getContent() != null) {
            Message.Content content = message.getContent();

            MessageResponse.MediaResponse mediaResponse = null;
            if (content.getMedia() != null) {
                Message.Media media = content.getMedia();
                boolean isLocked = media.isLocked();

                mediaResponse = MessageResponse.MediaResponse.builder()
                        .url(isLocked ? null : media.getUrl()) // No enviar URL si está bloqueado
                        .thumbnailUrl(isLocked ? null : media.getThumbnailUrl())
                        .blurredUrl(media.getBlurredUrl()) // Siempre enviar la versión borrosa
                        .mediaType(MessageResponse.MediaType.valueOf(media.getMediaType().name()))
                        .sizeBytes(media.getSizeBytes())
                        .width(media.getWidth())
                        .height(media.getHeight())
                        .durationSeconds(media.getDurationSeconds())
                        .priceNears(media.getPriceNears())
                        .lockStatus(MessageResponse.MediaLockStatus.valueOf(media.getLockStatus().name()))
                        .isLocked(isLocked)
                        .isPaid(media.isPaid())
                        .unlockedAt(media.getUnlockedAt())
                        .build();
            }

            MessageResponse.SystemEventResponse systemEventResponse = null;
            if (content.getSystemEvent() != null) {
                Message.SystemEvent se = content.getSystemEvent();
                systemEventResponse = MessageResponse.SystemEventResponse.builder()
                        .eventType(MessageResponse.SystemEventType.valueOf(se.getEventType().name()))
                        .eventData(se.getEventData())
                        .displayMessage(getSystemEventDisplayMessage(se.getEventType(), se.getEventData()))
                        .build();
            }

            contentResponse = MessageResponse.ContentResponse.builder()
                    .text(content.getText())
                    .media(mediaResponse)
                    .systemEvent(systemEventResponse)
                    .build();
        }

        // Obtener info del sender
        String senderDisplayName = null;
        Boolean senderIsAnonymous = null;
        if (message.getSenderId() != null) {
            Conversation.Participant sender = conversation.getParticipantByUserId(message.getSenderId());
            if (sender != null) {
                senderDisplayName = sender.getDisplayName();
                senderIsAnonymous = sender.getIsAnonymous();
            }
        }

        long remainingSeconds = 0;
        if (message.getExpiresAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(Instant.now(), message.getExpiresAt()).getSeconds());
        }

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderDisplayName(senderDisplayName)
                .senderIsAnonymous(senderIsAnonymous)
                .messageType(MessageResponse.MessageType.valueOf(message.getMessageType().name()))
                .content(contentResponse)
                .status(MessageResponse.MessageStatus.valueOf(message.getStatus().name()))
                .deliveredAt(message.getDeliveredAt())
                .readAt(message.getReadAt())
                .hasTip(message.getHasTip())
                .tipAmount(message.getTipAmount())
                .createdAt(message.getCreatedAt())
                .expiresAt(message.getExpiresAt())
                .remainingSeconds(remainingSeconds)
                .build();
    }

    private String getSystemEventDisplayMessage(SystemEventType eventType, String eventData) {
        return switch (eventType) {
            case CONVERSATION_STARTED -> "La conversación ha comenzado. " + (eventData != null ? eventData : "");
            case MEDIA_UNLOCKED -> eventData != null ? eventData : "Contenido desbloqueado";
            case TIP_RECEIVED -> eventData != null ? eventData : "Propina recibida";
            case CONVERSATION_EXPIRING_SOON -> "⚠️ " + (eventData != null ? eventData : "La conversación expirará pronto");
            case CONVERSATION_EXPIRED -> "La conversación ha expirado";
        };
    }
}
