package com.wellnessmate.plan.api;

import com.wellnessmate.plan.service.TrainingPlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training-plans")
public class TrainingPlanController {
  private final TrainingPlanService service;

  public TrainingPlanController(TrainingPlanService service) { this.service = service; }

  @GetMapping
  public List<TrainingPlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return service.list(id(jwt));
  }

  @GetMapping("/{id}")
  public TrainingPlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return service.get(id(jwt), id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TrainingPlanResponse create(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody TrainingPlanRequest request) {
    return service.create(id(jwt), request);
  }

  @PutMapping("/{id}")
  public TrainingPlanResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody TrainingPlanRequest request) {
    return service.update(id(jwt), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    service.delete(id(jwt), id);
  }

  @PostMapping("/{id}/check-ins")
  public TrainingPlanResponse checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return service.checkIn(id(jwt), id);
  }

  @PostMapping("/{id}/subscribe")
  public TrainingPlanResponse subscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return service.subscribe(id(jwt), id);
  }

  @DeleteMapping("/{id}/subscribe")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    service.unsubscribe(id(jwt), id);
  }

  @GetMapping("/subscribed")
  public List<TrainingPlanResponse> subscribed(@AuthenticationPrincipal Jwt jwt) {
    return service.subscribed(id(jwt));
  }

  @GetMapping("/{id}/subscribers")
  public List<SubscriberResponse> subscribers(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long id) {
    return service.subscribers(id(jwt), id);
  }

  private Long id(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
