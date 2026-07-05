package com.wellnessmate.advisor.agent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Holds all available {@link Tool} instances and provides convenience
 * methods for building the OpenAI-compatible {@code tools} payload and
 * dispatching by name.
 */
@Component
public class ToolRegistry {

  private final Map<String, Tool> tools;

  public ToolRegistry(List<Tool> tools) {
    this.tools = tools.stream().collect(Collectors.toMap(
        Tool::name,
        t -> t,
        (a, b) -> { throw new IllegalStateException("Duplicate tool name: " + a.name()); },
        LinkedHashMap::new));
  }

  /** Look up a tool by its {@link Tool#name()}. */
  public Tool get(String name) {
    return tools.get(name);
  }

  /** All registered tools. */
  public Collection<Tool> all() {
    return tools.values();
  }

  /**
   * Build the {@code tools} payload list for an OpenAI-compatible chat completion request.
   * Each entry has the shape {@code {"type":"function","function":{name,description,parameters}}}.
   */
  public List<Map<String, Object>> toOpenAiTools() {
    return tools.values().stream().map(tool -> {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("type", "function");
      Map<String, Object> fn = new LinkedHashMap<>();
      fn.put("name", tool.name());
      fn.put("description", tool.description());
      fn.put("parameters", tool.parametersSchema());
      entry.put("function", fn);
      return entry;
    }).toList();
  }
}
