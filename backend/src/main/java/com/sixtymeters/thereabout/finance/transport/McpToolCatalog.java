package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.generated.model.*;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import org.springframework.stereotype.Component;

/** Populated from the SDK definitions registered with each MCP server. */
@Component
public class McpToolCatalog {
  private final Map<String, Map<String, GenFinanceMcpTool>> endpoints = new TreeMap<>();
  public synchronized void register(McpSchema.Tool tool, boolean read) { register("/mcp/finances", tool, read); }
  public synchronized void register(String endpoint, McpSchema.Tool tool, boolean read) {
    endpoints.computeIfAbsent(endpoint, ignored -> new TreeMap<>()).put(tool.name(),
        new GenFinanceMcpTool().name(tool.name()).description(tool.description()).readOnly(read));
  }
  public synchronized GenFinanceMcpCatalog catalog() {
    return new GenFinanceMcpCatalog().endpoints(endpoints.entrySet().stream().map(entry ->
        new GenFinanceMcpEndpoint().path(entry.getKey()).tools(new ArrayList<>(entry.getValue().values()))).toList());
  }
}
