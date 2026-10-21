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
import static org.mockito.Mockito.verifyNoInteractions;
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
    void savesFranchiseWithStrippedNameAndEmitsIt() {
        UUID id = UUID.randomUUID();
        when(franchiseGateway.save(any(Franchise.class)))
                .thenAnswer(invocation -> Mono.just(((Franchise) invocation.getArgument(0)).toBuilder().id(id).build()));

        StepVerifier.create(useCase.execute(Franchise.builder().name("  Nequi Store  ").build()))
                .expectNextMatches(saved -> id.equals(saved.getId()) && "Nequi Store".equals(saved.getName()))
                .verifyComplete();
    }

    @Test
    void acceptsNameWithMaximumLength() {
        String name = "a".repeat(CreateFranchiseUseCase.MAX_NAME_LENGTH);
        when(franchiseGateway.save(any(Franchise.class))).thenReturn(Mono.just(Franchise.builder().name(name).build()));

        StepVerifier.create(useCase.execute(Franchise.builder().name(name).build()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void rejectsNullBlankAndTooLongNamesWithoutCallingTheGateway() {
        String tooLong = "a".repeat(CreateFranchiseUseCase.MAX_NAME_LENGTH + 1);

        for (String name : new String[]{null, "", "   ", tooLong}) {
            StepVerifier.create(useCase.execute(Franchise.builder().name(name).build()))
                    .expectErrorMatches(error -> error instanceof BusinessException exception
                            && exception.getErrorCode() == ErrorCode.INVALID_FRANCHISE_NAME)
                    .verify();
        }
        verifyNoInteractions(franchiseGateway);
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
