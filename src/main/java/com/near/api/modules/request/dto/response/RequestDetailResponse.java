package com.near.api.modules.request.dto.response;

import com.near.api.modules.request.entity.Request.ContentType;
import com.near.api.modules.request.entity.Request.RequestStatus;
import com.near.api.modules.request.entity.Request.TrustMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RequestDetailResponse {

    private UUID id;
    
    // Ubicación
    private Double latitude;
    private Double longitude;
    private String locationAddress;
    private String locationReference;
    private Integer radiusMeters;
    
    // Contenido
    private String description;
    private ContentType contentType;
    
    // Tiempo
    private Integer maxDurationMinutes;
    private OffsetDateTime expiresAt;
    private Long remainingSeconds;
    
    // Confianza
    private TrustMode trustMode;
    private Boolean isTrustModeActive;
    private OffsetDateTime trustModeExpiresAt;
    
    // Recompensa
    private Integer rewardNears;
    private Integer finalReward;
    private Integer commissionAmount;
    private BigDecimal commissionPercentage;
    
    // Estado
    private RequestStatus status;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime cancelledAt;
    private String cancellationReason;
    
    // Usuarios
    private UserInfo requester;
    private UserInfo responder;
    
    // Calificaciones
    private RatingInfo requesterRating;
    private RatingInfo responderRating;
    
    // Media entregada
    private List<RequestMediaResponse> media;
    
    // Metadata
    private Integer viewCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @Builder
    public static class UserInfo {
        private UUID id;
        private String fullName;
        private String displayName;
        private String profilePhotoUrl;
        private BigDecimal reputationStars;
        private Integer totalRatingsReceived;
        private Boolean isAnonymous;
        private Boolean isVerified;
    }

    @Data
    @Builder
    public static class RatingInfo {
        private BigDecimal rating;
        private String review;
        private OffsetDateTime ratedAt;
    }
}
