package co.com.bancolombia.api;

/** Route templates, path variable and query parameter names shared by the router and the handlers. */
final class ApiPaths {
    static final String FRANCHISE_ID = "franchiseId";
    static final String BRANCH_ID = "branchId";
    static final String ID = "id";

    static final String PAGE = "page";
    static final String SIZE = "size";

    static final String FRANCHISES = "/franchises";
    static final String FRANCHISE = FRANCHISES + "/{" + ID + "}";
    static final String BRANCHES = FRANCHISES + "/{" + FRANCHISE_ID + "}/branches";
    static final String BRANCH = BRANCHES + "/{" + ID + "}";
    static final String PRODUCTS = BRANCHES + "/{" + BRANCH_ID + "}/products";
    static final String PRODUCT = PRODUCTS + "/{" + ID + "}";
    static final String TOP_STOCK_PRODUCTS = FRANCHISES + "/{" + FRANCHISE_ID + "}/top-stock-products";

    static final String PRODUCT_STOCK = PRODUCT + "/stock";

    private ApiPaths() {
    }
}
