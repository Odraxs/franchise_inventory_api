package co.com.bancolombia.api.dto;

import java.util.UUID;

public record ProductResponse(UUID id, String name, Long stock) {
}
