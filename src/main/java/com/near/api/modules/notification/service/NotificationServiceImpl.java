package com.near.api.modules.notification.service;

import com.google.firebase.messaging.*;
import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.notification.dto.NotificationData;
import com.near.api.modules.notification.entity.FcmToken;
import com.near.api.modules.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;
    private final FirebaseMessaging firebaseMessaging;

    // Configuración para búsqueda de usuarios cercanos
    private static final int MAX_NEARBY_USERS = 100;
    private static final int ACTIVE_MINUTES_THRESHOLD = 30;
    private static final BigDecimal MIN_REPUTATION_FOR_TRUST = new BigDecimal("3.0");
    private static final int MAX_RADIUS_METERS = 5000; // 5km máximo

    // ============================================
    // Gestión de tokens FCM
    // ============================================

    @Override
    @Transactional
    public void registerToken(UUID userId, String fcmToken, String deviceId, String deviceType, String deviceName) {
        log.debug("Registrando token FCM para usuario {}: {}", userId, fcmToken.substring(0, 20) + "...");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        // Verificar si el token ya existe (podría estar asociado a otro usuario)
        fcmTokenRepository.findByToken(fcmToken).ifPresent(existingToken -> {
            if (!existingToken.getUser().getId().equals(userId)) {
                // El token estaba asociado a otro usuario, lo desactivamos
                log.info("Token FCM transferido de usuario {} a usuario {}", 
                        existingToken.getUser().getId(), userId);
                fcmTokenRepository.deactivateToken(fcmToken);
            } else {
                // Ya existe para este usuario, actualizamos
                existingToken.setIsActive(true);
                existingToken.setDeviceId(deviceId);
                existingToken.setDeviceType(deviceType);
                existingToken.setDeviceName(deviceName);
                existingToken.setLastUsedAt(OffsetDateTime.now());
                fcmTokenRepository.save(existingToken);
                return;
            }
        });

        // Crear nuevo token
        FcmToken newToken = FcmToken.builder()
                .user(user)
                .token(fcmToken)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .deviceName(deviceName)
                .isActive(true)
                .lastUsedAt(OffsetDateTime.now())
                .build();

        fcmTokenRepository.save(newToken);
        log.info("Token FCM registrado exitosamente para usuario {}", userId);
    }

    @Override
    @Transactional
    public void removeToken(String fcmToken) {
        fcmTokenRepository.deactivateToken(fcmToken);
        log.debug("Token FCM desactivado: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
    }

    @Override
    @Transactional
    public void removeAllUserTokens(UUID userId) {
        fcmTokenRepository.deactivateAllUserTokens(userId);
        log.debug("Todos los tokens FCM desactivados para usuario {}", userId);
    }

    // ============================================
    // Envío de notificaciones
    // ============================================

    @Override
    @Async
    public void sendToUser(UUID userId, NotificationData notification) {
        List<FcmToken> tokens = fcmTokenRepository.findByUserIdAndIsActiveTrue(userId);
        
        if (tokens.isEmpty()) {
            log.debug("Usuario {} no tiene tokens FCM activos", userId);
            return;
        }

        List<String> tokenStrings = tokens.stream()
                .map(FcmToken::getToken)
                .collect(Collectors.toList());

        sendToTokens(tokenStrings, notification);
    }

    @Override
    @Async
    public void sendToUsers(List<UUID> userIds, NotificationData notification) {
        if (userIds.isEmpty()) {
            return;
        }

        List<String> tokens = fcmTokenRepository.findActiveTokensByUserIds(userIds);
        
        if (tokens.isEmpty()) {
            log.debug("Ninguno de los {} usuarios tiene tokens FCM activos", userIds.size());
            return;
        }

        log.info("Enviando notificación {} a {} tokens de {} usuarios", 
                notification.getType(), tokens.size(), userIds.size());
        
        sendToTokens(tokens, notification);
    }

    @Override
    @Async
    public void sendToToken(String fcmToken, NotificationData notification) {
        sendToTokens(List.of(fcmToken), notification);
    }

    /**
     * Método interno para enviar notificaciones a múltiples tokens
     */
    private void sendToTokens(List<String> tokens, NotificationData notification) {
        if (tokens.isEmpty() || firebaseMessaging == null) {
            if (firebaseMessaging == null) {
                log.warn("Firebase Messaging no está configurado. Notificación no enviada.");
            }
            return;
        }

        try {
            // Construir la notificación
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(notification.getTitle())
                            .setBody(notification.getBody())
                            .build())
                    .putAllData(notification.getData())
                    // Configuración para Android
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setClickAction("OPEN_NOTIFICATION")
                                    .setSound("default")
                                    .build())
                            .build())
                    // Configuración para iOS
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            // Enviar en lotes si hay muchos tokens (FCM permite max 500 por request)
            if (tokens.size() <= 500) {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
                handleBatchResponse(response, tokens);
            } else {
                // Dividir en lotes de 500
                for (int i = 0; i < tokens.size(); i += 500) {
                    List<String> batch = tokens.subList(i, Math.min(i + 500, tokens.size()));
                    MulticastMessage batchMessage = MulticastMessage.builder()
                            .addAllTokens(batch)
                            .setNotification(Notification.builder()
                                    .setTitle(notification.getTitle())
                                    .setBody(notification.getBody())
                                    .build())
                            .putAllData(notification.getData())
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();
                    
                    BatchResponse response = firebaseMessaging.sendEachForMulticast(batchMessage);
                    handleBatchResponse(response, batch);
                }
            }

        } catch (FirebaseMessagingException e) {
            log.error("Error enviando notificación FCM: {}", e.getMessage());
        }
    }

    /**
     * Maneja la respuesta de envío masivo y limpia tokens inválidos
     */
    private void handleBatchResponse(BatchResponse response, List<String> tokens) {
        int successCount = response.getSuccessCount();
        int failureCount = response.getFailureCount();

        log.info("Notificaciones enviadas: {} exitosas, {} fallidas", successCount, failureCount);

        if (failureCount > 0) {
            List<SendResponse> responses = response.getResponses();
            List<String> tokensToRemove = new ArrayList<>();

            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);
                if (!sendResponse.isSuccessful()) {
                    FirebaseMessagingException exception = sendResponse.getException();
                    if (exception != null) {
                        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
                        
                        // Si el token es inválido o no está registrado, lo desactivamos
                        if (errorCode == MessagingErrorCode.UNREGISTERED ||
                            errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                            tokensToRemove.add(tokens.get(i));
                        }
                        
                        log.debug("Error en token {}: {} - {}", 
                                i, errorCode, exception.getMessage());
                    }
                }
            }

            // Desactivar tokens inválidos
            if (!tokensToRemove.isEmpty()) {
                log.info("Desactivando {} tokens inválidos", tokensToRemove.size());
                tokensToRemove.forEach(fcmTokenRepository::deactivateToken);
            }
        }
    }

    // ============================================
    // Notificaciones específicas del negocio
    // ============================================

    @Override
    @Async
    @Transactional(readOnly = true)
    public void notifyNearbyUsers(
            UUID requestId,
            UUID requesterId,
            double latitude,
            double longitude,
            int radiusMeters,
            String locationName,
            int rewardNears,
            long remainingMinutes,
            String description,
            String trustMode
    ) {
        log.info("Buscando usuarios cercanos para notificar sobre request {}", requestId);

        // Limitar el radio a máximo 5km
        int effectiveRadius = Math.min(radiusMeters, MAX_RADIUS_METERS);
        
        // Calcular el tiempo límite para usuarios "activos"
        OffsetDateTime activeThreshold = OffsetDateTime.now().minusMinutes(ACTIVE_MINUTES_THRESHOLD);

        // Buscar usuarios cercanos
        List<UUID> nearbyUserIds;
        
        if ("TRUST".equalsIgnoreCase(trustMode)) {
            // Solo usuarios con buena reputación
            nearbyUserIds = userRepository.findNearbyActiveUsersForTrustMode(
                    latitude, 
                    longitude, 
                    effectiveRadius,
                    activeThreshold,
                    MIN_REPUTATION_FOR_TRUST,
                    requesterId,
                    MAX_NEARBY_USERS
            );
        } else {
            // Todos los usuarios cercanos
            nearbyUserIds = userRepository.findNearbyActiveUsers(
                    latitude, 
                    longitude, 
                    effectiveRadius,
                    activeThreshold,
                    requesterId,
                    MAX_NEARBY_USERS
            );
        }

        if (nearbyUserIds.isEmpty()) {
            log.info("No se encontraron usuarios cercanos activos para request {}", requestId);
            return;
        }

        log.info("Encontrados {} usuarios cercanos para notificar", nearbyUserIds.size());

        // Crear y enviar notificación
        NotificationData notification = NotificationData.nearbyRequest(
                requestId,
                locationName,
                rewardNears,
                remainingMinutes,
                description
        );

        sendToUsers(nearbyUserIds, notification);
    }

    @Override
    @Async
    public void notifyRequestAccepted(UUID requesterId, UUID requestId, String responderName, String locationName) {
        NotificationData notification = NotificationData.requestAccepted(
                requestId,
                responderName,
                locationName
        );
        sendToUser(requesterId, notification);
    }

    @Override
    @Async
    public void notifyContentDelivered(UUID requesterId, UUID requestId, String responderName, String locationName) {
        NotificationData notification = NotificationData.contentDelivered(
                requestId,
                responderName,
                locationName
        );
        sendToUser(requesterId, notification);
    }

    @Override
    @Async
    public void notifyDeliveryConfirmed(UUID responderId, UUID requestId, int earnedNears) {
        NotificationData notification = NotificationData.deliveryConfirmed(
                requestId,
                earnedNears
        );
        sendToUser(responderId, notification);
    }

    @Override
    @Async
    public void notifyNewMessage(
            UUID recipientId,
            String conversationId,
            String senderName,
            String messagePreview,
            boolean isMedia
    ) {
        NotificationData notification = NotificationData.newMessage(
                conversationId,
                senderName,
                messagePreview,
                isMedia
        );
        sendToUser(recipientId, notification);
    }
}
