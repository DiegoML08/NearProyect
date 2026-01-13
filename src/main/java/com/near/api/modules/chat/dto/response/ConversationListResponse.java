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
public class ConversationListResponse {

    private String id;

    private UUID requestId;

    private ConversationStatus status;

    // === Otro participante (para mostrar en la lista) ===

    private UUID otherUserId;

    private String otherUserDisplayName;

    private String otherUserProfilePhotoUrl;

    private Boolean otherUserIsAnonymous;

    // === Último mensaje ===

    private String lastMessagePreview;

    private MessageType lastMessageType;

    private Instant lastMessageAt;

    private Boolean lastMessageIsFromMe;

    // === Contadores ===

    private Integer unreadCount;

    private Integer totalMessages;

    // === Tiempo ===

    private Instant createdAt;

    private Instant expiresAt;

    private Long remainingSeconds;

    private Boolean isExpired;

    // === Request info (opcional) ===

    private String requestLocationAddress;

    private Integer requestRewardNears;

    // === Enums ===

    public enum ConversationStatus {
        ACTIVE,
        COMPLETED,
        EXPIRED
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        SYSTEM
    }
}