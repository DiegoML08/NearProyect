package com.near.api.modules.request.dto.response;

import com.near.api.modules.request.entity.RequestMedia.MediaType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RequestMediaResponse {

    private UUID id;
    private MediaType mediaType;
    private String url;
    private String thumbnailUrl;
    private Integer fileSizeBytes;
    private Integer width;
    private Integer height;
    private Integer durationSeconds;
    private Boolean isVerified;
    private OffsetDateTime createdAt;
}
