package com.wellnessmate.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.advisor.agent.ToolConfirmationRegistry;
import org.junit.jupiter.api.Test;

class ToolConfirmationRegistryTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ToolConfirmationRegistry confirmations = new ToolConfirmationRegistry(mapper);

  @Test
  void requiresMatchingTokenForSameDestructiveAction() throws Exception {
    var args = mapper.readTree("""
        {"entry_id":42,"amount":73.5}
        """);

    var first = confirmations.check(7L, "update_tracker_entry", args);

    assertThat(first.required()).isTrue();
    assertThat(first.approved()).isFalse();
    assertThat(first.token()).isNotBlank();

    var wrong = confirmations.check(7L, "update_tracker_entry",
        mapper.readTree("""
            {"entry_id":43,"amount":73.5,"confirmation_token":"%s"}
            """.formatted(first.token())));

    assertThat(wrong.approved()).isFalse();
    assertThat(wrong.message()).contains("does not match");

    var secondRequest = confirmations.check(7L, "update_tracker_entry", args);
    var approved = confirmations.check(7L, "update_tracker_entry",
        mapper.readTree("""
            {"entry_id":42,"amount":73.5,"confirmation_token":"%s"}
            """.formatted(secondRequest.token())));

    assertThat(approved.approved()).isTrue();
    assertThat(approved.required()).isFalse();
  }
}
