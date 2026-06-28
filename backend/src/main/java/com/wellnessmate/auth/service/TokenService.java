package com.wellnessmate.auth.service;

import com.wellnessmate.auth.domain.UserAccount;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Creates signed access tokens without exposing signing material. @author TODO(team member) */
@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final Duration ttl;

  public TokenService(JwtEncoder encoder, @Value("${security.jwt.ttl:PT2H}") Duration ttl) {
    this.encoder = encoder;
    this.ttl = ttl;
  }

  public String createAccessToken(UserAccount user) {
    Instant issuedAt = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("wellness-mate")
        .issuedAt(issuedAt)
        .expiresAt(issuedAt.plus(ttl))
        .subject(user.getId().toString())
        .claim("username", user.getUsername())
        .claim("role", user.getRole().name())
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long expiresInSeconds() {
    return ttl.toSeconds();
  }
}
