package com.near.api.modules.request.service;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
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

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final BigDecimal MIN_TRUST_REPUTATION = new BigDecimal("4.0");

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

        // Congelar el saldo del requester
        walletService.processRequestPayment(requesterId, request.getId(), 
                BigDecimal.valueOf(dto.getRewardNears()));

        log.info("Request creada: {} por usuario {} con {} Nears", 
                request.getId(), requesterId, dto.getRewardNears());

        return mapToDetailResponse(request, null);
    }

    // ============================================
    // OBTENER REQUEST
    // ============================================
    
    @Override
    @Transactional
    public RequestDetailResponse getRequestById(UUID requestId, UUID viewerId) {
        Request request = requestRepository.findById(requestId)
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

        // Solo el requester puede cancelar
        if (!request.getRequester().getId().equals(userId)) {
            throw new UnauthorizedException("Solo el creador puede cancelar la request");
        }

        // Solo se puede cancelar si está pendiente
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Solo se pueden cancelar requests pendientes");
        }

        User canceller = userRepository.findById(userId).orElseThrow();

        request.setStatus(RequestStatus.CANCELLED);
        request.setCancelledAt(OffsetDateTime.now());
        request.setCancelledBy(canceller);
        request.setCancellationReason(reason);

        requestRepository.save(request);

        // Reembolsar al requester
        walletService.processRequestRefund(userId, requestId, BigDecimal.valueOf(request.getRewardNears()));

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
        request.setIsAnonymousResponder(dto.getAcceptAnonymously() && responder.getIsAnonymous());

        request = requestRepository.save(request);

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

        log.info("Request {} completada. Pago de {} Nears transferido a {}",
                requestId, request.getFinalReward(), request.getResponder().getId());

        return mapToDetailResponse(request, null);
    }

    // ============================================
    // RECHAZAR ENTREGA
    // ============================================
    
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

        // Marcar como disputa
        request.setStatus(RequestStatus.DISPUTED);

        request = requestRepository.save(request);

        log.info("Entrega rechazada para request {}. Razón: {}", requestId, reason);

        return mapToDetailResponse(request, null);
    }

    // ============================================
    // BÚSQUEDA GEOESPACIAL
    // ============================================
    
    @Override
    public List<NearbyRequestResponse> findNearbyRequests(UUID userId, double lat, double lng,
                                                          BigDecimal userReputation) {
        List<Request> requests;

        // Si el usuario tiene buena reputación, mostrar también requests en trust mode
        if (userReputation != null && userReputation.compareTo(MIN_TRUST_REPUTATION) >= 0) {
            // Usuario de confianza: ver todas las requests cercanas
            requests = requestRepository.findNearbyPendingRequests(lat, lng);
        } else {
            // Usuario normal: solo ver requests en modo "all" o trust mode expirado
            requests = requestRepository.findNearbyAllModeRequests(lat, lng);
        }

        return requests.stream()
                .filter(r -> !r.getRequester().getId().equals(userId)) // No mostrar propias
                .map(r -> {
                    Double distance = requestRepository.calculateDistance(r.getId(), lat, lng);
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
    // HISTORIAL
    // ============================================
    
    @Override
    public Page<RequestResponse> getMyRequestsAsRequester(UUID userId, Pageable pageable) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId, pageable)
                .map(r -> mapToResponse(r, null));
    }

    @Override
    public Page<RequestResponse> getMyRequestsAsResponder(UUID userId, Pageable pageable) {
        return requestRepository.findByResponderIdOrderByCreatedAtDesc(userId, pageable)
                .map(r -> mapToResponse(r, null));
    }

    @Override
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
        int expired = requestRepository.expireOldRequests(OffsetDateTime.now());
        if (expired > 0) {
            log.info("Se expiraron {} requests", expired);
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

    // ============================================
    // MÉTODOS AUXILIARES
    // ============================================
    
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
        BigDecimal avgRating = requestRepository.calculateAverageRating(userId);
        if (avgRating != null) {
            User user = userRepository.findById(userId).orElseThrow();
            user.setReputationStars(avgRating);
            user.setTotalRatingsReceived(user.getTotalRatingsReceived() + 1);
            userRepository.save(user);
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
                .requesterDisplayName(request.getIsAnonymousRequester() ?
                        "Anónimo " + request.getRequester().getAnonymousCode() :
                        request.getRequester().getFullName())
                .requesterReputation(request.getRequester().getReputationStars())
                .isAnonymousRequester(request.getIsAnonymousRequester())
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
}
