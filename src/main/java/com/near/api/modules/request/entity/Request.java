package com.near.api.modules.request.entity;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.wallet.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // === Usuarios involucrados ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responder_id")
    private User responder;

    // === Ubicación ===
    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    @Column(name = "location_address", length = 500)
    private String locationAddress;

    @Column(name = "location_reference")
    private String locationReference;

    @Column(name = "radius_meters")
    @Builder.Default
    private Integer radiusMeters = 500;

    // === Contenido solicitado ===
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "content_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private ContentType contentType;

    // === Configuración de tiempo ===
    @Column(name = "max_duration_minutes", nullable = false)
    private Integer maxDurationMinutes;

    @Column(name = "trust_mode_duration_seconds")
    @Builder.Default
    private Integer trustModeDurationSeconds = 60;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    // === Configuración de confianza ===
    @Column(name = "trust_mode", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TrustMode trustMode = TrustMode.ALL;

    @Column(name = "min_reputation_stars", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal minReputationStars = BigDecimal.ZERO;

    @Column(name = "trust_mode_expires_at")
    private OffsetDateTime trustModeExpiresAt;

    // === Recompensa ===
    @Column(name = "reward_nears", nullable = false)
    private Integer rewardNears;

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal commissionPercentage = new BigDecimal("15.00");

    @Column(name = "commission_amount")
    @Builder.Default
    private Integer commissionAmount = 0;

    @Column(name = "final_reward")
    @Builder.Default
    private Integer finalReward = 0;

    // === Estado ===
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    // === Timestamps del ciclo de vida ===
    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    // === Cancelación ===
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    // === Calificaciones ===
    @Column(name = "requester_rating", precision = 2, scale = 1)
    private BigDecimal requesterRating;

    @Column(name = "requester_review", columnDefinition = "TEXT")
    private String requesterReview;

    @Column(name = "requester_rated_at")
    private OffsetDateTime requesterRatedAt;

    @Column(name = "responder_rating", precision = 2, scale = 1)
    private BigDecimal responderRating;

    @Column(name = "responder_review", columnDefinition = "TEXT")
    private String responderReview;

    @Column(name = "responder_rated_at")
    private OffsetDateTime responderRatedAt;

    // === Transacciones ===
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private Transaction paymentTransaction;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "earning_transaction_id")
    private Transaction earningTransaction;

    // === Metadata ===
    @Column(name = "is_anonymous_requester")
    @Builder.Default
    private Boolean isAnonymousRequester = false;

    @Column(name = "is_anonymous_responder")
    @Builder.Default
    private Boolean isAnonymousResponder = false;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    // === Relaciones ===
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RequestMedia> media = new ArrayList<>();

    // === Auditoría ===
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // === Enums ===
    public enum ContentType {
        PHOTO, VIDEO, BOTH
    }

    public enum TrustMode {
        TRUST, ALL
    }

    public enum RequestStatus {
        PENDING,      // Esperando que alguien acepte
        ACCEPTED,     // Alguien aceptó
        IN_PROGRESS,  // Responder está tomando foto/video
        DELIVERED,    // Contenido entregado, esperando confirmación
        COMPLETED,    // Requester confirmó, transacción exitosa
        EXPIRED,      // Nadie aceptó a tiempo
        CANCELLED,    // Requester canceló antes de aceptar
        DISPUTED      // Hay una disputa abierta
    }

    // === Métodos de negocio ===
    public void calculateCommission() {
        BigDecimal reward = BigDecimal.valueOf(this.rewardNears);
        BigDecimal commission = reward.multiply(this.commissionPercentage)
                .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
        this.commissionAmount = commission.intValue();
        this.finalReward = this.rewardNears - this.commissionAmount;
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isTrustModeActive() {
        if (this.trustMode != TrustMode.TRUST) return false;
        if (this.trustModeExpiresAt == null) return false;
        return OffsetDateTime.now().isBefore(this.trustModeExpiresAt);
    }
}
