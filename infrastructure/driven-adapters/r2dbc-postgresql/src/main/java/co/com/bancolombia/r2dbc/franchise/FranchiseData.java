package co.com.bancolombia.r2dbc.franchise;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Row of the franchise table. The database owns id, created_at and updated_at, so they are not written here.
 */
@Table("franchise")
public record FranchiseData(@Id UUID id, String name) {
}
