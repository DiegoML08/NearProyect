package com.near.api.infrastructure.security;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                user.getIsActive(),
                true,
                true,
                !user.getIsBanned(),
                Collections.emptyList()
        );
    }

    public UserDetails loadUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + userId));

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                user.getIsActive(),
                true,
                true,
                !user.getIsBanned(),
                Collections.emptyList()
        );
    }
}
