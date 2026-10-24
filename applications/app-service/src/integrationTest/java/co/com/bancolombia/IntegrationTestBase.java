package co.com.bancolombia;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

/**
 * The whole app on a random port against one PostgreSQL container, called over HTTP with the API Credential.
 * Every test creates its own Franchise through the API, so tests never see each other's data and need no cleanup.
 */
@SpringBootTest(classes = MainApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public abstract class IntegrationTestBase {
    protected static final String FRANCHISES = "/franchises";
    protected static final String BRANCHES = FRANCHISES + "/{franchiseId}/branches";
    protected static final String PRODUCTS = BRANCHES + "/{branchId}/products";
    protected static final String PRODUCT = PRODUCTS + "/{id}";
    protected static final String PRODUCT_STOCK = PRODUCT + "/stock";
    protected static final String TOP_STOCK_PRODUCTS = FRANCHISES + "/{franchiseId}/top-stock-products";

    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final String SCHEMA = "public";
    private static final String CLIENT_USERNAME = "integration-test-client";
    private static final String CLIENT_PASSWORD = "integration-test-only-api-credential-0123456789";
    private static final String NAME = "name";
    private static final String STOCK = "stock";
    private static final String PAGE = "page";
    private static final String SIZE = "size";

    /**
     * Started once per JVM, before any Spring context reads its address, and never stopped by a test class: a
     * per-class container would stop while the cached context still points at it. Ryuk removes it when the JVM ends.
     */
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }

    /** Sends the test API Credential with every request. */
    protected WebTestClient webTestClient;

    @Autowired
    private WebTestClient client;

    /** The custom connection pool reads adapters.r2dbc.*, and Flyway reuses them, so @ServiceConnection cannot wire it. */
    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("adapters.r2dbc.host", POSTGRES::getHost);
        registry.add("adapters.r2dbc.port", () -> POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registry.add("adapters.r2dbc.database", POSTGRES::getDatabaseName);
        registry.add("adapters.r2dbc.schema", () -> SCHEMA);
        registry.add("adapters.r2dbc.username", POSTGRES::getUsername);
        registry.add("adapters.r2dbc.password", POSTGRES::getPassword);
        registry.add("security.client.username", () -> CLIENT_USERNAME);
        registry.add("security.client.password", () -> CLIENT_PASSWORD);
    }

    @BeforeEach
    void authenticateAsClient() {
        webTestClient = client.mutate()
                .defaultHeaders(headers -> headers.setBasicAuth(CLIENT_USERNAME, CLIENT_PASSWORD))
                .build();
    }

    /** Creates a Franchise with a unique name, so no other test can collide with it. */
    protected UUID createFranchise() {
        return idOf(postFranchise("Franchise " + UUID.randomUUID()));
    }

    protected UUID createBranch(UUID franchiseId, String name) {
        return idOf(postBranch(franchiseId, name));
    }

    protected UUID createProduct(UUID franchiseId, UUID branchId, String name, int stock) {
        return idOf(send(webTestClient.post(), PRODUCTS, Map.of(NAME, name, STOCK, stock), franchiseId, branchId));
    }

    protected WebTestClient.ResponseSpec postFranchise(String name) {
        return send(webTestClient.post(), FRANCHISES, Map.of(NAME, name));
    }

    protected WebTestClient.ResponseSpec postBranch(UUID franchiseId, String name) {
        return send(webTestClient.post(), BRANCHES, Map.of(NAME, name), franchiseId);
    }

    protected WebTestClient.ResponseSpec renameProduct(UUID franchiseId, UUID branchId, UUID productId, String name) {
        return send(webTestClient.patch(), PRODUCT, Map.of(NAME, name), franchiseId, branchId, productId);
    }

    protected WebTestClient.ResponseSpec changeStock(UUID franchiseId, UUID branchId, UUID productId, int stock) {
        return send(webTestClient.patch(), PRODUCT_STOCK, Map.of(STOCK, stock), franchiseId, branchId, productId);
    }

    protected WebTestClient.ResponseSpec deleteProduct(UUID franchiseId, UUID branchId, UUID productId) {
        return webTestClient.delete()
                .uri(PRODUCT, franchiseId, branchId, productId)
                .exchange();
    }

    protected WebTestClient.ResponseSpec getTopStockReport(UUID franchiseId) {
        return webTestClient.get()
                .uri(TOP_STOCK_PRODUCTS, franchiseId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }

    protected WebTestClient.ResponseSpec getTopStockReport(UUID franchiseId, int page, int size) {
        return webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(TOP_STOCK_PRODUCTS).queryParam(PAGE, page).queryParam(SIZE, size)
                        .build(franchiseId))
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }

    private static WebTestClient.ResponseSpec send(WebTestClient.RequestBodyUriSpec method, String path,
                                                   Map<String, Object> body, Object... pathVariables) {
        return method.uri(path, pathVariables)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private static UUID idOf(WebTestClient.ResponseSpec response) {
        return response
                .expectStatus().isCreated()
                .expectBody(CreatedResource.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private record CreatedResource(UUID id) {
    }
}
