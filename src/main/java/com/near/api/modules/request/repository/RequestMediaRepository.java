package com.near.api.modules.request.repository;

import com.near.api.modules.request.entity.RequestMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestMediaRepository extends JpaRepository<RequestMedia, UUID> {

    List<RequestMedia> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    void deleteByRequestId(UUID requestId);
}
