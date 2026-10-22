package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.PATCH;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {
    @Bean
    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return route(POST(ApiPaths.FRANCHISES), handler::createFranchise)
                .andRoute(PATCH(ApiPaths.FRANCHISE), handler::renameFranchise)
                .andRoute(POST(ApiPaths.BRANCHES), handler::addBranch)
                .andRoute(PATCH(ApiPaths.BRANCH), handler::renameBranch)
                .andRoute(POST(ApiPaths.PRODUCTS), handler::addProduct)
                .andRoute(PATCH(ApiPaths.PRODUCT), handler::renameProduct)
                .andRoute(DELETE(ApiPaths.PRODUCT), handler::deleteProduct)
                .andRoute(PATCH(ApiPaths.PRODUCT_STOCK), handler::updateProductStock)
                .andRoute(GET(ApiPaths.TOP_STOCK_PRODUCTS), handler::getTopStockProducts);
    }
}
