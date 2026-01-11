package com.near.api.modules.request.controller;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.request.dto.request.*;
import com.near.api.modules.request.dto.response.*;
import com.near.api.modules.request.service.RequestService;
import com.near.api.shared.dto.ApiResponse;
import com.near.api.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;
    private final UserRepository userRepository;

    // ============================================
    // CREAR REQUEST
    // ============================================
    
    @PostMapping
    public ResponseEntity<ApiResponse<RequestDetailResponse>> createRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateRequestDTO dto) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.createRequest(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Request creada exitosamente", response));
    }

    // ============================================
    // OBTENER REQUEST
    // ============================================
    
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> getRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId) {
        UUID userId = userDetails != null ? UUID.fromString(userDetails.getUsername()) : null;
        RequestDetailResponse response = requestService.getRequestById(requestId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ============================================
    // CANCELAR REQUEST
    // ============================================
    
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @RequestParam(required = false) String reason) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        requestService.cancelRequest(requestId, userId, reason);
        return ResponseEntity.ok(ApiResponse.success("Request cancelada", null));
    }

    // ============================================
    // ACEPTAR REQUEST
    // ============================================
    
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> acceptRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @Valid @RequestBody AcceptRequestDTO dto) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.acceptRequest(requestId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success("Request aceptada", response));
    }

    // ============================================
    // ENTREGAR CONTENIDO
    // ============================================
    
    @PostMapping("/{requestId}/deliver")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> deliverContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @Valid @RequestBody DeliverContentDTO dto) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.deliverContent(requestId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success("Contenido entregado", response));
    }

    // ============================================
    // CONFIRMAR ENTREGA
    // ============================================
    
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> confirmDelivery(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.confirmDelivery(requestId, userId);
        return ResponseEntity.ok(ApiResponse.success("Entrega confirmada. Pago transferido.", response));
    }

    // ============================================
    // RECHAZAR ENTREGA
    // ============================================
    
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> rejectDelivery(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @RequestParam String reason) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.rejectDelivery(requestId, userId, reason);
        return ResponseEntity.ok(ApiResponse.success("Entrega rechazada. Disputa abierta.", response));
    }

    // ============================================
    // BÚSQUEDA GEOESPACIAL
    // ============================================
    
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyRequestResponse>>> getNearbyRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<NearbyRequestResponse> requests = requestService.findNearbyRequests(
                userId, latitude, longitude, user.getReputationStars());

        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    // ============================================
    // CALIFICACIONES
    // ============================================
    
    @PostMapping("/{requestId}/rate/responder")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> rateResponder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @Valid @RequestBody RateRequestDTO dto) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.rateAsRequester(requestId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success("Calificación enviada", response));
    }

    @PostMapping("/{requestId}/rate/requester")
    public ResponseEntity<ApiResponse<RequestDetailResponse>> rateRequester(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @Valid @RequestBody RateRequestDTO dto) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        RequestDetailResponse response = requestService.rateAsResponder(requestId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success("Calificación enviada", response));
    }

    // ============================================
    // REPORTES
    // ============================================
    
    @PostMapping("/{requestId}/report")
    public ResponseEntity<ApiResponse<Void>> reportRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID requestId,
            @Valid @RequestBody ReportRequestDTO dto) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        requestService.reportRequest(requestId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success("Reporte enviado. Será revisado por nuestro equipo.", null));
    }

    // ============================================
    // HISTORIAL
    // ============================================
    
    @GetMapping("/my/created")
    public ResponseEntity<ApiResponse<Page<RequestResponse>>> getMyCreatedRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<RequestResponse> requests = requestService.getMyRequestsAsRequester(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping("/my/responded")
    public ResponseEntity<ApiResponse<Page<RequestResponse>>> getMyRespondedRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<RequestResponse> requests = requestService.getMyRequestsAsResponder(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping("/my/active")
    public ResponseEntity<ApiResponse<Page<RequestResponse>>> getMyActiveRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<RequestResponse> requests = requestService.getActiveRequests(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }
}
