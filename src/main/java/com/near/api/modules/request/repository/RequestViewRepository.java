package com.near.api.modules.request.repository;

import com.near.api.modules.request.entity.RequestView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestViewRepository extends JpaRepository<RequestView, UUID> {

    Optional<RequestView> findByRequestIdAndUserId(UUID requestId, UUID userId);

    boolean existsByRequestIdAndUserId(UUID requestId, UUID userId);

    long countByRequestId(UUID requestId);
}
