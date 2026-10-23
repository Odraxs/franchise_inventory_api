package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.branch.Branch;
import lombok.Builder;

import java.util.UUID;

@Builder
public record BranchResponse(UUID id, String name) {

    public static BranchResponse from(Branch branch) {
        return BranchResponse.builder().id(branch.id()).name(branch.name()).build();
    }
}
