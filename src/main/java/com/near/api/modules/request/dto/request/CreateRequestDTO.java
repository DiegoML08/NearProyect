package com.near.api.modules.request.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateRequestDTO {

    // === Ubicación ===
    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "Latitud inválida")
    @DecimalMax(value = "90.0", message = "Latitud inválida")
    private Double latitude;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "Longitud inválida")
    @DecimalMax(value = "180.0", message = "Longitud inválida")
    private Double longitude;

    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    private String locationAddress;

    @Size(max = 255, message = "La referencia no puede exceder 255 caracteres")
    private String locationReference;

    @Min(value = 100, message = "El radio mínimo es 100 metros")
    @Max(value = 5000, message = "El radio máximo es 5000 metros")
    private Integer radiusMeters = 500;

    // === Contenido solicitado ===
    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 10, max = 1000, message = "La descripción debe tener entre 10 y 1000 caracteres")
    private String description;

    @NotNull(message = "El tipo de contenido es obligatorio")
    private String contentType; // PHOTO, VIDEO, BOTH

    // === Tiempo ===
    @NotNull(message = "La duración máxima es obligatoria")
    @Min(value = 5, message = "La duración mínima es 5 minutos")
    @Max(value = 60, message = "La duración máxima es 60 minutos")
    private Integer maxDurationMinutes;

    // === Configuración de confianza ===
    private String trustMode = "ALL"; // TRUST o ALL

    // === Recompensa ===
    @NotNull(message = "La recompensa es obligatoria")
    @Min(value = 5, message = "La recompensa mínima es 5 Nears")
    private Integer rewardNears;

    // === Opciones adicionales ===
    private Boolean isAnonymous = false;
}
