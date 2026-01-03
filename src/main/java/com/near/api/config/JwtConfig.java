package com.near.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    private String secret;
    private Long expiration = 86400000L; // 24 horas por defecto
}
