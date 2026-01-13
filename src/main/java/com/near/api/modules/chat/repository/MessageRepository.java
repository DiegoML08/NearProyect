package com.near.api.modules.chat.repository;

import com.near.api.modules.chat.document.Message;
import com.near.api.modules.chat.document.Message.MessageStatus;
import com.near.api.modules.chat.document.Message.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    // === Búsquedas básicas ===

    List<Message> findByConversationId(String conversationId);

    List<Message> findByConversationId(String conversationId, Sort sort);

    Page<Message> findByConversationId(String conversationId, Pageable pageable);

    Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    Page<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);

    // === Búsqueda por conversación y ID ===

    Optional<Message> findByIdAndConversationId(String id, String conversationId);

    // === Búsquedas por tipo ===

    @Query("{ 'conversationId': ?0, 'messageType': { $in: ['IMAGE', 'VIDEO'] } }")
    List<Message> findMediaByConversationId(String conversationId);

    @Query("{ 'conversationId': ?0, 'messageType': ?1 }")
    List<Message> findByConversationIdAndMessageType(String conversationId, MessageType messageType);

    // === Búsquedas por sender ===

    List<Message> findByConversationIdAndSenderId(String conversationId, UUID senderId);

    // === Mensajes no leídos ===

    @Query("{ 'conversationId': ?0, 'senderId': { $ne: ?1 }, 'status': { $ne: 'READ' } }")
    List<Message> findUnreadMessages(String conversationId, UUID userId);

    @Query(value = "{ 'conversationId': ?0, 'senderId': { $ne: ?1 }, 'status': { $ne: 'READ' } }", count = true)
    long countUnreadMessages(String conversationId, UUID userId);

    // === Media bloqueada ===

    @Query("{ 'conversationId': ?0, 'content.media.lockStatus': 'LOCKED' }")
    List<Message> findLockedMediaByConversationId(String conversationId);

    @Query("{ '_id': ?0, 'content.media.lockStatus': 'LOCKED' }")
    Optional<Message> findLockedMessageById(String messageId);

    // === Actualizaciones de estado ===

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': 'DELIVERED', 'deliveredAt': ?1 } }")
    void markAsDelivered(String messageId, Instant deliveredAt);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': 'READ', 'readAt': ?1 } }")
    void markAsRead(String messageId, Instant readAt);

    @Query("{ 'conversationId': ?0, 'senderId': { $ne: ?1 }, 'status': { $ne: 'READ' } }")
    @Update("{ '$set': { 'status': 'READ', 'readAt': ?2 } }")
    void markAllAsRead(String conversationId, UUID userId, Instant readAt);

    @Query("{ 'conversationId': ?0, 'senderId': { $ne: ?1 }, 'status': 'SENT' }")
    @Update("{ '$set': { 'status': 'DELIVERED', 'deliveredAt': ?2 } }")
    void markAllAsDelivered(String conversationId, UUID userId, Instant deliveredAt);

    // === Desbloquear media ===

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'content.media.lockStatus': 'UNLOCKED', 'content.media.unlockedBy': ?1, 'content.media.unlockedAt': ?2, 'content.media.unlockTransactionId': ?3 } }")
    void unlockMedia(String messageId, UUID unlockedBy, Instant unlockedAt, UUID transactionId);

    // === Contadores ===

    long countByConversationId(String conversationId);

    @Query(value = "{ 'conversationId': ?0, 'messageType': { $in: ['IMAGE', 'VIDEO'] } }", count = true)
    long countMediaByConversationId(String conversationId);

    // === Propinas ===

    @Query("{ 'conversationId': ?0, 'hasTip': true }")
    List<Message> findMessagesWithTips(String conversationId);

    @Query(value = "{ 'conversationId': ?0, 'hasTip': true }")
    long countMessagesWithTips(String conversationId);

    // === Último mensaje ===

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(String conversationId);

    // === Limpieza (para cuando expire conversación) ===

    void deleteByConversationId(String conversationId);

    // === Búsqueda por public_id de Cloudinary (para limpieza) ===

    @Query("{ 'content.media.publicId': ?0 }")
    Optional<Message> findByMediaPublicId(String publicId);

    @Query("{ 'conversationId': ?0, 'content.media.publicId': { $exists: true } }")
    List<Message> findMessagesWithMediaByConversationId(String conversationId);
}