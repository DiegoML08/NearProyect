package com.near.api.modules.request.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class DeliverContentDTO {

    @NotEmpty(message = "Debe incluir al menos un archivo multimedia")
    private List<MediaItem> mediaItems;

    @Data
    public static class MediaItem {
        @NotNull(message = "El tipo de media es obligatorio")
        private String mediaType; // PHOTO o VIDEO

        @NotNull(message = "La URL es obligatoria")
        private String url;

        private String thumbnailUrl;
        private String publicId; // ID de Cloudinary

        private Integer fileSizeBytes;
        private Integer width;
        private Integer height;
        private Integer durationSeconds; // Para videos

        // Geolocalización de captura
        private Double captureLatitude;
        private Double captureLongitude;
        private OffsetDateTime captureTimestamp;

        private Map<String, Object> deviceInfo;
    }
}
