package com.near.api.modules.request.dto.response;

import com.near.api.modules.request.entity.Request.ContentType;
import com.near.api.modules.request.entity.Request.TrustMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NearbyRequestResponse {

    private UUID id;
    
    // Ubicación
    private Double latitude;
    private Double longitude;
    private String locationAddress;
    private Integer radiusMeters;
    private Double distanceMeters;
    
    // Contenido resumido
    private String descriptionPreview; // Primeros 100 caracteres
    private ContentType contentType;
    
    // Tiempo
    private OffsetDateTime expiresAt;
    private Long remainingSeconds;
    
    // Recompensa
    private Integer rewardNears;
    
    // Confianza
    private TrustMode trustMode;
    private Boolean isTrustModeActive;
    
    // Requester básico
    private String requesterDisplayName;
    private BigDecimal requesterReputation;
    private Boolean isAnonymousRequester;
    
    private OffsetDateTime createdAt;
}
