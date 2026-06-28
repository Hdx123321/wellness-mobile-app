package com.wellnessmate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that Flyway, JPA, and the Spring application context start together.
 *
 * @author TODO(team member)
 */
@SpringBootTest
@AutoConfigureMockMvc
class WellnessBackendApplicationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private MockMvc mockMvc;

  /**
   * Confirms the initial application context starts successfully.
   *
   * @author TODO(team member)
   */
  @Test
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }

  /**
   * Confirms Android can use the public readiness endpoint.
   *
   * @throws Exception when the mock HTTP exchange cannot be performed
   * @author TODO(team member)
   */
  @Test
  void healthEndpointIsAvailable() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
