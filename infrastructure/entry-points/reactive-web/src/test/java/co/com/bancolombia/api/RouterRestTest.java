package co.com.bancolombia.api;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, Handler.class})
@WebFluxTest
class RouterRestTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateFranchiseUseCase createFranchiseUseCase;

    @Test
    void createFranchiseReturns201WithLocationAndBody() {
        UUID id = UUID.randomUUID();
        when(createFranchiseUseCase.execute(argThat(franchise -> "Nequi".equals(franchise.getName()))))
                .thenReturn(Mono.just(Franchise.builder().id(id).name("Nequi").build()));

        postFranchise(Map.of("name", "Nequi"))
                .expectStatus().isCreated()
                .expectHeader().location("/franchises/" + id)
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Nequi");
    }

    @Test
    void createFranchiseReturns400ForInvalidName() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.INVALID_FRANCHISE_NAME)));

        postFranchise(Map.of("name", " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_FRANCHISE_NAME");
    }

    @Test
    void createFranchiseSendsNullNameWhenBodyIsEmpty() {
        when(createFranchiseUseCase.execute(argThat(franchise -> franchise.getName() == null)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.INVALID_FRANCHISE_NAME)));

        webTestClient.post()
                .uri("/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createFranchiseReturns409ForDuplicateName() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_FRANCHISE_NAME)));

        postFranchise(Map.of("name", "Nequi"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_FRANCHISE_NAME")
                .jsonPath("$.message").isEqualTo("A franchise with this name already exists");
    }

    private WebTestClient.ResponseSpec postFranchise(Map<String, String> body) {
        return webTestClient.post()
                .uri("/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }
}
