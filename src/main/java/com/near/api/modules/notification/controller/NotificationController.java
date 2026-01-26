package com.near.api.modules.notification.controller;

import com.near.api.modules.notification.dto.request.RegisterFcmTokenRequest;
import com.near.api.modules.notification.service.NotificationService;
import com.near.api.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Registrar o actualizar el token FCM del dispositivo
     * 
     * POST /api/v1/users/fcm-token
     * Body: { "fcmToken": "...", "deviceId": "...", "deviceType": "android", "deviceName": "Samsung S21" }
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RegisterFcmTokenRequest request
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        
        log.info("Registrando token FCM para usuario {}", userId);
        
        notificationService.registerToken(
                userId,
                request.getFcmToken(),
                request.getDeviceId(),
                request.getDeviceType(),
                request.getDeviceName()
        );

        return ResponseEntity.ok(ApiResponse.success("Token FCM registrado exitosamente", null));
    }

    /**
     * Eliminar un token FCM específico (logout de un dispositivo)
     * 
     * DELETE /api/v1/users/fcm-token
     * Body: { "fcmToken": "..." }
     */
    @DeleteMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> removeFcmToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RegisterFcmTokenRequest request
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        
        log.info("Eliminando token FCM para usuario {}", userId);
        
        notificationService.removeToken(request.getFcmToken());

        return ResponseEntity.ok(ApiResponse.success("Token FCM eliminado exitosamente", null));
    }

    /**
     * Eliminar todos los tokens FCM del usuario (logout completo)
     * 
     * DELETE /api/v1/users/fcm-tokens
     */
    @DeleteMapping("/fcm-tokens")
    public ResponseEntity<ApiResponse<Void>> removeAllFcmTokens(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        
        log.info("Eliminando todos los tokens FCM para usuario {}", userId);
        
        notificationService.removeAllUserTokens(userId);

        return ResponseEntity.ok(ApiResponse.success("Todos los tokens FCM eliminados", null));
    }
}
