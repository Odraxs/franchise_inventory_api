package co.com.bancolombia.model.franchise;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record Franchise(UUID id, String name) {
}
