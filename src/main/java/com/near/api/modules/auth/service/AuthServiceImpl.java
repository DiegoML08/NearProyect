package com.near.api.modules.auth.service;

import com.near.api.infrastructure.security.JwtTokenProvider;
import com.near.api.modules.auth.dto.request.AnonymousLoginRequest;
import com.near.api.modules.auth.dto.request.LoginRequest;
import com.near.api.modules.auth.dto.request.RefreshTokenRequest;
import com.near.api.modules.auth.dto.request.RegisterRequest;
import com.near.api.modules.auth.dto.response.AuthResponse;
import com.near.api.modules.auth.dto.response.UserResponse;
import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.entity.UserDevice;
import com.near.api.modules.auth.repository.UserDeviceRepository;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.wallet.service.WalletService;
import com.near.api.shared.exception.BadRequestException;
import com.near.api.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final WalletService walletService;


    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Verificar si el email ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }

        // Crear usuario
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .authProvider("email")
                .isAnonymous(false)
                .build();

        user = userRepository.save(user);

        //Crear wallet para el usuario
        walletService.getOrCreateWallet(user.getId());


        // Registrar dispositivo si viene el token
        if (StringUtils.hasText(request.getDeviceToken())) {
            saveUserDevice(user, request.getDeviceToken(), request.getDeviceType(),
                    request.getDeviceModel(), request.getAppVersion());
        }

        // Actualizar último login
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Autenticar con Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Obtener usuario
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Credenciales inválidas"));

        // Verificar si está baneado
        if (user.getIsBanned()) {
            throw new UnauthorizedException("Tu cuenta ha sido suspendida: " + user.getBanReason());
        }

        // Registrar dispositivo
        if (StringUtils.hasText(request.getDeviceToken())) {
            saveUserDevice(user, request.getDeviceToken(), request.getDeviceType(),
                    request.getDeviceModel(), request.getAppVersion());
        }

        // Actualizar último login
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse loginAnonymous(AnonymousLoginRequest request) {

        User user;

        // Si tiene código anónimo previo, intentar recuperarlo
        if (StringUtils.hasText(request.getExistingAnonymousCode())) {
            user = userRepository.findByAnonymousCode(request.getExistingAnonymousCode())
                    .orElseGet(() -> createAnonymousUser());
        } else {
            user = createAnonymousUser();
        }

        //Crear wallet para el usuario
        walletService.getOrCreateWallet(user.getId());
        // Registrar dispositivo
        if (StringUtils.hasText(request.getDeviceToken())) {
            saveUserDevice(user, request.getDeviceToken(), request.getDeviceType(),
                    request.getDeviceModel(), request.getAppVersion());
        }

        // Actualizar último login
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Refresh token inválido o expirado");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        if (user.getIsBanned()) {
            throw new UnauthorizedException("Tu cuenta ha sido suspendida");
        }

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(String userId, String deviceToken) {
        if (StringUtils.hasText(deviceToken)) {
            userDeviceRepository.deleteByUserIdAndDeviceToken(UUID.fromString(userId), deviceToken);
        }
    }

    // === Métodos privados auxiliares ===

    private User createAnonymousUser() {
        String anonymousCode = generateAnonymousCode();

        // Asegurar que el código sea único
        while (userRepository.existsByAnonymousCode(anonymousCode)) {
            anonymousCode = generateAnonymousCode();
        }

        User user = User.builder()
                .isAnonymous(true)
                .anonymousCode(anonymousCode)
                .build();

        return userRepository.save(user);
    }

    private String generateAnonymousCode() {
        // Formato: 9 dígitos basados en timestamp + random
        long timestamp = System.currentTimeMillis() % 1000000000;
        int random = (int) (Math.random() * 1000);
        return String.valueOf(timestamp + random);
    }

    private void saveUserDevice(User user, String deviceToken, String deviceType,
                                String deviceModel, String appVersion) {
        // Buscar si ya existe este dispositivo para el usuario
        userDeviceRepository.findByDeviceTokenAndUserId(deviceToken, user.getId())
                .ifPresentOrElse(
                        device -> {
                            device.setLastUsedAt(OffsetDateTime.now());
                            device.setIsActive(true);
                            userDeviceRepository.save(device);
                        },
                        () -> {
                            UserDevice newDevice = UserDevice.builder()
                                    .user(user)
                                    .deviceToken(deviceToken)
                                    .deviceType(deviceType)
                                    .deviceModel(deviceModel)
                                    .appVersion(appVersion)
                                    .lastUsedAt(OffsetDateTime.now())
                                    .build();
                            userDeviceRepository.save(newDevice);
                        }
                );
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getIsAnonymous());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .isAnonymous(user.getIsAnonymous())
                .anonymousCode(user.getAnonymousCode())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .phoneNumber(user.getPhoneNumber())
                .reputationStars(user.getReputationStars())
                .totalRatingsReceived(user.getTotalRatingsReceived())
                .isVerified(user.getIsVerified())
                .language(user.getLanguage())
                .build();
    }
}
