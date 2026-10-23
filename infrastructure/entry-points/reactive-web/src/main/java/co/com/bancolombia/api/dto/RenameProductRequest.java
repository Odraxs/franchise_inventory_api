package co.com.bancolombia.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * A Product rename changes only the name. The stock node is read only to reject a body that sends it:
 * a non-null node means the key is present, because Jackson binds an explicit null to NullNode.
 */
public record RenameProductRequest(String name, JsonNode stock) {

    public boolean hasStock() {
        return stock != null;
    }
}
