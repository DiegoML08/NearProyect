package com.near.api.modules.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageResponse {

    private String id;

    private String conversationId;

    private UUID senderId;

    private String senderDisplayName;

    private Boolean senderIsAnonymous;

    private MessageType messageType;

    private ContentResponse content;

    private MessageStatus status;

    private Instant deliveredAt;

    private Instant readAt;

    // === Propinas ===

    private Boolean hasTip;

    private Integer tipAmount;

    private Instant createdAt;

    private Instant expiresAt;

    private Long remainingSeconds;

    // === Embedded Classes ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentResponse {

        private String text;

        private MediaResponse media;

        private SystemEventResponse systemEvent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaResponse {

        private String url;

        private String thumbnailUrl;

        private String blurredUrl;

        private MediaType mediaType;

        private Integer sizeBytes;

        private Integer width;

        private Integer height;

        private Integer durationSeconds;

        // === Precio y bloqueo ===

        private Integer priceNears;

        private MediaLockStatus lockStatus;

        private Boolean isLocked;

        private Boolean isPaid;

        private Instant unlockedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemEventResponse {

        private SystemEventType eventType;

        private String eventData;

        private String displayMessage;
    }

    // === Enums ===

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        SYSTEM
    }

    public enum MessageStatus {
        SENT,
        DELIVERED,
        READ
    }

    public enum MediaType {
        IMAGE,
        VIDEO
    }

    public enum MediaLockStatus {
        LOCKED,
        UNLOCKED
    }

    public enum SystemEventType {
        CONVERSATION_STARTED,
        MEDIA_UNLOCKED,
        TIP_RECEIVED,
        CONVERSATION_EXPIRING_SOON,
        CONVERSATION_EXPIRED
    }
}