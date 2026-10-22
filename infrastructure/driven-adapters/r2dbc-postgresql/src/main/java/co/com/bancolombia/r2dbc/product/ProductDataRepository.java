package co.com.bancolombia.r2dbc.product;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface ProductDataRepository extends ReactiveCrudRepository<ProductData, UUID> {
}
