package com.near.api.modules.request.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RateRequestDTO {

    @NotNull(message = "La calificación es obligatoria")
    @DecimalMin(value = "1.0", message = "La calificación mínima es 1")
    @DecimalMax(value = "5.0", message = "La calificación máxima es 5")
    private BigDecimal rating;

    @Size(max = 500, message = "La reseña no puede exceder 500 caracteres")
    private String review;
}
