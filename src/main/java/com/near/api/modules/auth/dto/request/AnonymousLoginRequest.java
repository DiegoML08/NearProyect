package com.near.api.modules.auth.dto.request;

import lombok.Data;

@Data
public class AnonymousLoginRequest {

    private String existingAnonymousCode; // Si ya tiene código anónimo previo
    
    private String deviceToken;
    private String deviceType;
    private String deviceModel;
    private String appVersion;
}
