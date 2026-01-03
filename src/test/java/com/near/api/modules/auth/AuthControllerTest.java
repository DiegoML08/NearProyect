package com.near.api.modules.auth;

import com.near.api.modules.auth.dto.request.LoginRequest;
import com.near.api.modules.auth.dto.request.RegisterRequest;
import com.near.api.modules.auth.dto.response.AuthResponse;
import com.near.api.modules.auth.service.AuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuthService authService;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/v1/auth";
    }

    @Test
    @Order(1)
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@near.com");
        request.setPassword("Test1234!");
        request.setFullName("Usuario Test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<RegisterRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/register",
                entity,
                String.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("accessToken"));
    }

    @Test
    @Order(2)
    void shouldLoginWithValidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@near.com");
        request.setPassword("Test1234!");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/login",
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("accessToken"));
    }

    @Test
    @Order(3)
    void shouldCreateAnonymousUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/anonymous",
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("anonymousCode"));
    }

    @Test
    @Order(4)
    void shouldNotLoginWithInvalidPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@near.com");
        request.setPassword("WrongPassword!");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/login",
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}