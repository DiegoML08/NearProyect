package com.near.api.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_token", nullable = false, length = 500)
    private String deviceToken;

    @Column(name = "device_type", length = 20)
    private String deviceType; // 'ios', 'android'

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;
}
