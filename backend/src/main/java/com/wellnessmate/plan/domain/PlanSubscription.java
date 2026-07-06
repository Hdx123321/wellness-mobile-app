package com.wellnessmate.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "plan_subscriptions")
public class PlanSubscription {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long planId;
  @Column(nullable = false) private Long userId;
  @Column(nullable = false) private Instant subscribedAt;

  protected PlanSubscription() {}

  public PlanSubscription(Long planId, Long userId) {
    this.planId = planId;
    this.userId = userId;
    this.subscribedAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getPlanId() { return planId; }
  public Long getUserId() { return userId; }
  public Instant getSubscribedAt() { return subscribedAt; }
}
