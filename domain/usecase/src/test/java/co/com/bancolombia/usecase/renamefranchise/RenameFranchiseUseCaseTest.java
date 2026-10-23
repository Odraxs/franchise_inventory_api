package co.com.bancolombia.usecase.renamefranchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameFranchiseUseCaseTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    private RenameFranchiseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenameFranchiseUseCase(franchiseGateway);
    }

    @Test
    void renamesWithTheStrippedNameAndEmitsTheUpdatedFranchise() {
        when(franchiseGateway.rename(ID, "Nequi Store"))
                .thenReturn(Mono.just(Franchise.builder().id(ID).name("Nequi Store").build()));

        StepVerifier.create(useCase.execute(Franchise.builder().id(ID).name("  Nequi Store  ").build()))
                .expectNextMatches(renamed -> ID.equals(renamed.id()) && "Nequi Store".equals(renamed.name()))
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWhenNoFranchiseMatches() {
        when(franchiseGateway.rename(ID, "Nequi")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Franchise.builder().id(ID).name("Nequi").build()))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.FRANCHISE_NOT_FOUND))
                .verify();
    }

    @Test
    void propagatesDuplicateNameFromTheGateway() {
        when(franchiseGateway.rename(ID, "Nequi"))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_FRANCHISE_NAME)));

        StepVerifier.create(useCase.execute(Franchise.builder().id(ID).name("Nequi").build()))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.DUPLICATE_FRANCHISE_NAME))
                .verify();
    }

    private static boolean hasCode(Throwable error, ErrorCode code) {
        return error instanceof BusinessException exception && exception.getErrorCode() == code;
    }
}
