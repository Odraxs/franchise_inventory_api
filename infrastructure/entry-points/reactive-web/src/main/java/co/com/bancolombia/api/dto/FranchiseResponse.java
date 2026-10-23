package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.franchise.Franchise;
import lombok.Builder;

import java.util.UUID;

@Builder
public record FranchiseResponse(UUID id, String name) {

    public static FranchiseResponse from(Franchise franchise) {
        return new FranchiseResponse(franchise.id(), franchise.name());
    }
}
