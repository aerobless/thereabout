package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.generated.model.*;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import org.springframework.stereotype.Component;

/** Populated from the same SDK definitions that are registered with the MCP server. */
@Component
public class McpToolCatalog {
  private final Map<String, GenFinanceMcpTool> tools = new TreeMap<>();
  public synchronized void register(McpSchema.Tool tool, boolean read) {
    tools.put(tool.name(), new GenFinanceMcpTool().name(tool.name()).description(tool.description()).readOnly(read));
  }
  public synchronized GenFinanceMcpCatalog catalog() {
    return new GenFinanceMcpCatalog().endpoints(List.of(new GenFinanceMcpEndpoint().path("/mcp/finances").tools(new ArrayList<>(tools.values()))));
  }
}
