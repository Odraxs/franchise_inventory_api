package co.com.bancolombia.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record BranchResponse(UUID id, String name) {
}
