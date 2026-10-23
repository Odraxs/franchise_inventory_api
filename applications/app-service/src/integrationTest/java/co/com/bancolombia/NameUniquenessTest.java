package co.com.bancolombia;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

class NameUniquenessTest extends IntegrationTestBase {
    private static final String NAME = "name";
    private static final String DUPLICATE_FRANCHISE_NAME = "DUPLICATE_FRANCHISE_NAME";
    private static final String DUPLICATE_BRANCH_NAME = "DUPLICATE_BRANCH_NAME";

    @Test
    void creatingAFranchiseWhoseNameDiffersOnlyInCaseReturns409DuplicateFranchiseName() {
        String name = "Franchise " + UUID.randomUUID();
        postFranchise(name).expectStatus().isCreated();

        postFranchise(name.toUpperCase(Locale.ROOT))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo(DUPLICATE_FRANCHISE_NAME);
    }

    @Test
    void addingABranchWhoseNameDiffersOnlyInCaseFromAnotherOfTheSameFranchiseReturns409DuplicateBranchName() {
        UUID franchiseId = createFranchise();
        createBranch(franchiseId, "Downtown");

        postBranch(franchiseId, "DOWNTOWN")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo(DUPLICATE_BRANCH_NAME);
    }

    @Test
    void addingABranchWithTheSameNameToAnotherFranchiseReturns201() {
        UUID firstFranchiseId = createFranchise();
        UUID secondFranchiseId = createFranchise();
        createBranch(firstFranchiseId, "Downtown");

        postBranch(secondFranchiseId, "Downtown")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.name").isEqualTo("Downtown");
    }

    private WebTestClient.ResponseSpec postFranchise(String name) {
        return webTestClient.post()
                .uri(FRANCHISES)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(NAME, name))
                .exchange();
    }

    private WebTestClient.ResponseSpec postBranch(UUID franchiseId, String name) {
        return webTestClient.post()
                .uri(BRANCHES, franchiseId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(NAME, name))
                .exchange();
    }
}
