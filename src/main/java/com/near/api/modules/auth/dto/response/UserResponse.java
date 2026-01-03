package com.near.api.modules.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID id;
    private Boolean isAnonymous;
    private String anonymousCode;
    private String email;
    private String fullName;
    private String profilePhotoUrl;
    private String phoneNumber;
    private BigDecimal reputationStars;
    private Integer totalRatingsReceived;
    private Boolean isVerified;
    private String language;
}
