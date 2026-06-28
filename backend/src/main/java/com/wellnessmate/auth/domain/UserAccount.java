package com.wellnessmate.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Authenticated WellnessMate account.
 *
 * @author TODO(team member)
 */
@Entity
@Table(name = "users")
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Column(length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(nullable = false)
  private boolean onboardingCompleted;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected UserAccount() {
  }

  public UserAccount(String username, String email, String passwordHash, String displayName) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.role = UserRole.CLIENT;
    this.onboardingCompleted = false;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /** Marks the first-login questionnaire as complete. */
  public void completeOnboarding() {
    onboardingCompleted = true;
    updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getDisplayName() { return displayName; }
  public UserRole getRole() { return role; }
  public boolean isOnboardingCompleted() { return onboardingCompleted; }
}
