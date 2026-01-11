package com.near.api.modules.request.service;

import com.near.api.modules.request.dto.request.*;
import com.near.api.modules.request.dto.response.*;
import com.near.api.modules.request.entity.Request;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RequestService {

    // === CRUD ===
    RequestDetailResponse createRequest(UUID requesterId, CreateRequestDTO dto);
    
    RequestDetailResponse getRequestById(UUID requestId, UUID viewerId);
    
    void cancelRequest(UUID requestId, UUID userId, String reason);

    // === Flujo principal ===
    RequestDetailResponse acceptRequest(UUID requestId, UUID responderId, AcceptRequestDTO dto);
    
    RequestDetailResponse deliverContent(UUID requestId, UUID responderId, DeliverContentDTO dto);
    
    RequestDetailResponse confirmDelivery(UUID requestId, UUID requesterId);
    
    RequestDetailResponse rejectDelivery(UUID requestId, UUID requesterId, String reason);

    // === Búsqueda geoespacial ===
    List<NearbyRequestResponse> findNearbyRequests(UUID userId, double lat, double lng, BigDecimal userReputation);

    // === Calificaciones ===
    RequestDetailResponse rateAsRequester(UUID requestId, UUID requesterId, RateRequestDTO dto);
    
    RequestDetailResponse rateAsResponder(UUID requestId, UUID responderId, RateRequestDTO dto);

    // === Reportes ===
    void reportRequest(UUID requestId, UUID reporterId, ReportRequestDTO dto);

    // === Historial ===
    Page<RequestResponse> getMyRequestsAsRequester(UUID userId, Pageable pageable);
    
    Page<RequestResponse> getMyRequestsAsResponder(UUID userId, Pageable pageable);
    
    Page<RequestResponse> getActiveRequests(UUID userId, Pageable pageable);

    // === Tareas programadas ===
    void expireOldRequests();
    
    void processRefundsForExpiredRequests();
}
