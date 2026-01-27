package com.near.api.modules.auth.controller;

import com.near.api.modules.auth.dto.request.UpdateLocationRequest;
import com.near.api.modules.auth.dto.response.LocationResponse;
import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.notification.dto.NotificationData;
import com.near.api.modules.notification.service.NotificationService;
import com.near.api.shared.dto.ApiResponse;
import com.near.api.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Actualizar la ubicación del usuario actual
     *
     * PUT /api/v1/users/location
     */
    @PutMapping("/location")
    @Transactional
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateLocationRequest request
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Crear el punto geográfico (IMPORTANTE: PostGIS usa longitud, latitud)
        Point location = geometryFactory.createPoint(
                new Coordinate(request.getLongitude(), request.getLatitude())
        );

        // Actualizar ubicación
        user.setCurrentLocation(location);
        user.setLastLocationUpdate(OffsetDateTime.now());
        userRepository.save(user);

        log.info("📍 Ubicación actualizada para usuario {}: lat={}, lng={}",
                userId, request.getLatitude(), request.getLongitude());

        LocationResponse response = LocationResponse.builder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .updatedAt(user.getLastLocationUpdate())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Ubicación actualizada", response));
    }

    /**
     * Obtener la ubicación actual del usuario
     *
     * GET /api/v1/users/location
     */
    @GetMapping("/location")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        LocationResponse response = null;

        if (user.getCurrentLocation() != null) {
            response = LocationResponse.builder()
                    .latitude(user.getCurrentLocation().getY())
                    .longitude(user.getCurrentLocation().getX())
                    .updatedAt(user.getLastLocationUpdate())
                    .build();
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * ENDPOINT DE PRUEBA: Enviar una notificación de prueba al usuario actual
     *
     * POST /api/v1/users/test-notification
     *
     * Este endpoint es para verificar que FCM funciona correctamente.
     * ELIMINAR EN PRODUCCIÓN o proteger con rol de admin.
     */
    @PostMapping("/test-notification")
    public ResponseEntity<ApiResponse<String>> sendTestNotification(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        log.info("🔔 Enviando notificación de prueba al usuario {}", userId);

        // Crear notificación de prueba
        NotificationData notification = NotificationData.builder()
                .type(NotificationData.NotificationType.DELIVERY_CONFIRMED) // Usamos un tipo existente
                .title("🧪 Notificación de Prueba")
                .body("¡Las notificaciones están funcionando correctamente!")
                .data(java.util.Map.of(
                        "type", "TEST",
                        "requestId", UUID.randomUUID().toString(),
                        "earnedNears", "100",
                        "timestamp", OffsetDateTime.now().toString()
                ))
                .build();

        try {
            notificationService.sendToUser(userId, notification);
            log.info("✅ Notificación de prueba enviada al usuario {}", userId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Notificación de prueba enviada. Revisa tu dispositivo.",
                    "OK"
            ));
        } catch (Exception e) {
            log.error("❌ Error enviando notificación de prueba: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error enviando notificación: " + e.getMessage()));
        }
    }

    /**
     * ENDPOINT DE PRUEBA: Enviar notificación a un token específico
     *
     * POST /api/v1/users/test-notification-token?token=...
     */
    @PostMapping("/test-notification-token")
    public ResponseEntity<ApiResponse<String>> sendTestNotificationToToken(
            @RequestParam String token
    ) {
        log.info("🔔 Enviando notificación de prueba al token: {}...",
                token.substring(0, Math.min(20, token.length())));

        NotificationData notification = NotificationData.builder()
                .type(NotificationData.NotificationType.DELIVERY_CONFIRMED)
                .title("🧪 Test Directo a Token")
                .body("Si ves esto, FCM funciona correctamente!")
                .data(java.util.Map.of(
                        "type", "TEST",
                        "requestId", UUID.randomUUID().toString(),
                        "earnedNears", "50"
                ))
                .build();

        try {
            notificationService.sendToToken(token, notification);
            log.info("✅ Notificación enviada al token");
            return ResponseEntity.ok(ApiResponse.success("Notificación enviada al token", "OK"));
        } catch (Exception e) {
            log.error("❌ Error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error: " + e.getMessage()));
        }
    }
}
