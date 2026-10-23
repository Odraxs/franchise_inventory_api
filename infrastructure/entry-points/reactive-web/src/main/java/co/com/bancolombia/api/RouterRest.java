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
    public RouterFunction<ServerResponse> routerFunction(FranchiseHandler franchiseHandler,
                                                         BranchHandler branchHandler,
                                                         ProductHandler productHandler,
                                                         TopStockHandler topStockHandler) {
        return route(POST(ApiPaths.FRANCHISES), franchiseHandler::createFranchise)
                .andRoute(PATCH(ApiPaths.FRANCHISE), franchiseHandler::renameFranchise)
                .andRoute(POST(ApiPaths.BRANCHES), branchHandler::addBranch)
                .andRoute(PATCH(ApiPaths.BRANCH), branchHandler::renameBranch)
                .andRoute(POST(ApiPaths.PRODUCTS), productHandler::addProduct)
                .andRoute(PATCH(ApiPaths.PRODUCT), productHandler::renameProduct)
                .andRoute(DELETE(ApiPaths.PRODUCT), productHandler::deleteProduct)
                .andRoute(PATCH(ApiPaths.PRODUCT_STOCK), productHandler::updateProductStock)
                .andRoute(GET(ApiPaths.TOP_STOCK_PRODUCTS), topStockHandler::getTopStockProducts);
    }
}
