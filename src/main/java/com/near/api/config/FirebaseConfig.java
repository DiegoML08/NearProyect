package com.near.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.service-account.path:#{null}}")
    private String serviceAccountPath;

    @Value("${firebase.service-account.base64:#{null}}")
    private String serviceAccountBase64;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(getCredentials())
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("✅ Firebase inicializado correctamente");
            }
        } catch (Exception e) {
            log.error("❌ Error inicializando Firebase: {}", e.getMessage());
            // No lanzar excepción para no romper el inicio de la aplicación
            // Las notificaciones simplemente no funcionarán
        }
    }

    private GoogleCredentials getCredentials() throws IOException {
        // Opción 1: Desde variable de entorno Base64 (producción)
        if (StringUtils.hasText(serviceAccountBase64)) {
            log.info("📱 Cargando credenciales de Firebase desde Base64");
            byte[] decodedBytes = Base64.getDecoder().decode(serviceAccountBase64);
            InputStream stream = new ByteArrayInputStream(decodedBytes);
            return GoogleCredentials.fromStream(stream);
        }

        // Opción 2: Desde archivo en resources (desarrollo)
        if (StringUtils.hasText(serviceAccountPath)) {
            log.info("📱 Cargando credenciales de Firebase desde archivo: {}", serviceAccountPath);
            ClassPathResource resource = new ClassPathResource(serviceAccountPath);
            return GoogleCredentials.fromStream(resource.getInputStream());
        }

        // Opción 3: Intentar cargar desde la ruta por defecto
        log.info("📱 Intentando cargar credenciales de Firebase desde ruta por defecto");
        ClassPathResource defaultResource = new ClassPathResource("firebase/service-account.json");
        if (defaultResource.exists()) {
            return GoogleCredentials.fromStream(defaultResource.getInputStream());
        }

        throw new IllegalStateException(
                "No se encontraron credenciales de Firebase. " +
                "Configure FIREBASE_SERVICE_ACCOUNT_BASE64 o firebase.service-account.path"
        );
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("⚠️ Firebase no está inicializado. Las notificaciones push no funcionarán.");
            return null;
        }
        return FirebaseMessaging.getInstance();
    }
}
