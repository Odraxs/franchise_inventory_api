package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.product.Product;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductResponse(UUID id, String name, Integer stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.name(), product.stock());
    }
}
