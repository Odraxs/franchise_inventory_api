package co.com.bancolombia.api;

/** Route templates and path variable names shared by the router and the handler. */
final class ApiPaths {
    static final String FRANCHISE_ID = "franchiseId";
    static final String BRANCH_ID = "branchId";
    static final String ID = "id";

    static final String FRANCHISES = "/franchises";
    static final String FRANCHISE = FRANCHISES + "/{" + ID + "}";
    static final String BRANCHES = FRANCHISES + "/{" + FRANCHISE_ID + "}/branches";
    static final String BRANCH = BRANCHES + "/{" + ID + "}";
    static final String PRODUCTS = BRANCHES + "/{" + BRANCH_ID + "}/products";
    static final String PRODUCT = PRODUCTS + "/{" + ID + "}";

    private ApiPaths() {
    }
}
