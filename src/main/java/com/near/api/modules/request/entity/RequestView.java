package com.near.api.modules.request.entity;

import com.near.api.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_views", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"request_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_location", columnDefinition = "geography(Point,4326)")
    private Point userLocation;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "was_trust_eligible")
    @Builder.Default
    private Boolean wasTrustEligible = false;

    @CreationTimestamp
    @Column(name = "viewed_at", updatable = false)
    private OffsetDateTime viewedAt;
}
