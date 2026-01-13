package com.near.api.modules.chat.controller;

import com.near.api.modules.chat.dto.request.SendMediaMessageRequest;
import com.near.api.modules.chat.dto.request.SendMessageRequest;
import com.near.api.modules.chat.dto.response.MessageResponse;
import com.near.api.modules.chat.service.ChatService;
import com.near.api.shared.exception.BadRequestException;
import com.near.api.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // ============================================
    // ENVIAR MENSAJES
    // ============================================

    /**
     * Enviar mensaje de texto vía WebSocket
     * Cliente envía a: /app/chat/{conversationId}/send-text
     * Broadcast a: /topic/chat/{conversationId}/messages
     */
    @MessageMapping("/chat/{conversationId}/send-text")
    public void sendTextMessage(
            @DestinationVariable String conversationId,
            @Payload SendMessageRequest request,
            Principal principal) {

        UUID userId = extractUserId(principal);

        try {
            MessageResponse response = chatService.sendTextMessage(conversationId, userId, request);

            // El servicio ya envía el broadcast, pero podemos enviar confirmación al sender
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/message-sent",
                    Map.of(
                            "messageId", response.getId(),
                            "conversationId", conversationId,
                            "status", "SENT",
                            "timestamp", Instant.now().toString()
                    )
            );

            log.debug("Mensaje de texto enviado vía WebSocket: {} en conversación {}",
                    response.getId(), conversationId);

        } catch (Exception e) {
            sendErrorToUser(principal.getName(), conversationId, e.getMessage());
            log.error("Error enviando mensaje de texto: {}", e.getMessage());
        }
    }

    /**
     * Enviar mensaje multimedia vía WebSocket
     * Cliente envía a: /app/chat/{conversationId}/send-media
     * Broadcast a: /topic/chat/{conversationId}/messages
     */
    @MessageMapping("/chat/{conversationId}/send-media")
    public void sendMediaMessage(
            @DestinationVariable String conversationId,
            @Payload SendMediaMessageRequest request,
            Principal principal) {

        UUID userId = extractUserId(principal);

        try {
            MessageResponse response = chatService.sendMediaMessage(conversationId, userId, request);

            // Confirmación al sender
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/message-sent",
                    Map.of(
                            "messageId", response.getId(),
                            "conversationId", conversationId,
                            "status", "SENT",
                            "mediaType", request.getMediaType().name(),
                            "timestamp", Instant.now().toString()
                    )
            );

            log.debug("Mensaje multimedia enviado vía WebSocket: {} en conversación {}",
                    response.getId(), conversationId);

        } catch (Exception e) {
            sendErrorToUser(principal.getName(), conversationId, e.getMessage());
            log.error("Error enviando mensaje multimedia: {}", e.getMessage());
        }
    }

    // ============================================
    // ESTADO DE MENSAJES
    // ============================================

    /**
     * Marcar mensajes como leídos
     * Cliente envía a: /app/chat/{conversationId}/mark-read
     * Notifica a: /topic/chat/{conversationId}/read
     */
    @MessageMapping("/chat/{conversationId}/mark-read")
    public void markAsRead(
            @DestinationVariable String conversationId,
            Principal principal) {

        UUID userId = extractUserId(principal);

        try {
            chatService.markConversationAsRead(conversationId, userId);

            // Notificar al otro participante que los mensajes fueron leídos
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversationId + "/read",
                    Map.of(
                            "userId", userId.toString(),
                            "conversationId", conversationId,
                            "timestamp", Instant.now().toString()
                    )
            );

            log.debug("Conversación {} marcada como leída por {}", conversationId, userId);

        } catch (Exception e) {
            log.error("Error marcando como leído: {}", e.getMessage());
        }
    }

    /**
     * Notificar que el usuario está escribiendo
     * Cliente envía a: /app/chat/{conversationId}/typing
     * Broadcast a: /topic/chat/{conversationId}/typing
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void userTyping(
            @DestinationVariable String conversationId,
            @Payload Map<String, Boolean> payload,
            Principal principal) {

        UUID userId = extractUserId(principal);
        boolean isTyping = payload.getOrDefault("isTyping", false);

        // Verificar que es participante
        if (!chatService.isParticipant(conversationId, userId)) {
            return;
        }

        // Broadcast a la conversación (excepto al sender)
        messagingTemplate.convertAndSend(
                "/topic/chat/" + conversationId + "/typing",
                Map.of(
                        "userId", userId.toString(),
                        "isTyping", isTyping,
                        "timestamp", Instant.now().toString()
                )
        );
    }

    // ============================================
    // DESBLOQUEAR MEDIA
    // ============================================

    /**
     * Desbloquear media pagada vía WebSocket
     * Cliente envía a: /app/chat/{conversationId}/unlock-media
     * Broadcast a: /topic/chat/{conversationId}/media-unlocked
     */
    @MessageMapping("/chat/{conversationId}/unlock-media")
    public void unlockMedia(
            @DestinationVariable String conversationId,
            @Payload Map<String, String> payload,
            Principal principal) {

        UUID userId = extractUserId(principal);
        String messageId = payload.get("messageId");

        if (messageId == null || messageId.isBlank()) {
            sendErrorToUser(principal.getName(), conversationId, "messageId es requerido");
            return;
        }

        try {
            MessageResponse response = chatService.unlockMedia(conversationId, messageId, userId);

            // Confirmación al comprador
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/media-unlocked",
                    Map.of(
                            "messageId", messageId,
                            "conversationId", conversationId,
                            "status", "UNLOCKED",
                            "timestamp", Instant.now().toString()
                    )
            );

            log.debug("Media desbloqueada vía WebSocket: mensaje {} en conversación {}",
                    messageId, conversationId);

        } catch (Exception e) {
            sendErrorToUser(principal.getName(), conversationId, e.getMessage());
            log.error("Error desbloqueando media: {}", e.getMessage());
        }
    }

    // ============================================
    // SUSCRIPCIÓN Y CONEXIÓN
    // ============================================

    /**
     * Manejar cuando un usuario se une a una conversación
     * Cliente envía a: /app/chat/{conversationId}/join
     */
    @MessageMapping("/chat/{conversationId}/join")
    public void joinConversation(
            @DestinationVariable String conversationId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        UUID userId = extractUserId(principal);

        // Verificar que es participante
        if (!chatService.isParticipant(conversationId, userId)) {
            sendErrorToUser(principal.getName(), conversationId, "No eres participante de esta conversación");
            return;
        }

        // Guardar conversationId en la sesión para cleanup
        headerAccessor.getSessionAttributes().put("conversationId", conversationId);

        // Notificar que el usuario se unió
        messagingTemplate.convertAndSend(
                "/topic/chat/" + conversationId + "/presence",
                Map.of(
                        "userId", userId.toString(),
                        "status", "ONLINE",
                        "timestamp", Instant.now().toString()
                )
        );

        // Marcar mensajes como entregados
        try {
            chatService.markConversationAsRead(conversationId, userId);
        } catch (Exception e) {
            log.warn("Error marcando mensajes como leídos al unirse: {}", e.getMessage());
        }

        log.debug("Usuario {} se unió a la conversación {}", userId, conversationId);
    }

    /**
     * Manejar cuando un usuario deja una conversación
     * Cliente envía a: /app/chat/{conversationId}/leave
     */
    @MessageMapping("/chat/{conversationId}/leave")
    public void leaveConversation(
            @DestinationVariable String conversationId,
            Principal principal) {

        UUID userId = extractUserId(principal);

        // Notificar que el usuario se fue
        messagingTemplate.convertAndSend(
                "/topic/chat/" + conversationId + "/presence",
                Map.of(
                        "userId", userId.toString(),
                        "status", "OFFLINE",
                        "timestamp", Instant.now().toString()
                )
        );

        log.debug("Usuario {} dejó la conversación {}", userId, conversationId);
    }

    // ============================================
    // CONFIGURACIÓN
    // ============================================

    /**
     * Actualizar configuración de propinas
     * Cliente envía a: /app/chat/{conversationId}/tips-config
     */
    @MessageMapping("/chat/{conversationId}/tips-config")
    public void updateTipsConfig(
            @DestinationVariable String conversationId,
            @Payload Map<String, Boolean> payload,
            Principal principal) {

        UUID userId = extractUserId(principal);
        Boolean enabled = payload.get("enabled");

        if (enabled == null) {
            sendErrorToUser(principal.getName(), conversationId, "enabled es requerido");
            return;
        }

        try {
            chatService.updateTipsEnabled(conversationId, userId, enabled);

            // Notificar al otro participante
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + conversationId + "/tips-config",
                    Map.of(
                            "userId", userId.toString(),
                            "tipsEnabled", enabled,
                            "timestamp", Instant.now().toString()
                    )
            );

        } catch (Exception e) {
            sendErrorToUser(principal.getName(), conversationId, e.getMessage());
        }
    }

    // ============================================
    // MÉTODOS AUXILIARES
    // ============================================

    private UUID extractUserId(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("No autenticado");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Usuario inválido");
        }
    }

    private void sendErrorToUser(String username, String conversationId, String errorMessage) {
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/errors",
                Map.of(
                        "conversationId", conversationId,
                        "error", errorMessage,
                        "timestamp", Instant.now().toString()
                )
        );
    }
}
