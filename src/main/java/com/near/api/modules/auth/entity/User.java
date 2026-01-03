package com.near.api.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous = false;

    @Column(name = "anonymous_code", unique = true, length = 20)
    private String anonymousCode;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider", length = 20)
    private String authProvider; // 'email', 'google', 'apple'

    @Column(name = "auth_provider_id")
    private String authProviderId;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "current_location", columnDefinition = "geography(Point,4326)")
    private Point currentLocation;

    @Column(name = "last_location_update")
    private OffsetDateTime lastLocationUpdate;

    @Column(name = "reputation_stars", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal reputationStars = BigDecimal.ZERO;

    @Column(name = "total_ratings_received")
    @Builder.Default
    private Integer totalRatingsReceived = 0;

    @Column(name = "total_ab_trust_points")
    @Builder.Default
    private Integer totalAbTrustPoints = 0;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_banned")
    @Builder.Default
    private Boolean isBanned = false;

    @Column(name = "ban_reason", columnDefinition = "TEXT")
    private String banReason;

    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(length = 10)
    @Builder.Default
    private String language = "es";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;
}