package co.com.bancolombia.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FranchiseResponse(UUID id, String name) {
}
