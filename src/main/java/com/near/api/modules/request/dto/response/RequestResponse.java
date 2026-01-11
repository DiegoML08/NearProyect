package com.near.api.modules.request.dto.response;

import com.near.api.modules.request.entity.Request.ContentType;
import com.near.api.modules.request.entity.Request.RequestStatus;
import com.near.api.modules.request.entity.Request.TrustMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RequestResponse {

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
    
    // Recompensa
    private Integer rewardNears;
    private Integer finalReward;
    private BigDecimal commissionPercentage;
    
    // Estado
    private RequestStatus status;
    
    // Usuarios (básico)
    private RequesterInfo requester;
    private ResponderInfo responder;
    
    // Metadata
    private Integer viewCount;
    private Double distanceMeters;
    private OffsetDateTime createdAt;

    @Data
    @Builder
    public static class RequesterInfo {
        private UUID id;
        private String displayName;
        private String profilePhotoUrl;
        private BigDecimal reputationStars;
        private Boolean isAnonymous;
    }

    @Data
    @Builder
    public static class ResponderInfo {
        private UUID id;
        private String displayName;
        private String profilePhotoUrl;
        private BigDecimal reputationStars;
        private Boolean isAnonymous;
    }
}
