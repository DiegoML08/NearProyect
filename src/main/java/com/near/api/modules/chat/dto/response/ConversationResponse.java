package com.near.api.modules.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationResponse {

    private String id;

    private UUID requestId;

    private ConversationStatus status;

    private ParticipantResponse requester;

    private ParticipantResponse responder;

    private ParticipantResponse currentUser;

    private ParticipantResponse otherUser;

    private LastMessageResponse lastMessage;

    private MetadataResponse metadata;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant expiresAt;

    private Long remainingSeconds;

    private Boolean isExpired;

    // === Embedded Classes ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParticipantResponse {

        private UUID userId;

        private ParticipantRole role;

        private Boolean isAnonymous;

        private String displayName;

        private String profilePhotoUrl;

        private Integer unreadCount;

        private Instant lastReadAt;

        private Boolean tipsEnabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LastMessageResponse {

        private String contentPreview;

        private MessageType messageType;

        private UUID senderId;

        private Instant sentAt;

        private Boolean isFromCurrentUser;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataResponse {

        private Integer totalMessages;

        private Integer totalMedia;

        private Integer totalNearsTransferred;

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
}