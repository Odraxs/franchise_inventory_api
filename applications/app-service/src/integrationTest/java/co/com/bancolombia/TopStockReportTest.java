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
}
