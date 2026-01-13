package com.near.api.modules.chat.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "conversations")
@CompoundIndex(name = "idx_participants_status", def = "{'participants.userId': 1, 'status': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("request_id")
    private UUID requestId;

    @Field("participants")
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    @Field("status")
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Field("last_message")
    private LastMessage lastMessage;

    @Field("metadata")
    @Builder.Default
    private Metadata metadata = new Metadata();

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;

    @Indexed(expireAfter = "0s")
    @Field("expires_at")
    private Instant expiresAt;

    // === Embedded Classes ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Participant {

        @Field("user_id")
        private UUID userId;

        @Field("role")
        private ParticipantRole role;

        @Field("is_anonymous")
        @Builder.Default
        private Boolean isAnonymous = false;

        @Field("display_name")
        private String displayName;

        @Field("profile_photo_url")
        private String profilePhotoUrl;

        @Field("unread_count")
        @Builder.Default
        private Integer unreadCount = 0;

        @Field("last_read_at")
        private Instant lastReadAt;

        @Field("tips_enabled")
        @Builder.Default
        private Boolean tipsEnabled = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LastMessage {

        @Field("content_preview")
        private String contentPreview;

        @Field("message_type")
        private MessageType messageType;

        @Field("sender_id")
        private UUID senderId;

        @Field("sent_at")
        private Instant sentAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Metadata {

        @Field("total_messages")
        @Builder.Default
        private Integer totalMessages = 0;

        @Field("total_media")
        @Builder.Default
        private Integer totalMedia = 0;

        @Field("total_nears_transferred")
        @Builder.Default
        private Integer totalNearsTransferred = 0;

        @Field("original_reward_nears")
        private Integer originalRewardNears;
    }

    // === Enums ===

    public enum ConversationStatus {
        ACTIVE,
        COMPLETED,
        EXPIRED
    }

    public enum ParticipantRole {
        REQUESTER,
        RESPONDER
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        SYSTEM
    }

    // === Helper Methods ===

    public Participant getRequester() {
        return participants.stream()
                .filter(p -> p.getRole() == ParticipantRole.REQUESTER)
                .findFirst()
                .orElse(null);
    }

    public Participant getResponder() {
        return participants.stream()
                .filter(p -> p.getRole() == ParticipantRole.RESPONDER)
                .findFirst()
                .orElse(null);
    }

    public Participant getParticipantByUserId(UUID userId) {
        return participants.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    public boolean isParticipant(UUID userId) {
        return participants.stream()
                .anyMatch(p -> p.getUserId().equals(userId));
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public void incrementMessageCount() {
        if (this.metadata == null) {
            this.metadata = new Metadata();
        }
        this.metadata.setTotalMessages(this.metadata.getTotalMessages() + 1);
    }

    public void incrementMediaCount() {
        if (this.metadata == null) {
            this.metadata = new Metadata();
        }
        this.metadata.setTotalMedia(this.metadata.getTotalMedia() + 1);
    }

    public void addNearsTransferred(Integer amount) {
        if (this.metadata == null) {
            this.metadata = new Metadata();
        }
        this.metadata.setTotalNearsTransferred(
                this.metadata.getTotalNearsTransferred() + amount
        );
    }
}
