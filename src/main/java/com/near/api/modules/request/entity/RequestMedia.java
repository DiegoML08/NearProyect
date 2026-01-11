package com.near.api.modules.request.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "request_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(name = "media_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "public_id")
    private String publicId; // ID para eliminar de Cloudinary

    @Column(name = "file_size_bytes")
    private Integer fileSizeBytes;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "capture_location", columnDefinition = "geography(Point,4326)")
    private Point captureLocation;

    @Column(name = "capture_timestamp")
    private OffsetDateTime captureTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_info", columnDefinition = "jsonb")
    private Map<String, Object> deviceInfo;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public enum MediaType {
        PHOTO, VIDEO
    }
}
