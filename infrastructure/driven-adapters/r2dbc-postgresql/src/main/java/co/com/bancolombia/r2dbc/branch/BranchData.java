package co.com.bancolombia.r2dbc.branch;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Row of the branch table. The database owns id, created_at and updated_at, so they are not written here.
 */
@Table("branch")
public record BranchData(@Id UUID id, UUID franchiseId, String name) {
}
