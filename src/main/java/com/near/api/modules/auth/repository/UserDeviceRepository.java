package com.near.api.modules.auth.repository;

import com.near.api.modules.auth.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    List<UserDevice> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<UserDevice> findByDeviceTokenAndUserId(String deviceToken, UUID userId);

    void deleteByUserIdAndDeviceToken(UUID userId, String deviceToken);
}
