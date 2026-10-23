package co.com.bancolombia.api.dto;

import lombok.Builder;

@Builder
public record ErrorResponse(String code, String message) {
}
