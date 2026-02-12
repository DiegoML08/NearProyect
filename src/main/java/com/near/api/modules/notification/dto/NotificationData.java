package com.near.api.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clase para construir los datos de las notificaciones push.
 * Soporta todos los tipos de notificación de la app Near.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationData {

    /**
     * Tipos de notificación soportados
     */
    public enum NotificationType {
        NEARBY_REQUEST,      // Nueva request cerca del usuario
        REQUEST_ACCEPTED,    // Alguien aceptó tu request
        CONTENT_DELIVERED,   // El responder entregó contenido
        DELIVERY_CONFIRMED,  // El requester confirmó la entrega (pago realizado)
        NEW_MESSAGE,          // Nuevo mensaje en el chat

        REQUEST_CANCELLED,    //
        REQUEST_RELEASED      //

        }

    private NotificationType type;
    private String title;
    private String body;
    private Map<String, String> data;

    // ============================================
    // Factory Methods para cada tipo de notificación
    // ============================================

    /**
     * Notificación al responder cuando el requester cancela la request
     */
    public static NotificationData requestCancelled(UUID requestId, String locationAddress) {
        return NotificationData.builder()
                .type(NotificationType.REQUEST_CANCELLED)
                .title("Request cancelada")
                .body("El solicitante canceló la request en " + locationAddress)
                .data(Map.of(
                        "type", "REQUEST_CANCELLED",
                        "requestId", requestId.toString()
                ))
                .build();
    }

    /**
     * Notificación al responder cuando la request es liberada
     */
    public static NotificationData requestReleased(UUID requestId, String reason) {
        return NotificationData.builder()
                .type(NotificationType.REQUEST_RELEASED)
                .title("Request liberada")
                .body("La request fue liberada: " + reason)
                .data(Map.of(
                        "type", "REQUEST_RELEASED",
                        "requestId", requestId.toString()
                ))
                .build();
    }

    /**
     * Notificación para usuarios cercanos cuando se crea una nueva request
     */
    public static NotificationData nearbyRequest(
            UUID requestId,
            String locationName,
            int rewardNears,
            long remainingMinutes,
            String description
    ) {
        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.NEARBY_REQUEST.name());
        data.put("requestId", requestId.toString());
        data.put("locationName", locationName);
        data.put("rewardNears", String.valueOf(rewardNears));
        data.put("remainingMinutes", String.valueOf(remainingMinutes));
        data.put("description", truncate(description, 100));

        return NotificationData.builder()
                .type(NotificationType.NEARBY_REQUEST)
                .title("📍 Nueva Request cerca de ti")
                .body(String.format("%s • 🪙 %d Nears", truncate(locationName, 30), rewardNears))
                .data(data)
                .build();
    }

    /**
     * Notificación al requester cuando alguien acepta su request
     */
    public static NotificationData requestAccepted(
            UUID requestId,
            String responderName,
            String locationName
    ) {
        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.REQUEST_ACCEPTED.name());
        data.put("requestId", requestId.toString());
        data.put("responderName", responderName);
        data.put("locationName", locationName);

        return NotificationData.builder()
                .type(NotificationType.REQUEST_ACCEPTED)
                .title("✅ Request aceptada")
                .body(String.format("%s aceptó tu request en %s", responderName, truncate(locationName, 25)))
                .data(data)
                .build();
    }

    /**
     * Notificación al requester cuando el responder entrega contenido
     */
    public static NotificationData contentDelivered(
            UUID requestId,
            String responderName,
            String locationName
    ) {
        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.CONTENT_DELIVERED.name());
        data.put("requestId", requestId.toString());
        data.put("responderName", responderName);
        data.put("locationName", locationName);

        return NotificationData.builder()
                .type(NotificationType.CONTENT_DELIVERED)
                .title("📸 Contenido recibido")
                .body(String.format("%s entregó contenido de %s", responderName, truncate(locationName, 25)))
                .data(data)
                .build();
    }

    /**
     * Notificación al responder cuando el requester confirma la entrega
     */
    public static NotificationData deliveryConfirmed(
            UUID requestId,
            int earnedNears
    ) {
        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.DELIVERY_CONFIRMED.name());
        data.put("requestId", requestId.toString());
        data.put("earnedNears", String.valueOf(earnedNears));

        return NotificationData.builder()
                .type(NotificationType.DELIVERY_CONFIRMED)
                .title("💰 ¡Pago recibido!")
                .body(String.format("Has ganado %d Nears por tu entrega", earnedNears))
                .data(data)
                .build();
    }

    /**
     * Notificación de nuevo mensaje en el chat
     */
    public static NotificationData newMessage(
            String conversationId,
            String senderName,
            String messagePreview,
            boolean isMedia
    ) {
        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.NEW_MESSAGE.name());
        data.put("conversationId", conversationId);
        data.put("senderName", senderName);
        data.put("messagePreview", isMedia ? getMediaIndicator(messagePreview) : truncate(messagePreview, 50));

        String body = isMedia 
                ? String.format("%s: %s", senderName, getMediaIndicator(messagePreview))
                : String.format("%s: %s", senderName, truncate(messagePreview, 50));

        return NotificationData.builder()
                .type(NotificationType.NEW_MESSAGE)
                .title("💬 Nuevo mensaje")
                .body(body)
                .data(data)
                .build();
    }

    // ============================================
    // Helpers
    // ============================================

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String getMediaIndicator(String mediaType) {
        if (mediaType == null) return "📎 Archivo";
        return switch (mediaType.toUpperCase()) {
            case "IMAGE", "PHOTO" -> "📷 Foto";
            case "VIDEO" -> "🎥 Video";
            case "AUDIO" -> "🎵 Audio";
            default -> "📎 Archivo";
        };
    }
}
