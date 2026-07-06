package com.wellnessmate.advisor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * A callable tool exposed to the LLM via function calling.
 * Each implementation defines its own JSON Schema and execution logic.
 */
public interface Tool {

  /** Unique tool name exposed to the LLM (e.g. "query_tracker_data"). */
  String name();

  /** Human-readable description that helps the LLM decide when to use this tool. */
  String description();

  /**
   * JSON Schema for the tool's parameters, as a Map suitable for serialization
   * into an OpenAI-compatible {@code tools[].function.parameters} block.
   */
  Map<String, Object> parametersSchema();

  /**
   * Execute the tool on behalf of the given user.
   * @param userId  the authenticated user (injected by the orchestrator)
   * @param arguments  parsed JSON arguments from the LLM tool call
   * @return a string summary of the result (injected back into the conversation)
   */
  String execute(Long userId, JsonNode arguments);
}
