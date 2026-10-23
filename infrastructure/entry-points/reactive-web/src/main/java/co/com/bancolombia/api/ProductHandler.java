package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.CreateProductRequest;
import co.com.bancolombia.api.dto.ProductResponse;
import co.com.bancolombia.api.dto.RenameProductRequest;
import co.com.bancolombia.api.dto.UpdateStockRequest;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.usecase.addproduct.AddProductUseCase;
import co.com.bancolombia.usecase.deleteproduct.DeleteProductUseCase;
import co.com.bancolombia.usecase.renameproduct.RenameProductUseCase;
import co.com.bancolombia.usecase.updateproductstock.UpdateProductStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductHandler {
    private final AddProductUseCase addProductUseCase;
    private final RenameProductUseCase renameProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final UpdateProductStockUseCase updateProductStockUseCase;

    public Mono<ServerResponse> addProduct(ServerRequest serverRequest) {
        return Mono.zip(RequestReader.parseId(serverRequest, ApiPaths.FRANCHISE_ID),
                        RequestReader.parseId(serverRequest, ApiPaths.BRANCH_ID))
                .flatMap(ids -> RequestReader.readBody(serverRequest, CreateProductRequest.class)
                        .map(request -> Product.builder().branchId(ids.getT2()).name(request.name())
                                .stock(request.stock()).build())
                        .flatMap(product -> addProductUseCase.execute(ids.getT1(), product)))
                .flatMap(product -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.PRODUCT)
                                .build(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID), product.branchId(),
                                        product.id()))
                        .bodyValue(ProductResponse.from(product)))
                .transform(ErrorResponses::handle);
    }

    public Mono<ServerResponse> renameProduct(ServerRequest serverRequest) {
        return parseProductIds(serverRequest)
                .flatMap(ids -> RequestReader.readBody(serverRequest, RenameProductRequest.class)
                        .filter(request -> !request.hasStock())
                        .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_UPDATE)))
                        .flatMap(request -> renameProductUseCase.execute(ids.getT1(), ids.getT2(), ids.getT3(),
                                request.name())))
                .flatMap(product -> ServerResponse.ok().bodyValue(ProductResponse.from(product)))
                .transform(ErrorResponses::handle);
    }

    public Mono<ServerResponse> deleteProduct(ServerRequest serverRequest) {
        return parseProductIds(serverRequest)
                .flatMap(ids -> deleteProductUseCase.execute(ids.getT1(), ids.getT2(), ids.getT3()))
                .then(Mono.defer(() -> ServerResponse.noContent().build()))
                .transform(ErrorResponses::handle);
    }

    public Mono<ServerResponse> updateProductStock(ServerRequest serverRequest) {
        return parseProductIds(serverRequest)
                .flatMap(ids -> RequestReader.readBody(serverRequest, UpdateStockRequest.class)
                        .flatMap(request -> updateProductStockUseCase.execute(ids.getT1(), ids.getT2(), ids.getT3(),
                                request.stock())))
                .flatMap(product -> ServerResponse.ok().bodyValue(ProductResponse.from(product)))
                .transform(ErrorResponses::handle);
    }

    /** The franchise, branch and product ids, in that order. */
    private static Mono<Tuple3<UUID, UUID, UUID>> parseProductIds(ServerRequest serverRequest) {
        return Mono.zip(RequestReader.parseId(serverRequest, ApiPaths.FRANCHISE_ID),
                RequestReader.parseId(serverRequest, ApiPaths.BRANCH_ID),
                RequestReader.parseId(serverRequest, ApiPaths.ID));
    }
}
