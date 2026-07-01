package com.wellnessmate.advisor.api;

import com.wellnessmate.advisor.service.AiAdvisorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai-advisor/messages")
public class AiAdvisorController {
  private final AiAdvisorService advisor;

  public AiAdvisorController(AiAdvisorService advisor) { this.advisor = advisor; }

  @GetMapping
  public List<AiAdvisorMessageResponse> messages(@AuthenticationPrincipal Jwt jwt) {
    return advisor.messages(Long.parseLong(jwt.getSubject()));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AiAdvisorMessageResponse send(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody AiAdvisorMessageRequest request) {
    return advisor.send(Long.parseLong(jwt.getSubject()), request.content());
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter sendStream(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody AiAdvisorMessageRequest request) {
    return advisor.sendStream(Long.parseLong(jwt.getSubject()), request.content());
  }
}
