package com.near.api.modules.chat.repository;

import com.near.api.modules.chat.document.Conversation;
import com.near.api.modules.chat.document.Conversation.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // === Búsquedas básicas ===

    Optional<Conversation> findByRequestId(UUID requestId);

    boolean existsByRequestId(UUID requestId);

    // === Búsquedas por participante ===

    @Query("{ 'participants.userId': ?0 }")
    List<Conversation> findByParticipantUserId(UUID userId);

    @Query("{ 'participants.userId': ?0, 'status': ?1 }")
    List<Conversation> findByParticipantUserIdAndStatus(UUID userId, ConversationStatus status);

    @Query("{ 'participants.userId': ?0 }")
    Page<Conversation> findByParticipantUserId(UUID userId, Pageable pageable);

    @Query("{ 'participants.userId': ?0, 'status': 'ACTIVE' }")
    Page<Conversation> findActiveByParticipantUserId(UUID userId, Pageable pageable);

    @Query("{ 'participants.userId': ?0, 'status': { $in: ?1 } }")
    Page<Conversation> findByParticipantUserIdAndStatusIn(UUID userId, List<ConversationStatus> statuses, Pageable pageable);

    // === Búsquedas con ordenamiento por último mensaje ===

    @Query("{ 'participants.userId': ?0 }")
    Page<Conversation> findByParticipantUserIdOrderByLastMessageSentAtDesc(UUID userId, Pageable pageable);

    @Query("{ 'participants.userId': ?0, 'status': 'ACTIVE' }")
    List<Conversation> findActiveConversationsByUserId(UUID userId);

    // === Contadores ===

    @Query(value = "{ 'participants.userId': ?0, 'status': 'ACTIVE' }", count = true)
    long countActiveByParticipantUserId(UUID userId);

    @Query(value = "{ 'participants': { $elemMatch: { 'userId': ?0, 'unreadCount': { $gt: 0 } } } }", count = true)
    long countWithUnreadMessages(UUID userId);

    // === Actualizaciones ===

    @Query("{ '_id': ?0, 'participants.userId': ?1 }")
    @Update("{ '$set': { 'participants.$.unreadCount': 0, 'participants.$.lastReadAt': ?2 } }")
    void markAsRead(String conversationId, UUID userId, Instant readAt);

    @Query("{ '_id': ?0, 'participants.userId': ?1 }")
    @Update("{ '$inc': { 'participants.$.unreadCount': 1 } }")
    void incrementUnreadCount(String conversationId, UUID recipientId);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'lastMessage': ?1, 'updatedAt': ?2 }, '$inc': { 'metadata.totalMessages': 1 } }")
    void updateLastMessage(String conversationId, Conversation.LastMessage lastMessage, Instant updatedAt);

    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'metadata.totalMedia': 1 } }")
    void incrementMediaCount(String conversationId);

    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'metadata.totalNearsTransferred': ?1 } }")
    void addNearsTransferred(String conversationId, Integer amount);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1 } }")
    void updateStatus(String conversationId, ConversationStatus status);

    @Query("{ '_id': ?0, 'participants.userId': ?1 }")
    @Update("{ '$set': { 'participants.$.tipsEnabled': ?2 } }")
    void updateTipsEnabled(String conversationId, UUID userId, Boolean tipsEnabled);

    // === Expiración ===

    @Query("{ 'status': 'ACTIVE', 'expiresAt': { $lte: ?0 } }")
    List<Conversation> findExpiredConversations(Instant now);

    @Query("{ 'status': 'ACTIVE', 'expiresAt': { $lte: ?0, $gt: ?1 } }")
    List<Conversation> findConversationsExpiringSoon(Instant expiresBefore, Instant expiresAfter);
}