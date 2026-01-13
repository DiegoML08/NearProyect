package com.near.api.modules.chat.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMediaMessageRequest {

    @NotBlank(message = "La URL del archivo es obligatoria")
    private String url;

    @NotBlank(message = "El public_id es obligatorio")
    private String publicId;

    private String thumbnailUrl;

    private String blurredUrl;

    @NotNull(message = "El tipo de media es obligatorio")
    private MediaType mediaType;

    private Integer sizeBytes;

    private Integer width;

    private Integer height;

    @Max(value = 5, message = "La duración máxima del video es 5 segundos")
    private Integer durationSeconds;

    @Size(max = 200, message = "La descripción no puede exceder 200 caracteres")
    private String caption;

    @Min(value = 0, message = "El precio no puede ser negativo")
    @Builder.Default
    private Integer priceNears = 0;

    @Min(value = 0, message = "La propina no puede ser negativa")
    @Max(value = 10, message = "La propina máxima es 10 Nears")
    @Builder.Default
    private Integer tipAmount = 0;

    public enum MediaType {
        IMAGE,
        VIDEO
    }

    public boolean isPaid() {
        return priceNears != null && priceNears > 0;
    }
}