package com.near.api.modules.notification.entity;

import com.near.api.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad para almacenar los tokens FCM de los dispositivos de los usuarios.
 * Un usuario puede tener múltiples tokens (múltiples dispositivos).
 */
@Entity
@Table(name = "fcm_tokens", 
       indexes = {
           @Index(name = "idx_fcm_tokens_user_id", columnList = "user_id"),
           @Index(name = "idx_fcm_tokens_token", columnList = "token")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_fcm_token", columnNames = {"token"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_type", length = 20)
    private String deviceType; // 'android', 'ios', 'web'

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;
}
