package com.near.api.modules.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFcmTokenRequest {

    @NotBlank(message = "El token FCM es requerido")
    @Size(max = 500, message = "El token FCM no puede exceder 500 caracteres")
    private String fcmToken;

    @Size(max = 255, message = "El deviceId no puede exceder 255 caracteres")
    private String deviceId;

    @Size(max = 20, message = "El deviceType no puede exceder 20 caracteres")
    private String deviceType; // 'android', 'ios', 'web'

    @Size(max = 100, message = "El deviceName no puede exceder 100 caracteres")
    private String deviceName;
}
