package com.near.api.modules.notification.service;

import com.near.api.modules.notification.dto.NotificationData;

import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestionar notificaciones push vía Firebase Cloud Messaging
 */
public interface NotificationService {

    // ============================================
    // Gestión de tokens FCM
    // ============================================

    /**
     * Registrar o actualizar el token FCM de un dispositivo
     */
    void registerToken(UUID userId, String fcmToken, String deviceId, String deviceType, String deviceName);

    /**
     * Eliminar/desactivar un token FCM
     */
    void removeToken(String fcmToken);

    /**
     * Eliminar todos los tokens de un usuario
     */
    void removeAllUserTokens(UUID userId);

    // ============================================
    // Envío de notificaciones individuales
    // ============================================

    /**
     * Enviar notificación a un usuario específico (a todos sus dispositivos)
     */
    void sendToUser(UUID userId, NotificationData notification);

    /**
     * Enviar notificación a múltiples usuarios
     */
    void sendToUsers(List<UUID> userIds, NotificationData notification);

    /**
     * Enviar notificación a un token específico
     */
    void sendToToken(String fcmToken, NotificationData notification);

    // ============================================
    // Notificaciones específicas del negocio
    // ============================================

    /**
     * Notificar a usuarios cercanos sobre una nueva request
     * 
     * @param requestId ID de la request creada
     * @param requesterId ID del usuario que creó la request (para excluirlo)
     * @param latitude Latitud de la request
     * @param longitude Longitud de la request
     * @param radiusMeters Radio de búsqueda de la request
     * @param locationName Nombre/dirección de la ubicación
     * @param rewardNears Recompensa en Nears
     * @param remainingMinutes Minutos restantes
     * @param description Descripción de la request
     * @param trustMode Modo de confianza (ALL o TRUST)
     */
    void notifyNearbyUsers(
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
    );

    /**
     * Notificar al requester que alguien aceptó su request
     */
    void notifyRequestAccepted(UUID requesterId, UUID requestId, String responderName, String locationName);

    /**
     * Notificar al requester que el responder entregó contenido
     */
    void notifyContentDelivered(UUID requesterId, UUID requestId, String responderName, String locationName);

    /**
     * Notificar al responder que el pago fue confirmado
     */
    void notifyDeliveryConfirmed(UUID responderId, UUID requestId, int earnedNears);

    /**
     * Notificar nuevo mensaje en chat
     */
    void notifyNewMessage(
            UUID recipientId,
            String conversationId,
            String senderName,
            String messagePreview,
            boolean isMedia
    );
}
