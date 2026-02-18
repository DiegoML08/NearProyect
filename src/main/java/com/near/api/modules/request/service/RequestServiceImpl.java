package com.near.api.modules.request.service;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.chat.service.ChatService;
import com.near.api.modules.notification.dto.NotificationData;
import com.near.api.modules.request.dto.request.*;
import com.near.api.modules.request.dto.response.*;
import com.near.api.modules.request.entity.*;
import com.near.api.modules.request.entity.Request.ContentType;
import com.near.api.modules.request.entity.Request.RequestStatus;
import com.near.api.modules.request.entity.Request.TrustMode;
import com.near.api.modules.request.entity.RequestMedia.MediaType;
import com.near.api.modules.request.entity.RequestReport.ReportType;
import com.near.api.modules.request.repository.*;
import com.near.api.modules.wallet.service.WalletService;
import com.near.api.shared.exception.BadRequestException;
import com.near.api.shared.exception.ResourceNotFoundException;
import com.near.api.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.near.api.modules.notification.dto.NotificationData;
import com.near.api.modules.notification.service.NotificationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final RequestMediaRepository requestMediaRepository;
    private final RequestReportRepository requestReportRepository;
    private final RequestViewRepository requestViewRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ChatService chatService;
    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final BigDecimal MIN_TRUST_REPUTATION = new BigDecimal("4.0");
    private final NotificationService notificationService;

    // ============================================
    // CREAR REQUEST
    // ============================================
    
    @Override
    @Transactional
    public RequestDetailResponse createRequest(UUID requesterId, CreateRequestDTO dto) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Verificar saldo suficiente
        if (!walletService.hasEnoughBalance(requesterId, BigDecimal.valueOf(dto.getRewardNears()))) {
            throw new BadRequestException("Saldo insuficiente. Necesitas " + dto.getRewardNears() + " Nears");
        }

        // Crear punto de ubicación
        Point location = createPoint(dto.getLongitude(), dto.getLatitude());

        // Calcular tiempo de expiración
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(dto.getMaxDurationMinutes());

        // Calcular tiempo de trust mode
        OffsetDateTime trustModeExpiresAt = null;
        TrustMode trustMode = TrustMode.valueOf(dto.getTrustMode().toUpperCase());
        if (trustMode == TrustMode.TRUST) {
            trustModeExpiresAt = OffsetDateTime.now().plusSeconds(60); // 1 minuto
        }

        // Crear request
        Request request = Request.builder()
                .requester(requester)
                .location(location)
                .locationAddress(dto.getLocationAddress())
                .locationReference(dto.getLocationReference())
                .radiusMeters(dto.getRadiusMeters())
                .description(dto.getDescription())
                .contentType(ContentType.valueOf(dto.getContentType().toUpperCase()))
                .maxDurationMinutes(dto.getMaxDurationMinutes())
                .expiresAt(expiresAt)
                .trustMode(trustMode)
                .trustModeExpiresAt(trustModeExpiresAt)
                .rewardNears(dto.getRewardNears())
                .isAnonymousRequester(dto.getIsAnonymous() && requester.getIsAnonymous())
                .status(RequestStatus.PENDING)
                .build();

        // Calcular comisión
        request.calculateCommission();

        request = requestRepository.save(request);

        try {
            long remainingMinutes = java.time.Duration.between(
                    java.time.OffsetDateTime.now(),
                    request.getExpiresAt()
            ).toMinutes();

            notificationService.notifyNearbyUsers(
                    request.getId(),
                    requesterId,
                    dto.getLatitude(),
                    dto.getLongitude(),
                    dto.getRadiusMeters(),
                    dto.getLocationAddress(),
                    dto.getRewardNears(),
                    remainingMinutes,
                    dto.getDescription(),
                    dto.getTrustMode()
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación NEARBY_REQUEST: {}", e.getMessage());
        }

        // Congelar el saldo del requester
        walletService.processRequestPayment(requesterId, request.getId(), 
                BigDecimal.valueOf(dto.getRewardNears()));

        log.info("Request creada: {} por usuario {} con {} Nears", 
                request.getId(), requesterId, dto.getRewardNears());

        return mapToDetailResponse(request, null);
    }

    // ============================================
    // OBTENER REQUEST - CORREGIDO
    // ============================================

    @Override
    @Transactional
    public RequestDetailResponse getRequestById(UUID requestId, UUID viewerId) {
        // Usar la query con FETCH JOIN
        Request request = requestRepository.findByIdWithUsers(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        // Registrar vista si es diferente usuario
        if (viewerId != null && !viewerId.equals(request.getRequester().getId())) {
            registerView(request, viewerId);
        }

        Double distance = null;
        if (viewerId != null) {
            User viewer = userRepository.findById(viewerId).orElse(null);
            if (viewer != null && viewer.getCurrentLocation() != null) {
                distance = requestRepository.calculateDistance(
                        requestId,
                        viewer.getCurrentLocation().getY(),
                        viewer.getCurrentLocation().getX()
                );
            }
        }

        return mapToDetailResponse(request, distance);
    }


    // ============================================
    // CANCELAR REQUEST
    // ============================================

    @Override
    @Transactional
    public void cancelRequest(UUID requestId, UUID userId, String reason) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        // Determinar rol del usuario
        boolean isRequester = request.getRequester().getId().equals(userId);
        boolean isResponder = request.getResponder() != null && request.getResponder().getId().equals(userId);

        // Permitir cancelar al requester O al responder (solo si está ACCEPTED)
        if (!isRequester && !(isResponder && request.getStatus() == RequestStatus.ACCEPTED)) {
            throw new UnauthorizedException("No tienes permiso para cancelar esta request");
        }

        // Si el responder cancela, solo liberar (re-publicar como PENDING)
        if (isResponder) {
            releaseRequest(request, "Responder abortó la aceptación");
            log.info("Request {} liberada por responder {}", requestId, userId);
            return;
        }

        // === Flujo de cancelación por el REQUESTER ===

        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.ACCEPTED) {
            throw new BadRequestException("Solo se pueden cancelar requests pendientes o aceptadas");
        }

        boolean wasAccepted = request.getStatus() == RequestStatus.ACCEPTED;
        UUID oldResponderId = wasAccepted && request.getResponder() != null
                ? request.getResponder().getId() : null;

        User canceller = userRepository.findById(userId).orElseThrow();

        request.setStatus(RequestStatus.CANCELLED);
        request.setCancelledAt(OffsetDateTime.now());
        request.setCancelledBy(canceller);
        request.setCancellationReason(reason);

        if (wasAccepted) {
            request.setResponder(null);
            request.setAcceptedAt(null);
            request.setAcceptDeadlineAt(null);
        }

        requestRepository.save(request);

        // Reembolsar al requester
        walletService.processRequestRefund(userId, requestId, BigDecimal.valueOf(request.getRewardNears()));

        // Notificar al responder si estaba aceptada
        if (oldResponderId != null) {
            try {
                notificationService.sendToUser(
                        oldResponderId,
                        NotificationData.requestCancelled(requestId, request.getLocationAddress())
                );
            } catch (Exception e) {
                log.warn("Error notificando cancelación: {}", e.getMessage());
            }
        }

        log.info("Request {} cancelada por usuario {}", requestId, userId);
    }
    // ============================================
    // ACEPTAR REQUEST
    // ============================================
    
    @Override
    @Transactional
    public RequestDetailResponse acceptRequest(UUID requestId, UUID responderId, AcceptRequestDTO dto) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        // Validaciones
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Esta request ya no está disponible");
        }

        if (request.isExpired()) {
            throw new BadRequestException("Esta request ha expirado");
        }

        if (request.getRequester().getId().equals(responderId)) {
            throw new BadRequestException("No puedes aceptar tu propia request");
        }

        User responder = userRepository.findById(responderId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Verificar trust mode
        if (request.isTrustModeActive()) {
            if (responder.getReputationStars().compareTo(MIN_TRUST_REPUTATION) < 0) {
                throw new BadRequestException(
                        "Esta request está en modo confianza. Necesitas al menos 4 estrellas de reputación");
            }
        }

        // Verificar distancia
        Double distance = requestRepository.calculateDistance(
                requestId, dto.getCurrentLatitude(), dto.getCurrentLongitude());

        if (distance != null && distance > request.getRadiusMeters()) {
            throw new BadRequestException(
                    String.format("Estás muy lejos de la ubicación solicitada. Distancia: %.0f metros", distance));
        }

        // Actualizar request
        request.setResponder(responder);
        request.setStatus(RequestStatus.ACCEPTED);
        request.setAcceptedAt(OffsetDateTime.now());
        request.setAcceptDeadlineAt(OffsetDateTime.now().plusMinutes(5));
        request.setIsAnonymousResponder(dto.getAcceptAnonymously() && responder.getIsAnonymous());

        request = requestRepository.save(request);

        try {
            String responderName = getDisplayName(request.getResponder());
            notificationService.notifyRequestAccepted(
                    request.getRequester().getId(),
                    requestId,
                    responderName,
                    request.getLocationAddress()
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación REQUEST_ACCEPTED: {}", e.getMessage());
        }

        log.info("Request {} aceptada por usuario {}", requestId, responderId);

        return mapToDetailResponse(request, distance);
    }

    // ============================================
    // ENTREGAR CONTENIDO
    // ============================================
    
    @Override
    @Transactional
    public RequestDetailResponse deliverContent(UUID requestId, UUID responderId, DeliverContentDTO dto) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        // Validaciones
        if (request.getResponder() == null || !request.getResponder().getId().equals(responderId)) {
            throw new UnauthorizedException("No eres el responder de esta request");
        }

        if (request.getStatus() != RequestStatus.ACCEPTED && request.getStatus() != RequestStatus.IN_PROGRESS) {
            throw new BadRequestException("No puedes entregar contenido en este estado");
        }

        // Guardar media
        for (DeliverContentDTO.MediaItem item : dto.getMediaItems()) {
            Point captureLocation = null;
            if (item.getCaptureLatitude() != null && item.getCaptureLongitude() != null) {
                captureLocation = createPoint(item.getCaptureLongitude(), item.getCaptureLatitude());
            }

            RequestMedia media = RequestMedia.builder()
                    .request(request)
                    .mediaType(MediaType.valueOf(item.getMediaType().toUpperCase()))
                    .url(item.getUrl())
                    .thumbnailUrl(item.getThumbnailUrl())
                    .publicId(item.getPublicId())
                    .fileSizeBytes(item.getFileSizeBytes())
                    .width(item.getWidth())
                    .height(item.getHeight())
                    .durationSeconds(item.getDurationSeconds())
                    .captureLocation(captureLocation)
                    .captureTimestamp(item.getCaptureTimestamp())
                    .deviceInfo(item.getDeviceInfo())
                    .build();

            requestMediaRepository.save(media);
        }

        // Actualizar estado
        request.setStatus(RequestStatus.DELIVERED);
        request.setDeliveredAt(OffsetDateTime.now());

        request = requestRepository.save(request);

        try {
            String responderName = getDisplayName(request.getResponder());
            notificationService.notifyContentDelivered(
                    request.getRequester().getId(),
                    requestId,
                    responderName,
                    request.getLocationAddress()
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación CONTENT_DELIVERED: {}", e.getMessage());
        }

        log.info("Contenido entregado para request {} por usuario {}", requestId, responderId);

        return mapToDetailResponse(request, null);
    }

    // ============================================
    // CONFIRMAR ENTREGA
    // ============================================
    
    @Override
    @Transactional
    public RequestDetailResponse confirmDelivery(UUID requestId, UUID requesterId) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        // Validaciones
        if (!request.getRequester().getId().equals(requesterId)) {
            throw new UnauthorizedException("No eres el creador de esta request");
        }

        if (request.getStatus() != RequestStatus.DELIVERED) {
            throw new BadRequestException("No hay contenido pendiente de confirmar");
        }

        // Procesar pago al responder
        walletService.releaseFrozenBalance(requesterId, BigDecimal.valueOf(request.getRewardNears()));
        walletService.processRequestEarning(
                request.getResponder().getId(),
                requestId,
                BigDecimal.valueOf(request.getRewardNears()),
                BigDecimal.valueOf(request.getCommissionAmount())
        );

        // Actualizar estado
        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(OffsetDateTime.now());

        request = requestRepository.save(request);

        try {
            notificationService.notifyDeliveryConfirmed(
                    request.getResponder().getId(),
                    requestId,
                    request.getRewardNears()
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación DELIVERY_CONFIRMED: {}", e.getMessage());
        }

        // ✅ CREAR CONVERSACIÓN AUTOMÁTICAMENTE
        try {
            chatService.createConversation(
                    requestId,
                    request.getRequester().getId(),
                    request.getResponder().getId(),
                    request.getRewardNears()
            );
            log.info("Conversación creada para request {}", requestId);
        } catch (Exception e) {
            log.error("Error creando conversación para request {}: {}", requestId, e.getMessage());
            // No lanzamos excepción para no afectar la confirmación de la request
        }

        log.info("Request {} completada. Pago de {} Nears transferido a {}",
                requestId, request.getFinalReward(), request.getResponder().getId());

        return mapToDetailResponse(request, null);
    }

    @Override
    @Transactional
    public RequestDetailResponse rejectDelivery(UUID requestId, UUID requesterId, String reason) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        if (!request.getRequester().getId().equals(requesterId)) {
            throw new UnauthorizedException("No eres el creador de esta request");
        }

        if (request.getStatus() != RequestStatus.DELIVERED) {
            throw new BadRequestException("No hay contenido pendiente de revisar");
        }

        // Verificar que aún tiene tiempo restante
        boolean hasTimeLeft = OffsetDateTime.now().isBefore(request.getExpiresAt());

        // Penalizar al requester: -0.1 en su promedio de reputación
        try {
            penalizeRequester(requesterId);
        } catch (Exception e) {
            log.warn("Error aplicando penalización al requester {}: {}", requesterId, e.getMessage());
        }

        if (hasTimeLeft) {
            // Re-publicar la request con tiempo restante
            releaseRequest(request, "Entrega rechazada por el requester: " + reason);

            // Limpiar media entregada
            requestMediaRepository.deleteByRequestId(requestId);

            log.info("Request {} rechazada y re-publicada con tiempo restante. Requester {} penalizado.",
                    requestId, requesterId);
        } else {
            // Si ya no tiene tiempo, expirar y reembolsar
            request.setStatus(RequestStatus.EXPIRED);
            request.setResponder(null);
            requestRepository.save(request);

            walletService.processRequestRefund(
                    requesterId, requestId, BigDecimal.valueOf(request.getRewardNears())
            );

            // Limpiar media
            requestMediaRepository.deleteByRequestId(requestId);

            log.info("Request {} rechazada sin tiempo restante. Expirada y reembolsada.", requestId);
        }

        // Recargar para devolver la respuesta actualizada
        Request updatedRequest = requestRepository.findByIdWithUsers(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        return mapToDetailResponse(updatedRequest, null);
    }

    /**
     * Penaliza al requester bajando 0.1 de su promedio de reputación
     */
    private void penalizeRequester(UUID requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        BigDecimal currentReputation = requester.getReputationStars();
        BigDecimal penalty = new BigDecimal("0.1");
        BigDecimal newReputation = currentReputation.subtract(penalty);

        // No bajar de 0
        if (newReputation.compareTo(BigDecimal.ZERO) < 0) {
            newReputation = BigDecimal.ZERO;
        }

        requester.setReputationStars(newReputation);
        userRepository.save(requester);

        log.info("Requester {} penalizado: {} → {} estrellas",
                requesterId, currentReputation, newReputation);
    }

    // ============================================
// BÚSQUEDA GEOESPACIAL - CORREGIDA
// ============================================

    @Override
    public List<NearbyRequestResponse> findNearbyRequests(UUID userId, double lat, double lng,
                                                          BigDecimal userReputation) {
        List<Request> requests;

        // Si el usuario tiene buena reputación, puede ver también requests en trust mode activo
        if (userReputation != null && userReputation.compareTo(MIN_TRUST_REPUTATION) >= 0) {
            // Usuario de confianza: ver todas las requests cercanas (incluyendo trust mode)
            requests = requestRepository.findNearbyPendingRequests(userId, lat, lng);
        } else {
            // Usuario normal: solo ver requests en modo "all" o trust mode expirado
            requests = requestRepository.findNearbyAllModeRequests(userId, lat, lng);
        }

        return requests.stream()
                .map(r -> {
                    Double distance = requestRepository.calculateDistance(r.getId(), lat, lng);
                    // Cargar el requester si es necesario
                    return mapToNearbyResponse(r, distance);
                })
                .collect(Collectors.toList());
    }


    // ============================================
    // CALIFICACIONES
    // ============================================
    
    @Override
    @Transactional
    public RequestDetailResponse rateAsRequester(UUID requestId, UUID requesterId, RateRequestDTO dto) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        if (!request.getRequester().getId().equals(requesterId)) {
            throw new UnauthorizedException("No eres el creador de esta request");
        }

        if (request.getStatus() != RequestStatus.COMPLETED) {
            throw new BadRequestException("Solo puedes calificar requests completadas");
        }

        if (request.getResponderRating() != null) {
            throw new BadRequestException("Ya calificaste al responder");
        }

        // El requester califica al responder
        request.setResponderRating(dto.getRating());
        request.setResponderReview(dto.getReview());
        request.setResponderRatedAt(OffsetDateTime.now());

        request = requestRepository.save(request);

        // Actualizar reputación del responder
        updateUserReputation(request.getResponder().getId());

        log.info("Requester {} calificó al responder {} con {} estrellas",
                requesterId, request.getResponder().getId(), dto.getRating());

        return mapToDetailResponse(request, null);
    }

    @Override
    @Transactional
    public RequestDetailResponse rateAsResponder(UUID requestId, UUID responderId, RateRequestDTO dto) {
        Request request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        if (request.getResponder() == null || !request.getResponder().getId().equals(responderId)) {
            throw new UnauthorizedException("No eres el responder de esta request");
        }

        if (request.getStatus() != RequestStatus.COMPLETED) {
            throw new BadRequestException("Solo puedes calificar requests completadas");
        }

        if (request.getRequesterRating() != null) {
            throw new BadRequestException("Ya calificaste al requester");
        }

        // El responder califica al requester
        request.setRequesterRating(dto.getRating());
        request.setRequesterReview(dto.getReview());
        request.setRequesterRatedAt(OffsetDateTime.now());

        request = requestRepository.save(request);

        // Actualizar reputación del requester
        updateUserReputation(request.getRequester().getId());

        log.info("Responder {} calificó al requester {} con {} estrellas",
                responderId, request.getRequester().getId(), dto.getRating());

        return mapToDetailResponse(request, null);
    }

    // ============================================
    // REPORTES
    // ============================================
    
    @Override
    @Transactional
    public void reportRequest(UUID requestId, UUID reporterId, ReportRequestDTO dto) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request no encontrada"));

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        User reportedUser = userRepository.findById(dto.getReportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario reportado no encontrado"));

        // Verificar que el reportador es parte de la request
        boolean isRequester = request.getRequester().getId().equals(reporterId);
        boolean isResponder = request.getResponder() != null && 
                              request.getResponder().getId().equals(reporterId);

        if (!isRequester && !isResponder) {
            throw new UnauthorizedException("No puedes reportar una request en la que no participas");
        }

        // Verificar que no se reporte a sí mismo
        if (reporterId.equals(dto.getReportedUserId())) {
            throw new BadRequestException("No puedes reportarte a ti mismo");
        }

        // Verificar que no existe reporte duplicado
        if (requestReportRepository.existsByRequestIdAndReporterId(requestId, reporterId)) {
            throw new BadRequestException("Ya has reportado esta request");
        }

        RequestReport report = RequestReport.builder()
                .request(request)
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reportType(ReportType.valueOf(dto.getReportType()))
                .description(dto.getDescription())
                .evidenceUrls(dto.getEvidenceUrls())
                .build();

        requestReportRepository.save(report);

        log.info("Reporte creado para request {} por usuario {} contra usuario {}",
                requestId, reporterId, dto.getReportedUserId());
    }

    // ============================================
// HISTORIAL - CORREGIDO
// ============================================

    @Override
    @Transactional(readOnly = true)
    public Page<RequestResponse> getMyRequestsAsRequester(UUID userId, Pageable pageable) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId, pageable)
                .map(r -> mapToResponse(r, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RequestResponse> getMyRequestsAsResponder(UUID userId, Pageable pageable) {
        return requestRepository.findByResponderIdOrderByCreatedAtDesc(userId, pageable)
                .map(r -> mapToResponse(r, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RequestResponse> getActiveRequests(UUID userId, Pageable pageable) {
        List<RequestStatus> activeStatuses = List.of(
                RequestStatus.PENDING,
                RequestStatus.ACCEPTED,
                RequestStatus.IN_PROGRESS,
                RequestStatus.DELIVERED
        );
        return requestRepository.findByRequesterIdAndStatusInOrderByCreatedAtDesc(userId, activeStatuses, pageable)
                .map(r -> mapToResponse(r, null));
    }

    // ============================================
    // TAREAS PROGRAMADAS
    // ============================================

    @Override
    @Scheduled(fixedRate = 60000) // Cada minuto
    @Transactional
    public void expireOldRequests() {
        OffsetDateTime now = OffsetDateTime.now();

        // Buscar requests PENDING que expiraron
        List<Request> expiredPending = requestRepository.findByStatusAndExpiresAtBefore(
                RequestStatus.PENDING, now);

        for (Request request : expiredPending) {
            try {
                request.setStatus(RequestStatus.EXPIRED);
                requestRepository.save(request);

                // Reembolsar Nears congelados
                walletService.processRequestRefund(
                        request.getRequester().getId(),
                        request.getId(),
                        BigDecimal.valueOf(request.getRewardNears())
                );

                log.info("Request PENDING {} expirada. Reembolso de {} Nears.",
                        request.getId(), request.getRewardNears());
            } catch (Exception e) {
                log.error("Error expirando request {}: {}", request.getId(), e.getMessage());
            }
        }

        if (!expiredPending.isEmpty()) {
            log.info("Se expiraron {} requests PENDING", expiredPending.size());
        }
    }

    @Override
    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    @Transactional
    public void processRefundsForExpiredRequests() {
        List<Request> expiredRequests = requestRepository.findByStatusAndExpiresAtBefore(
                RequestStatus.EXPIRED, OffsetDateTime.now().minusMinutes(5));

        for (Request request : expiredRequests) {
            try {
                walletService.processRequestRefund(
                        request.getRequester().getId(),
                        request.getId(),
                        BigDecimal.valueOf(request.getRewardNears())
                );
                log.info("Reembolso procesado para request expirada: {}", request.getId());
            } catch (Exception e) {
                log.error("Error procesando reembolso para request {}: {}", request.getId(), e.getMessage());
            }
        }
    }

    /**
     * Cada 30 segundos: liberar requests ACCEPTED que pasaron 5 min sin delivery
     * y re-publicarlas como PENDING con tiempo restante.
     */
    @Scheduled(fixedRate = 30000) // Cada 30 segundos
    @Transactional
    public void releaseExpiredAcceptedRequests() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1) ACCEPTED que pasaron el deadline de 5 min PERO aún tienen tiempo global
        List<Request> pastDeadline = requestRepository.findAcceptedPastDeadline(now);
        for (Request request : pastDeadline) {
            try {
                releaseRequest(request, "El responder no envió contenido en 5 minutos");
            } catch (Exception e) {
                log.error("Error liberando request {}: {}", request.getId(), e.getMessage());
            }
        }

        // 2) ACCEPTED cuyo tiempo global también expiró → EXPIRED + reembolso
        List<Request> acceptedAndExpired = requestRepository.findAcceptedAndExpired(now);
        for (Request request : acceptedAndExpired) {
            try {
                request.setStatus(RequestStatus.EXPIRED);
                request.setResponder(null);
                request.setAcceptedAt(null);
                request.setAcceptDeadlineAt(null);
                requestRepository.save(request);

                // Reembolsar
                walletService.processRequestRefund(
                        request.getRequester().getId(),
                        request.getId(),
                        BigDecimal.valueOf(request.getRewardNears())
                );

                log.info("Request ACCEPTED {} expirada globalmente. Reembolso procesado.", request.getId());
            } catch (Exception e) {
                log.error("Error expirando request accepted {}: {}", request.getId(), e.getMessage());
            }
        }

        if (!pastDeadline.isEmpty() || !acceptedAndExpired.isEmpty()) {
            log.info("Liberadas {} requests (deadline), Expiradas {} requests (tiempo global)",
                    pastDeadline.size(), acceptedAndExpired.size());
        }
    }

    // ============================================
    // MÉTODOS AUXILIARES
    // ============================================

    /**
     * Libera una request: quita al responder y la re-publica como PENDING
     * con el tiempo restante que le quede.
     */
    private void releaseRequest(Request request, String reason) {
        UUID oldResponderId = request.getResponder() != null ? request.getResponder().getId() : null;

        // Re-publicar como PENDING
        request.setStatus(RequestStatus.PENDING);
        request.setResponder(null);
        request.setAcceptedAt(null);
        request.setAcceptDeadlineAt(null);
        request.setDeliveredAt(null);

        // Limpiar media entregada si la hubo (para DELIVERED→PENDING)
        // No borrar de Cloudinary aquí, solo desvincular

        request = requestRepository.save(request);

        log.info("Request {} liberada y re-publicada. Motivo: {}. Tiempo restante: {} segundos",
                request.getId(), reason,
                java.time.temporal.ChronoUnit.SECONDS.between(OffsetDateTime.now(), request.getExpiresAt()));

        // Notificar al responder anterior

        if (oldResponderId != null) {
            try {
                notificationService.sendToUser(
                        oldResponderId,
                        NotificationData.requestReleased(request.getId(), reason)
                );
            } catch (Exception e) {
                log.warn("Error notificando liberación: {}", e.getMessage());
            }
        }
    }

    private Point createPoint(double longitude, double latitude) {
        return geometryFactory.createPoint(new Coordinate(longitude, latitude));
    }

    private void registerView(Request request, UUID viewerId) {
        if (!requestViewRepository.existsByRequestIdAndUserId(request.getId(), viewerId)) {
            User viewer = userRepository.findById(viewerId).orElse(null);
            if (viewer != null) {
                Double distance = null;
                Point userLocation = viewer.getCurrentLocation();

                if (userLocation != null) {
                    distance = requestRepository.calculateDistance(
                            request.getId(),
                            userLocation.getY(),
                            userLocation.getX()
                    );
                }

                RequestView view = RequestView.builder()
                        .request(request)
                        .user(viewer)
                        .userLocation(userLocation)
                        .distanceMeters(distance != null ? distance.intValue() : null)
                        .wasTrustEligible(viewer.getReputationStars().compareTo(MIN_TRUST_REPUTATION) >= 0)
                        .build();

                requestViewRepository.save(view);
                requestRepository.incrementViewCount(request.getId());
            }
        }
    }

    private void updateUserReputation(UUID userId) {
        BigDecimal avgAsResponder = requestRepository.calculateAverageRatingAsResponder(userId);
        BigDecimal avgAsRequester = requestRepository.calculateAverageRatingAsRequester(userId);
        Long countAsResponder = requestRepository.countRatingsAsResponder(userId);
        Long countAsRequester = requestRepository.countRatingsAsRequester(userId);

        long totalCount = (countAsResponder != null ? countAsResponder : 0)
                + (countAsRequester != null ? countAsRequester : 0);

        if (totalCount > 0) {
            // Promedio ponderado de ambos roles
            double sumResponder = (avgAsResponder != null ? avgAsResponder.doubleValue() : 0)
                    * (countAsResponder != null ? countAsResponder : 0);
            double sumRequester = (avgAsRequester != null ? avgAsRequester.doubleValue() : 0)
                    * (countAsRequester != null ? countAsRequester : 0);

            BigDecimal combinedAvg = BigDecimal.valueOf((sumResponder + sumRequester) / totalCount)
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            User user = userRepository.findById(userId).orElseThrow();
            user.setReputationStars(combinedAvg);
            user.setTotalRatingsReceived((int) totalCount);
            userRepository.save(user);

            log.info("Reputación actualizada para usuario {}: {} estrellas ({} valoraciones)",
                    userId, combinedAvg, totalCount);
        }
    }

    // ============================================
    // MAPPERS
    // ============================================
    
    private RequestResponse mapToResponse(Request request, Double distance) {
        long remainingSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), request.getExpiresAt());

        return RequestResponse.builder()
                .id(request.getId())
                .latitude(request.getLocation().getY())
                .longitude(request.getLocation().getX())
                .locationAddress(request.getLocationAddress())
                .locationReference(request.getLocationReference())
                .radiusMeters(request.getRadiusMeters())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .maxDurationMinutes(request.getMaxDurationMinutes())
                .expiresAt(request.getExpiresAt())
                .remainingSeconds(Math.max(0, remainingSeconds))
                .trustMode(request.getTrustMode())
                .isTrustModeActive(request.isTrustModeActive())
                .rewardNears(request.getRewardNears())
                .finalReward(request.getFinalReward())
                .commissionPercentage(request.getCommissionPercentage())
                .status(request.getStatus())
                .requester(mapRequesterInfo(request))
                .responder(request.getResponder() != null ? mapResponderInfo(request) : null)
                .viewCount(request.getViewCount())
                .distanceMeters(distance)
                .createdAt(request.getCreatedAt())
                .build();
    }

    private RequestDetailResponse mapToDetailResponse(Request request, Double distance) {
        List<RequestMedia> mediaList = requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());

        long remainingSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), request.getExpiresAt());

        return RequestDetailResponse.builder()
                .id(request.getId())
                .latitude(request.getLocation().getY())
                .longitude(request.getLocation().getX())
                .locationAddress(request.getLocationAddress())
                .locationReference(request.getLocationReference())
                .radiusMeters(request.getRadiusMeters())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .maxDurationMinutes(request.getMaxDurationMinutes())
                .expiresAt(request.getExpiresAt())
                .remainingSeconds(Math.max(0, remainingSeconds))
                .trustMode(request.getTrustMode())
                .isTrustModeActive(request.isTrustModeActive())
                .trustModeExpiresAt(request.getTrustModeExpiresAt())
                .rewardNears(request.getRewardNears())
                .finalReward(request.getFinalReward())
                .commissionAmount(request.getCommissionAmount())
                .commissionPercentage(request.getCommissionPercentage())
                .status(request.getStatus())
                .acceptedAt(request.getAcceptedAt())
                .deliveredAt(request.getDeliveredAt())
                .completedAt(request.getCompletedAt())
                .cancelledAt(request.getCancelledAt())
                .cancellationReason(request.getCancellationReason())
                .requester(mapUserInfo(request.getRequester(), request.getIsAnonymousRequester()))
                .responder(request.getResponder() != null ?
                        mapUserInfo(request.getResponder(), request.getIsAnonymousResponder()) : null)
                .requesterRating(request.getRequesterRating() != null ?
                        RequestDetailResponse.RatingInfo.builder()
                                .rating(request.getRequesterRating())
                                .review(request.getRequesterReview())
                                .ratedAt(request.getRequesterRatedAt())
                                .build() : null)
                .responderRating(request.getResponderRating() != null ?
                        RequestDetailResponse.RatingInfo.builder()
                                .rating(request.getResponderRating())
                                .review(request.getResponderReview())
                                .ratedAt(request.getResponderRatedAt())
                                .build() : null)
                .media(mediaList.stream().map(this::mapMediaResponse).collect(Collectors.toList()))
                .viewCount(request.getViewCount())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private NearbyRequestResponse mapToNearbyResponse(Request request, Double distance) {
        long remainingSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), request.getExpiresAt());
        String preview = request.getDescription().length() > 100 ?
                request.getDescription().substring(0, 100) + "..." : request.getDescription();

        // Obtener datos del requester de forma segura
        User requester = request.getRequester();
        String requesterDisplayName;
        BigDecimal requesterReputation;
        boolean isAnonymous = request.getIsAnonymousRequester() != null && request.getIsAnonymousRequester();

        if (requester != null) {
            requesterDisplayName = isAnonymous ?
                    "Anónimo " + (requester.getAnonymousCode() != null ? requester.getAnonymousCode() : "***") :
                    (requester.getFullName() != null ? requester.getFullName() : "Usuario");
            requesterReputation = requester.getReputationStars();
        } else {
            requesterDisplayName = "Usuario";
            requesterReputation = BigDecimal.ZERO;
        }

        return NearbyRequestResponse.builder()
                .id(request.getId())
                .latitude(request.getLocation().getY())
                .longitude(request.getLocation().getX())
                .locationAddress(request.getLocationAddress())
                .radiusMeters(request.getRadiusMeters())
                .distanceMeters(distance)
                .descriptionPreview(preview)
                .contentType(request.getContentType())
                .expiresAt(request.getExpiresAt())
                .remainingSeconds(Math.max(0, remainingSeconds))
                .rewardNears(request.getRewardNears())
                .trustMode(request.getTrustMode())
                .isTrustModeActive(request.isTrustModeActive())
                .requesterDisplayName(requesterDisplayName)
                .requesterReputation(requesterReputation)
                .isAnonymousRequester(isAnonymous)
                .createdAt(request.getCreatedAt())
                .build();
    }


    private RequestResponse.RequesterInfo mapRequesterInfo(Request request) {
        User requester = request.getRequester();
        return RequestResponse.RequesterInfo.builder()
                .id(requester.getId())
                .displayName(request.getIsAnonymousRequester() ?
                        "Anónimo " + requester.getAnonymousCode() : requester.getFullName())
                .profilePhotoUrl(request.getIsAnonymousRequester() ? null : requester.getProfilePhotoUrl())
                .reputationStars(requester.getReputationStars())
                .isAnonymous(request.getIsAnonymousRequester())
                .build();
    }

    private RequestResponse.ResponderInfo mapResponderInfo(Request request) {
        User responder = request.getResponder();
        return RequestResponse.ResponderInfo.builder()
                .id(responder.getId())
                .displayName(request.getIsAnonymousResponder() ?
                        "Anónimo " + responder.getAnonymousCode() : responder.getFullName())
                .profilePhotoUrl(request.getIsAnonymousResponder() ? null : responder.getProfilePhotoUrl())
                .reputationStars(responder.getReputationStars())
                .isAnonymous(request.getIsAnonymousResponder())
                .build();
    }

    private RequestDetailResponse.UserInfo mapUserInfo(User user, Boolean isAnonymous) {
        return RequestDetailResponse.UserInfo.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .displayName(isAnonymous ? "Anónimo " + user.getAnonymousCode() : user.getFullName())
                .profilePhotoUrl(isAnonymous ? null : user.getProfilePhotoUrl())
                .reputationStars(user.getReputationStars())
                .totalRatingsReceived(user.getTotalRatingsReceived())
                .isAnonymous(isAnonymous)
                .isVerified(user.getIsVerified())
                .build();
    }

    private RequestMediaResponse mapMediaResponse(RequestMedia media) {
        return RequestMediaResponse.builder()
                .id(media.getId())
                .mediaType(media.getMediaType())
                .url(media.getUrl())
                .thumbnailUrl(media.getThumbnailUrl())
                .fileSizeBytes(media.getFileSizeBytes())
                .width(media.getWidth())
                .height(media.getHeight())
                .durationSeconds(media.getDurationSeconds())
                .isVerified(media.getIsVerified())
                .createdAt(media.getCreatedAt())
                .build();
    }

    private String getDisplayName(User user) {
        if (user == null) return "Usuario";
        if (user.getIsAnonymous()) {
            return "Usuario #" + (user.getAnonymousCode() != null
                    ? user.getAnonymousCode().substring(0, 4)
                    : "????");
        }
        if (user.getFullName() != null && !user.getFullName().isEmpty()) {
            return user.getFullName().split(" ")[0];
        }
        return "Usuario";
    }

}
