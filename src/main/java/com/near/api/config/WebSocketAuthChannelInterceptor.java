package com.near.api.config;

import com.near.api.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Solo autenticar en CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);

            if (token != null) {
                try {
                    // Validar token y extraer userId
                    if (jwtTokenProvider.validateToken(token)) {
                        UUID userId = jwtTokenProvider.getUserIdFromToken(token);

                        // Crear autenticación
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userId.toString(),
                                        null,
                                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                                );

                        // Establecer el usuario en el accessor
                        accessor.setUser(authentication);

                        // También establecer en el SecurityContext
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("WebSocket conectado para usuario: {}", userId);
                    } else {
                        log.warn("Token JWT inválido en conexión WebSocket");
                        throw new IllegalArgumentException("Token inválido");
                    }
                } catch (Exception e) {
                    log.error("Error autenticando conexión WebSocket: {}", e.getMessage());
                    throw new IllegalArgumentException("Error de autenticación: " + e.getMessage());
                }
            } else {
                log.warn("No se encontró token en conexión WebSocket");
                throw new IllegalArgumentException("Token no proporcionado");
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // Intentar obtener del header Authorization
        List<String> authHeaders = accessor.getNativeHeader(AUTHORIZATION_HEADER);

        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length());
            }
            return authHeader;
        }

        // Intentar obtener del header "token" (alternativa para algunos clientes)
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // Intentar obtener de header X-Auth-Token (para SockJS)
        String query = accessor.getFirstNativeHeader("X-Auth-Token");
        if (query != null) {
            return query;
        }

        return null;
    }
}
