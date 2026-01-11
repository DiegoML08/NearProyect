package com.near.api.modules.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReportRequestDTO {

    @NotNull(message = "El tipo de reporte es obligatorio")
    private String reportType; // INAPPROPRIATE_CONTENT, PRIVACY_VIOLATION, etc.

    @NotNull(message = "El ID del usuario reportado es obligatorio")
    private UUID reportedUserId;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 20, max = 1000, message = "La descripción debe tener entre 20 y 1000 caracteres")
    private String description;

    private List<String> evidenceUrls;
}
