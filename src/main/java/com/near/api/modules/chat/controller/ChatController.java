package com.near.api.modules.chat.controller;

import com.near.api.modules.chat.dto.request.SendMediaMessageRequest;
import com.near.api.modules.chat.dto.request.SendMessageRequest;
import com.near.api.modules.chat.dto.request.UnlockMediaRequest;
import com.near.api.modules.chat.dto.response.ConversationListResponse;
import com.near.api.modules.chat.dto.response.ConversationResponse;
import com.near.api.modules.chat.dto.response.MessageResponse;
import com.near.api.modules.chat.service.ChatService;
import com.near.api.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ============================================
    // CONVERSACIONES
    // ============================================

    /**
     * Obtener todas mis conversaciones (paginado)
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<Page<ConversationListResponse>>> getMyConversations(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<ConversationListResponse> conversations = chatService.getMyConversations(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    /**
     * Obtener conversaciones activas
     */
    @GetMapping("/conversations/active")
    public ResponseEntity<ApiResponse<List<ConversationListResponse>>> getActiveConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<ConversationListResponse> conversations = chatService.getActiveConversations(userId);
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    /**
     * Obtener conteo de conversaciones con mensajes no leídos
     */
    @GetMapping("/conversations/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadConversationsCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        long count = chatService.getUnreadConversationsCount(userId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * Obtener una conversación por ID
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ConversationResponse conversation = chatService.getConversationById(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    /**
     * Obtener una conversación por ID de request
     */
    @GetMapping("/conversations/by-request/{requestId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversationByRequestId(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ConversationResponse conversation = chatService.getConversationByRequestId(requestId, userId);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    // ============================================
    // MENSAJES
    // ============================================

    /**
     * Obtener mensajes de una conversación (paginado)
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<MessageResponse> messages = chatService.getMessages(conversationId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    /**
     * Enviar mensaje de texto (alternativa REST al WebSocket)
     */
    @PostMapping("/conversations/{conversationId}/messages/text")
    public ResponseEntity<ApiResponse<MessageResponse>> sendTextMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        MessageResponse message = chatService.sendTextMessage(conversationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Mensaje enviado", message));
    }

    /**
     * Enviar mensaje multimedia (alternativa REST al WebSocket)
     */
    @PostMapping("/conversations/{conversationId}/messages/media")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMediaMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId,
            @Valid @RequestBody SendMediaMessageRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        MessageResponse message = chatService.sendMediaMessage(conversationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Contenido enviado", message));
    }

    /**
     * Desbloquear media pagada
     */
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/unlock")
    public ResponseEntity<ApiResponse<MessageResponse>> unlockMedia(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        MessageResponse message = chatService.unlockMedia(conversationId, messageId, userId);
        return ResponseEntity.ok(ApiResponse.success("Contenido desbloqueado", message));
    }

    /**
     * Marcar conversación como leída
     */
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse<Void>> markConversationAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        chatService.markConversationAsRead(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Conversación marcada como leída", null));
    }

    /**
     * Marcar mensaje específico como leído
     */
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markMessageAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        chatService.markMessageAsRead(conversationId, messageId, userId);
        return ResponseEntity.ok(ApiResponse.success("Mensaje marcado como leído", null));
    }

    // ============================================
    // CONFIGURACIÓN
    // ============================================

    /**
     * Actualizar configuración de propinas
     */
    @PostMapping("/conversations/{conversationId}/tips")
    public ResponseEntity<ApiResponse<Void>> updateTipsEnabled(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId,
            @RequestParam Boolean enabled) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        chatService.updateTipsEnabled(conversationId, userId, enabled);
        String message = enabled ? "Propinas habilitadas" : "Propinas deshabilitadas";
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ============================================
    // VALIDACIONES
    // ============================================

    /**
     * Verificar si el usuario es participante de una conversación
     */
    @GetMapping("/conversations/{conversationId}/is-participant")
    public ResponseEntity<ApiResponse<Boolean>> isParticipant(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String conversationId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        boolean isParticipant = chatService.isParticipant(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success(isParticipant));
    }

    /**
     * Verificar si una conversación está activa
     */
    @GetMapping("/conversations/{conversationId}/is-active")
    public ResponseEntity<ApiResponse<Boolean>> isConversationActive(
            @PathVariable String conversationId) {
        boolean isActive = chatService.isConversationActive(conversationId);
        return ResponseEntity.ok(ApiResponse.success(isActive));
    }
}
