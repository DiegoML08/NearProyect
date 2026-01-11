package com.near.api.modules.request.repository;

import com.near.api.modules.request.entity.RequestReport;
import com.near.api.modules.request.entity.RequestReport.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestReportRepository extends JpaRepository<RequestReport, UUID> {

    Page<RequestReport> findByStatusInOrderByCreatedAtDesc(List<ReportStatus> statuses, Pageable pageable);

    List<RequestReport> findByRequestId(UUID requestId);

    List<RequestReport> findByReportedUserIdAndStatus(UUID reportedUserId, ReportStatus status);

    boolean existsByRequestIdAndReporterId(UUID requestId, UUID reporterId);
}
