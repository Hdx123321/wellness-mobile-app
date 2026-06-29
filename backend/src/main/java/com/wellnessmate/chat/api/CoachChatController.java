package com.wellnessmate.chat.api;

import com.wellnessmate.chat.service.CoachChatService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coach-chat")
public class CoachChatController {
  private final CoachChatService chat;

  public CoachChatController(CoachChatService chat) { this.chat = chat; }

  @GetMapping("/conversations")
  public List<CoachConversationResponse> conversations(@AuthenticationPrincipal Jwt jwt) {
    return chat.conversations(userId(jwt));
  }

  @GetMapping("/conversations/{id}/messages")
  public List<CoachMessageResponse> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                              @RequestParam(defaultValue = "0") Long afterId) {
    return chat.messages(userId(jwt), id, afterId);
  }

  @PostMapping("/conversations/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public CoachMessageResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody CoachMessageRequest request) {
    return chat.send(userId(jwt), id, request.content());
  }

  private Long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
