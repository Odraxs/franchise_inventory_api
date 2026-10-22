package co.com.bancolombia.r2dbc.product;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Row of the product table. The database owns id, created_at, updated_at and deleted_at, so they are not written here.
 */
@Builder
@Table("product")
public record ProductData(@Id UUID id, UUID branchId, String name, Integer stock) {
}
