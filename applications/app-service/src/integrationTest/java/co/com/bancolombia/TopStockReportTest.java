package co.com.bancolombia;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TopStockReportTest extends IntegrationTestBase {

    @Test
    void reportShowsTheTopStockProductOfEachBranchAfterAStockChangeAndNoProductForAnEmptyBranch() {
        UUID franchiseId = createFranchise();
        UUID downtownId = createBranch(franchiseId, "Downtown");
        UUID uptownId = createBranch(franchiseId, "Uptown");
        UUID burgerId = createProduct(franchiseId, downtownId, "Cheeseburger", 10);
        createProduct(franchiseId, downtownId, "Fries", 25);

        changeStock(franchiseId, downtownId, burgerId, 40)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(burgerId.toString())
                .jsonPath("$.stock").isEqualTo(40);

        getTopStockReport(franchiseId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.franchiseId").isEqualTo(franchiseId.toString())
                .jsonPath("$.branches.length()").isEqualTo(2)
                .jsonPath("$.branches[0].branchId").isEqualTo(downtownId.toString())
                .jsonPath("$.branches[0].branchName").isEqualTo("Downtown")
                .jsonPath("$.branches[0].topProduct.id").isEqualTo(burgerId.toString())
                .jsonPath("$.branches[0].topProduct.name").isEqualTo("Cheeseburger")
                .jsonPath("$.branches[0].topProduct.stock").isEqualTo(40)
                .jsonPath("$.branches[1].branchId").isEqualTo(uptownId.toString())
                .jsonPath("$.branches[1].branchName").isEqualTo("Uptown")
                .jsonPath("$.branches[1]").value(Map.class,
                        branch -> assertThat(branch).containsEntry("topProduct", null));
    }

    @Test
    void theOldestProductIsTheTopStockProductWhenTwoProductsTieOnStock() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");
        UUID zucchiniId = createProduct(franchiseId, branchId, "Zucchini", 15);
        createProduct(franchiseId, branchId, "Apple", 15);

        getTopStockReport(franchiseId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.branches.length()").isEqualTo(1)
                .jsonPath("$.branches[0].topProduct.id").isEqualTo(zucchiniId.toString())
                .jsonPath("$.branches[0].topProduct.name").isEqualTo("Zucchini")
                .jsonPath("$.branches[0].topProduct.stock").isEqualTo(15);
    }

    @Test
    void pagesSortBranchesByNameIgnoringCaseAndKeepTheTopStockProductOfEachBranch() {
        UUID franchiseId = createFranchise();
        UUID bravoId = createBranch(franchiseId, "bravo");
        UUID charlieId = createBranch(franchiseId, "charlie");
        UUID alphaId = createBranch(franchiseId, "Alpha");
        UUID colaId = createProduct(franchiseId, charlieId, "Cola", 9);
        createProduct(franchiseId, charlieId, "Agua", 3);

        getTopStockReport(franchiseId, 0, 2)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(2)
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.branches.length()").isEqualTo(2)
                .jsonPath("$.branches[0].branchId").isEqualTo(alphaId.toString())
                .jsonPath("$.branches[1].branchId").isEqualTo(bravoId.toString());

        getTopStockReport(franchiseId, 1, 2)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.branches.length()").isEqualTo(1)
                .jsonPath("$.branches[0].branchId").isEqualTo(charlieId.toString())
                .jsonPath("$.branches[0].topProduct.id").isEqualTo(colaId.toString());
    }

    @Test
    void aPagePastTheLastBranchHasNoBranchesAndTheFranchiseTotals() {
        UUID franchiseId = createFranchise();
        createBranch(franchiseId, "Downtown");
        createBranch(franchiseId, "Uptown");
        createBranch(franchiseId, "Midtown");

        getTopStockReport(franchiseId, 5, 2)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(5)
                .jsonPath("$.size").isEqualTo(2)
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.branches.length()").isEqualTo(0);
    }

    @Test
    void theDefaultPageHoldsTheFirstTwentyBranches() {
        UUID franchiseId = createFranchise();
        createBranch(franchiseId, "Downtown");

        getTopStockReport(franchiseId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.totalPages").isEqualTo(1);
    }

    @Test
    void aSizeAboveTheMaximumIsAnInvalidRequest() {
        UUID franchiseId = createFranchise();

        getTopStockReport(franchiseId, 0, 101)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void anUnknownFranchiseIsNotFoundOnAnyPage() {
        getTopStockReport(UUID.randomUUID(), 3, 10)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FRANCHISE_NOT_FOUND");
    }
}
