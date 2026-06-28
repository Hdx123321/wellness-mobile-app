package com.wellnessmate.auth.service;

import com.wellnessmate.auth.api.AuthResponse;
import com.wellnessmate.auth.api.LoginRequest;
import com.wellnessmate.auth.api.RegisterRequest;
import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.repository.UserAccountRepository;
import com.wellnessmate.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and credential authentication use cases. @author TODO(team member) */
@Service
public class AuthService {
  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokens;

  public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (users.existsByUsernameIgnoreCase(request.username())) {
      throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username is already registered");
    }
    if (users.existsByEmailIgnoreCase(request.email())) {
      throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
    }
    UserAccount user = users.save(new UserAccount(
        request.username().trim(), request.email().trim().toLowerCase(),
        passwordEncoder.encode(request.password()), normalized(request.displayName())));
    return response(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String identifier = request.identifier().trim();
    UserAccount user = users.findByUsernameIgnoreCase(identifier)
        .or(() -> users.findByEmailIgnoreCase(identifier))
        .orElseThrow(this::invalidCredentials);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }
    return response(user);
  }

  private AuthResponse response(UserAccount user) {
    return AuthResponse.from(user, tokens.createAccessToken(user), tokens.expiresInSeconds());
  }

  private ApiException invalidCredentials() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
  }

  private String normalized(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
