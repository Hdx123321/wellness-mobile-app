package com.wellnessmate.advisor.api;

import com.wellnessmate.advisor.service.AiAdvisorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai-advisor")
public class AiAdvisorController {
  private final AiAdvisorService advisor;

  public AiAdvisorController(AiAdvisorService advisor) { this.advisor = advisor; }

  // ── Sessions ──

  @GetMapping("/sessions")
  public List<AiChatSessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
    return advisor.listSessions(Long.parseLong(jwt.getSubject()));
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public AiChatSessionResponse createSession(@AuthenticationPrincipal Jwt jwt) {
    return advisor.createSession(Long.parseLong(jwt.getSubject()));
  }

  @DeleteMapping("/sessions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSession(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    advisor.deleteSession(Long.parseLong(jwt.getSubject()), id);
  }

  @PostMapping("/sessions/{id}/rename")
  public AiChatSessionResponse renameSession(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long id,
                                              @Valid @RequestBody RenameSessionRequest request) {
    return advisor.renameSession(Long.parseLong(jwt.getSubject()), id, request.title());
  }

  // ── Messages ──

  @GetMapping("/sessions/{sessionId}/messages")
  public List<AiAdvisorMessageResponse> sessionMessages(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable Long sessionId) {
    return advisor.messagesForSession(Long.parseLong(jwt.getSubject()), sessionId);
  }

  @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter sendStream(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable Long sessionId,
                                @Valid @RequestBody AiAdvisorMessageRequest request) {
    return advisor.sendStream(Long.parseLong(jwt.getSubject()), sessionId, request.content());
  }
}
