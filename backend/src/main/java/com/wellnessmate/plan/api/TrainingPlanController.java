package com.wellnessmate.plan.api;

import com.wellnessmate.plan.service.TrainingPlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training-plans")
public class TrainingPlanController {
  private final TrainingPlanService service;
  public TrainingPlanController(TrainingPlanService service) { this.service = service; }
  @GetMapping public List<TrainingPlanResponse> list(@AuthenticationPrincipal Jwt jwt) { return service.list(id(jwt)); }
  @GetMapping("/{id}") public TrainingPlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return service.get(id(jwt), id); }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public TrainingPlanResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrainingPlanRequest request) {
    return service.create(id(jwt), request);
  }
  @PostMapping("/{id}/check-ins")
  public TrainingPlanResponse checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return service.checkIn(id(jwt), id); }
  private Long id(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
