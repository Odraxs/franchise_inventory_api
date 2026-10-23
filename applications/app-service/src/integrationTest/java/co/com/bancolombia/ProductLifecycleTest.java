package co.com.bancolombia;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

class ProductLifecycleTest extends IntegrationTestBase {
    private static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    private static final String DUPLICATE_PRODUCT_NAME = "DUPLICATE_PRODUCT_NAME";

    @Test
    void deletingAProductRemovesItFromTheTopStockReport() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");
        UUID deletedId = createProduct(franchiseId, branchId, "Cheeseburger", 30);
        UUID remainingId = createProduct(franchiseId, branchId, "Fries", 10);

        deleteProduct(franchiseId, branchId, deletedId)
                .expectStatus().isNoContent();

        getTopStockReport(franchiseId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.branches.length()").isEqualTo(1)
                .jsonPath("$.branches[0].topProduct.id").isEqualTo(remainingId.toString())
                .jsonPath("$.branches[0].topProduct.name").isEqualTo("Fries")
                .jsonPath("$.branches[0].topProduct.stock").isEqualTo(10);
    }

    @Test
    void aDeletedProductsNameCanBeReusedInADifferentCase() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");
        UUID deletedId = createProduct(franchiseId, branchId, "Cheeseburger", 10);

        deleteProduct(franchiseId, branchId, deletedId)
                .expectStatus().isNoContent();

        UUID reusedId = createProduct(franchiseId, branchId, "cHEESEBURGER", 10);

        getTopStockReport(franchiseId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.branches[0].topProduct.id").isEqualTo(reusedId.toString())
                .jsonPath("$.branches[0].topProduct.name").isEqualTo("cHEESEBURGER");
    }

    @Test
    void deletingAnAlreadyDeletedProductReturns404() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");
        UUID productId = createProduct(franchiseId, branchId, "Cheeseburger", 10);

        deleteProduct(franchiseId, branchId, productId)
                .expectStatus().isNoContent();

        deleteProduct(franchiseId, branchId, productId)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(PRODUCT_NOT_FOUND);
    }

    @Test
    void renamingAProductToAnotherActiveProductsNameIgnoringCaseReturns409() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");
        UUID friesId = createProduct(franchiseId, branchId, "Fries", 10);
        createProduct(franchiseId, branchId, "Cheeseburger", 20);

        renameProduct(franchiseId, branchId, friesId, "CHEESEBURGER")
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo(DUPLICATE_PRODUCT_NAME);
    }

    @Test
    void renamingAnUnknownProductReturns404() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");

        renameProduct(franchiseId, branchId, UUID.randomUUID(), "Cheeseburger")
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(PRODUCT_NOT_FOUND);
    }

    @Test
    void renamingAProductToItsOwnNameInADifferentCaseReturns200() {
        UUID franchiseId = createFranchise();
        UUID branchId = createBranch(franchiseId, "Downtown");
        UUID productId = createProduct(franchiseId, branchId, "Cheeseburger", 7);

        renameProduct(franchiseId, branchId, productId, "CHEESEBURGER")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId.toString())
                .jsonPath("$.name").isEqualTo("CHEESEBURGER")
                .jsonPath("$.stock").isEqualTo(7);
    }

    @Test
    void aProductIdUsedUnderAnotherBranchOfTheSameFranchiseReturns404ForRenameStockAndDelete() {
        UUID franchiseId = createFranchise();
        UUID downtownId = createBranch(franchiseId, "Downtown");
        UUID uptownId = createBranch(franchiseId, "Uptown");
        UUID productId = createProduct(franchiseId, downtownId, "Cheeseburger", 10);

        renameProduct(franchiseId, uptownId, productId, "Cola")
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(PRODUCT_NOT_FOUND);
        changeStock(franchiseId, uptownId, productId, 99)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(PRODUCT_NOT_FOUND);
        deleteProduct(franchiseId, uptownId, productId)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(PRODUCT_NOT_FOUND);
    }
}
