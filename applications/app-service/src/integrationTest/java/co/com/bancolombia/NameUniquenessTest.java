package co.com.bancolombia;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.UUID;

class NameUniquenessTest extends IntegrationTestBase {
    private static final String DUPLICATE_FRANCHISE_NAME = "DUPLICATE_FRANCHISE_NAME";
    private static final String DUPLICATE_BRANCH_NAME = "DUPLICATE_BRANCH_NAME";

    @Test
    void creatingAFranchiseWhoseNameDiffersOnlyInCaseReturns409DuplicateFranchiseName() {
        String name = "Franchise " + UUID.randomUUID();
        postFranchise(name).expectStatus().isCreated();

        postFranchise(name.toUpperCase(Locale.ROOT))
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo(DUPLICATE_FRANCHISE_NAME);
    }

    @Test
    void addingABranchWhoseNameDiffersOnlyInCaseFromAnotherOfTheSameFranchiseReturns409DuplicateBranchName() {
        UUID franchiseId = createFranchise();
        createBranch(franchiseId, "Downtown");

        postBranch(franchiseId, "DOWNTOWN")
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
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
}
