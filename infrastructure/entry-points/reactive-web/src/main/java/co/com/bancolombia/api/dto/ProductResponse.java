package co.com.bancolombia.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductResponse(UUID id, String name, Integer stock) {
}
