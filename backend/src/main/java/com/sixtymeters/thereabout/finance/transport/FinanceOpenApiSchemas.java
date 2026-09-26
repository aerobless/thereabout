package com.sixtymeters.thereabout.finance.transport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Loads bundled OpenAPI schemas as self-contained MCP JSON schemas at startup. */
final class FinanceOpenApiSchemas {
  private static final Path INDEX = Path.of("openapi/finances.yaml");
  private final ObjectMapper json;
  private final Map<Path, JsonNode> documents = new HashMap<>();
  private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

  FinanceOpenApiSchemas(ObjectMapper json) {
    this.json = json;
  }

  JsonNode load() throws IOException {
    return resolve(
        document(INDEX).required("components").required("schemas"), INDEX, new HashSet<>());
  }

  private JsonNode document(Path path) throws IOException {
    if (path.isAbsolute() || !path.startsWith("openapi")) {
      throw new IllegalStateException(
          "OpenAPI references must stay within bundled openapi resources");
    }
    if (!documents.containsKey(path)) {
      try (var input = new ClassPathResource(path.toString()).getInputStream()) {
        documents.put(path, json.valueToTree(yaml.load(input)));
      }
    }
    return documents.get(path);
  }

  private JsonNode resolve(JsonNode node, Path source, Set<String> resolving) throws IOException {
    if (node.has("$ref")) {
      String reference = node.required("$ref").asString();
      String[] parts = reference.split("#", 2);
      if (parts.length != 2 || !parts[1].startsWith("/components/schemas/")) {
        throw new IllegalStateException("Expected an OpenAPI schema reference: " + reference);
      }
      Path target = parts[0].isEmpty() ? source : source.getParent().resolve(parts[0]).normalize();
      String key = target + "#" + parts[1];
      if (!resolving.add(key)) {
        throw new IllegalStateException("Recursive schemas cannot be inlined for MCP: " + key);
      }
      JsonNode result = resolve(document(target).requiredAt(parts[1]), target, resolving);
      resolving.remove(key);
      return result;
    }
    if (node.isObject()) {
      var result = json.createObjectNode();
      for (var property : node.properties()) {
        result.set(property.getKey(), resolve(property.getValue(), source, resolving));
      }
      return result;
    }
    if (node.isArray()) {
      var result = json.createArrayNode();
      for (var item : node) result.add(resolve(item, source, resolving));
      return result;
    }
    return node;
  }
}
