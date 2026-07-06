package com.wellnessmate.advisor.agent;

/**
 * A parsed tool-call extracted from an LLM streaming response.
 * {@code arguments} is the raw JSON string as returned by the model.
 */
public record ToolCall(String id, String name, String arguments) {}
