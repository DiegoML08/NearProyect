package com.near.api.modules.chat.service;

import com.near.api.modules.chat.document.Conversation;
import com.near.api.modules.chat.document.Message;
import com.near.api.modules.chat.dto.request.SendMediaMessageRequest;
import com.near.api.modules.chat.dto.request.SendMessageRequest;
import com.near.api.modules.chat.dto.response.ConversationListResponse;
import com.near.api.modules.chat.dto.response.ConversationResponse;
import com.near.api.modules.chat.dto.response.MessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    // === Conversaciones ===

    /**
     * Crea una conversación cuando se confirma una request.
     * Llamado desde RequestServiceImpl.confirmDelivery()
     */
    ConversationResponse createConversation(UUID requestId, UUID requesterId, UUID responderId,
                                            Integer originalRewardNears);

    /**
     * Obtiene una conversación por su ID
     */
    ConversationResponse getConversationById(String conversationId, UUID userId);

    /**
     * Obtiene una conversación por el ID de la request
     */
    ConversationResponse getConversationByRequestId(UUID requestId, UUID userId);

    /**
     * Lista todas las conversaciones de un usuario (paginado)
     */
    Page<ConversationListResponse> getMyConversations(UUID userId, Pageable pageable);

    /**
     * Lista conversaciones activas de un usuario
     */
    List<ConversationListResponse> getActiveConversations(UUID userId);

    /**
     * Obtiene el conteo de conversaciones con mensajes no leídos
     */
    long getUnreadConversationsCount(UUID userId);

    // === Mensajes ===

    /**
     * Envía un mensaje de texto (con propina opcional)
     */
    MessageResponse sendTextMessage(String conversationId, UUID senderId, SendMessageRequest request);

    /**
     * Envía un mensaje con media (foto/video) con precio opcional
     */
    MessageResponse sendMediaMessage(String conversationId, UUID senderId, SendMediaMessageRequest request);

    /**
     * Desbloquea una media pagada
     */
    MessageResponse unlockMedia(String conversationId, String messageId, UUID userId);

    /**
     * Obtiene los mensajes de una conversación (paginado)
     */
    Page<MessageResponse> getMessages(String conversationId, UUID userId, Pageable pageable);

    /**
     * Marca todos los mensajes de una conversación como leídos
     */
    void markConversationAsRead(String conversationId, UUID userId);

    /**
     * Marca un mensaje específico como leído
     */
    void markMessageAsRead(String conversationId, String messageId, UUID userId);

    // === Configuración ===

    /**
     * Actualiza la configuración de propinas para un usuario en una conversación
     */
    void updateTipsEnabled(String conversationId, UUID userId, Boolean enabled);

    // === Mensajes del sistema ===

    /**
     * Envía un mensaje del sistema a una conversación
     */
    MessageResponse sendSystemMessage(String conversationId, Message.SystemEventType eventType, String eventData);

    // === Tareas programadas ===

    /**
     * Expira conversaciones antiguas y limpia recursos
     */
    void expireOldConversations();

    /**
     * Envía notificaciones de conversaciones próximas a expirar
     */
    void notifyExpiringConversations();

    // === Validaciones ===

    /**
     * Verifica si un usuario es participante de una conversación
     */
    boolean isParticipant(String conversationId, UUID userId);

    /**
     * Verifica si una conversación existe y está activa
     */
    boolean isConversationActive(String conversationId);
}