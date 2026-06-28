package com.wellnessmate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.common.api.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless HTTP security boundary for mobile APIs. @author TODO(team member) */
@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/api/auth/register", "/api/auth/login").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth
            .jwt(Customizer.withDefaults())
            .authenticationEntryPoint((request, response, error) -> writeError(
                objectMapper, response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required",
                request.getRequestURI())))
        .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, error) -> writeError(
            objectMapper, response, 403, "ACCESS_DENIED", "Access is denied", request.getRequestURI())));
    return http.build();
  }

  private void writeError(ObjectMapper mapper, HttpServletResponse response, int status,
                          String code, String message, String path) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), ApiError.of(status, code, message, path));
  }
}
