package com.near.api.modules.auth.controller;

import com.near.api.modules.auth.dto.request.AnonymousLoginRequest;
import com.near.api.modules.auth.dto.request.LoginRequest;
import com.near.api.modules.auth.dto.request.RefreshTokenRequest;
import com.near.api.modules.auth.dto.request.RegisterRequest;
import com.near.api.modules.auth.dto.response.AuthResponse;
import com.near.api.modules.auth.service.AuthService;
import com.near.api.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Usuario registrado exitosamente", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login exitoso", response));
    }

    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<AuthResponse>> loginAnonymous(@RequestBody AnonymousLoginRequest request) {
        AuthResponse response = authService.loginAnonymous(request);
        return ResponseEntity.ok(ApiResponse.success("Sesión anónima iniciada", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token renovado", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String deviceToken) {
        authService.logout(userDetails.getUsername(), deviceToken);
        return ResponseEntity.ok(ApiResponse.success("Sesión cerrada", null));
    }
}
