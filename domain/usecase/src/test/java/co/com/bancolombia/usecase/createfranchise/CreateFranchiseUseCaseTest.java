package co.com.bancolombia.usecase.createfranchise;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateFranchiseUseCaseTest {

    @Mock
    private FranchiseGateway franchiseGateway;

    private CreateFranchiseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateFranchiseUseCase(franchiseGateway);
    }

    @Test
    void savesFranchiseAndEmitsItWithItsGeneratedId() {
        UUID id = UUID.randomUUID();
        when(franchiseGateway.save(any(Franchise.class)))
                .thenAnswer(invocation -> Mono.just(((Franchise) invocation.getArgument(0)).toBuilder().id(id).build()));

        StepVerifier.create(useCase.execute(Franchise.builder().name("  Nequi Store  ").build()))
                .expectNextMatches(saved -> id.equals(saved.id()) && "Nequi Store".equals(saved.name()))
                .verifyComplete();
    }

    @Test
    void propagatesDuplicateNameFromTheGateway() {
        when(franchiseGateway.save(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_FRANCHISE_NAME)));

        StepVerifier.create(useCase.execute(Franchise.builder().name("Nequi").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_FRANCHISE_NAME)
                .verify();
        verify(franchiseGateway).save(any(Franchise.class));
    }
}
