package com.near.api.modules.chat.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "messages")
@CompoundIndex(name = "idx_conversation_created", def = "{'conversation_id': 1, 'created_at': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    private String id;

    @Indexed
    @Field("conversation_id")
    private String conversationId;

    @Indexed
    @Field("sender_id")
    private UUID senderId;

    @Field("message_type")
    private MessageType messageType;

    @Field("content")
    private Content content;

    @Field("status")
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Field("delivered_at")
    private Instant deliveredAt;

    @Field("read_at")
    private Instant readAt;

    // === Propinas ===

    @Field("has_tip")
    @Builder.Default
    private Boolean hasTip = false;

    @Field("tip_amount")
    @Builder.Default
    private Integer tipAmount = 0;

    @Field("tip_transaction_id")
    private UUID tipTransactionId;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @Indexed(expireAfter = "0s")
    @Field("expires_at")
    private Instant expiresAt;

    // === Embedded Classes ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Content {

        @Field("text")
        private String text;

        @Field("media")
        private Media media;

        @Field("system_event")
        private SystemEvent systemEvent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Media {

        @Field("url")
        private String url;

        @Field("public_id")
        private String publicId;

        @Field("thumbnail_url")
        private String thumbnailUrl;

        @Field("blurred_url")
        private String blurredUrl;

        @Field("media_type")
        private MediaType mediaType;

        @Field("size_bytes")
        private Integer sizeBytes;

        @Field("width")
        private Integer width;

        @Field("height")
        private Integer height;

        @Field("duration_seconds")
        private Integer durationSeconds;

        // === Precio y bloqueo ===

        @Field("price_nears")
        @Builder.Default
        private Integer priceNears = 0;

        @Field("lock_status")
        @Builder.Default
        private MediaLockStatus lockStatus = MediaLockStatus.UNLOCKED;

        @Field("unlocked_by")
        private UUID unlockedBy;

        @Field("unlocked_at")
        private Instant unlockedAt;

        @Field("unlock_transaction_id")
        private UUID unlockTransactionId;

        public boolean isPaid() {
            return priceNears != null && priceNears > 0;
        }

        public boolean isLocked() {
            return lockStatus == MediaLockStatus.LOCKED;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SystemEvent {

        @Field("event_type")
        private SystemEventType eventType;

        @Field("event_data")
        private String eventData;
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

    // === Helper Methods ===

    public boolean isTextMessage() {
        return messageType == MessageType.TEXT;
    }

    public boolean isMediaMessage() {
        return messageType == MessageType.IMAGE || messageType == MessageType.VIDEO;
    }

    public boolean isSystemMessage() {
        return messageType == MessageType.SYSTEM;
    }

    public boolean hasLockedMedia() {
        return content != null
                && content.getMedia() != null
                && content.getMedia().isLocked();
    }

    public boolean hasPaidMedia() {
        return content != null
                && content.getMedia() != null
                && content.getMedia().isPaid();
    }

    public String getPreview(int maxLength) {
        if (messageType == MessageType.SYSTEM) {
            return "[Sistema]";
        }
        if (isMediaMessage()) {
            String prefix = messageType == MessageType.IMAGE ? "📷 Foto" : "🎥 Video";
            if (hasPaidMedia()) {
                return prefix + " - " + content.getMedia().getPriceNears() + " Nears";
            }
            return prefix;
        }
        if (content != null && content.getText() != null) {
            String text = content.getText();
            if (text.length() > maxLength) {
                return text.substring(0, maxLength) + "...";
            }
            return text;
        }
        return "";
    }
}
