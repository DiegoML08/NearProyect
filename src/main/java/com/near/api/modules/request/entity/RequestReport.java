package com.near.api.modules.request.entity;

import com.near.api.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "request_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;

    @Column(name = "report_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence_urls", columnDefinition = "TEXT[]")
    private List<String> evidenceUrls;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "action_taken", length = 30)
    @Enumerated(EnumType.STRING)
    private ActionTaken actionTaken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum ReportType {
        INAPPROPRIATE_CONTENT,
        PRIVACY_VIOLATION,
        FRAUD,
        HARASSMENT,
        NO_RESPONSE,
        WRONG_LOCATION,
        LOW_QUALITY,
        OTHER
    }

    public enum ReportStatus {
        PENDING,
        REVIEWING,
        RESOLVED,
        DISMISSED
    }

    public enum ActionTaken {
        NONE,
        WARNING,
        REFUND,
        USER_SUSPENDED,
        USER_BANNED
    }
}
