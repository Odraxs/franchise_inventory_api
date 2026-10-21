package co.com.bancolombia.model.franchise;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Franchise {
    private final UUID id;
    private final String name;
}
