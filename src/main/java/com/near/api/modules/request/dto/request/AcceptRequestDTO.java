package com.near.api.modules.request.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AcceptRequestDTO {

    @NotNull(message = "La latitud actual es obligatoria")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double currentLatitude;

    @NotNull(message = "La longitud actual es obligatoria")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double currentLongitude;

    private Boolean acceptAnonymously = false;
}
