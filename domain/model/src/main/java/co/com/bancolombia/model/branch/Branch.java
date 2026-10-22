package co.com.bancolombia.model.branch;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record Branch(UUID id, UUID franchiseId, String name) {
}
