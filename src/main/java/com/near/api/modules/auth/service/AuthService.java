package com.near.api.modules.auth.service;

import com.near.api.modules.auth.dto.request.AnonymousLoginRequest;
import com.near.api.modules.auth.dto.request.LoginRequest;
import com.near.api.modules.auth.dto.request.RefreshTokenRequest;
import com.near.api.modules.auth.dto.request.RegisterRequest;
import com.near.api.modules.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginAnonymous(AnonymousLoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String userId, String deviceToken);
}
