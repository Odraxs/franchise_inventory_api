package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.BranchResponse;
import co.com.bancolombia.api.dto.BranchTopStockResponse;
import co.com.bancolombia.api.dto.CreateBranchRequest;
import co.com.bancolombia.api.dto.CreateFranchiseRequest;
import co.com.bancolombia.api.dto.ErrorResponse;
import co.com.bancolombia.api.dto.CreateProductRequest;
import co.com.bancolombia.api.dto.FranchiseResponse;
import co.com.bancolombia.api.dto.ProductResponse;
import co.com.bancolombia.api.dto.UpdateStockRequest;
import co.com.bancolombia.api.dto.TopStockReportResponse;
import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.DomainException;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.product.BranchTopStock;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.usecase.addbranch.AddBranchUseCase;
import co.com.bancolombia.usecase.addproduct.AddProductUseCase;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import co.com.bancolombia.usecase.deleteproduct.DeleteProductUseCase;
import co.com.bancolombia.usecase.updateproductstock.UpdateProductStockUseCase;
import co.com.bancolombia.usecase.gettopstockreport.GetTopStockReportUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class Handler {
    private static final String INVALID_PREFIX = "INVALID_";
    private static final String NOT_FOUND_SUFFIX = "_NOT_FOUND";
    private static final String DUPLICATE_PREFIX = "DUPLICATE_";

    private final CreateFranchiseUseCase createFranchiseUseCase;
    private final AddBranchUseCase addBranchUseCase;
    private final AddProductUseCase addProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final UpdateProductStockUseCase updateProductStockUseCase;
    private final GetTopStockReportUseCase getTopStockReportUseCase;

    public Mono<ServerResponse> createFranchise(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(CreateFranchiseRequest.class)
                .onErrorMap(ServerWebInputException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                .map(request -> Franchise.builder().name(request.name()).build())
                .flatMap(createFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.FRANCHISE).build(franchise.id()))
                        .bodyValue(FranchiseResponse.builder().id(franchise.id()).name(franchise.name()).build()))
                .transform(this::handleDomainErrors);
    }

    public Mono<ServerResponse> addBranch(ServerRequest serverRequest) {
        return parseId(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID))
                .flatMap(franchiseId -> serverRequest.bodyToMono(CreateBranchRequest.class)
                        .onErrorMap(ServerWebInputException.class,
                                error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                        .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                        .map(request -> Branch.builder().franchiseId(franchiseId).name(request.name()).build()))
                .flatMap(addBranchUseCase::execute)
                .flatMap(branch -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.BRANCH)
                                .build(branch.franchiseId(), branch.id()))
                        .bodyValue(BranchResponse.builder().id(branch.id()).name(branch.name()).build()))
                .transform(this::handleDomainErrors);
    }

    public Mono<ServerResponse> addProduct(ServerRequest serverRequest) {
        return Mono.zip(parseId(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID)),
                        parseId(serverRequest.pathVariable(ApiPaths.BRANCH_ID)))
                .flatMap(ids -> serverRequest.bodyToMono(CreateProductRequest.class)
                        .onErrorMap(ServerWebInputException.class,
                                error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                        .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                        .map(request -> Product.builder().branchId(ids.getT2()).name(request.name())
                                .stock(request.stock()).build())
                        .flatMap(product -> addProductUseCase.execute(ids.getT1(), product)))
                .flatMap(product -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.PRODUCT)
                                .build(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID), product.branchId(),
                                        product.id()))
                        .bodyValue(toResponse(product)))
                .transform(this::handleDomainErrors);
    }

    public Mono<ServerResponse> deleteProduct(ServerRequest serverRequest) {
        return Mono.zip(parseId(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID)),
                        parseId(serverRequest.pathVariable(ApiPaths.BRANCH_ID)),
                        parseId(serverRequest.pathVariable(ApiPaths.ID)))
                .flatMap(ids -> deleteProductUseCase.execute(ids.getT1(), ids.getT2(), ids.getT3()))
                .then(Mono.defer(() -> ServerResponse.noContent().build()))
                .transform(this::handleDomainErrors);
    }

    public Mono<ServerResponse> updateProductStock(ServerRequest serverRequest) {
        return Mono.zip(parseId(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID)),
                        parseId(serverRequest.pathVariable(ApiPaths.BRANCH_ID)),
                        parseId(serverRequest.pathVariable(ApiPaths.ID)))
                .flatMap(ids -> serverRequest.bodyToMono(UpdateStockRequest.class)
                        .onErrorMap(ServerWebInputException.class,
                                error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                        .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                        .flatMap(request -> updateProductStockUseCase.execute(ids.getT1(), ids.getT2(), ids.getT3(),
                                request.stock())))
                .flatMap(product -> ServerResponse.ok().bodyValue(toResponse(product)))
                .transform(this::handleDomainErrors);
    }

    public Mono<ServerResponse> getTopStockProducts(ServerRequest serverRequest) {
        return parseId(serverRequest.pathVariable(ApiPaths.FRANCHISE_ID))
                .flatMap(getTopStockReportUseCase::execute)
                .flatMap(report -> Flux.fromIterable(report.branches())
                        .map(Handler::toResponse)
                        .collectList()
                        .map(branches -> new TopStockReportResponse(report.franchiseId(), branches)))
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .transform(this::handleDomainErrors);
    }

    private static BranchTopStockResponse toResponse(BranchTopStock branch) {
        return new BranchTopStockResponse(branch.branchId(), branch.branchName(),
                Optional.ofNullable(branch.topProduct()).map(Handler::toResponse).orElse(null));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.id(), product.name(), product.stock());
    }

    private static Mono<UUID> parseId(String value) {
        return Mono.fromCallable(() -> UUID.fromString(value))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error));
    }

    /** Technical failures are logged before mapping; business failures are expected outcomes and stay unlogged. */
    private Mono<ServerResponse> handleDomainErrors(Mono<ServerResponse> response) {
        return response
                .onErrorResume(TechnicalException.class, this::toTechnicalErrorResponse)
                .onErrorResume(DomainException.class, this::toErrorResponse);
    }

    private Mono<ServerResponse> toTechnicalErrorResponse(TechnicalException exception) {
        log.error("Technical failure: {}", exception.getCode(), exception);
        return toErrorResponse(exception);
    }

    private Mono<ServerResponse> toErrorResponse(DomainException exception) {
        return ServerResponse
                .status(statusOf(exception))
                .bodyValue(ErrorResponse.builder().code(exception.getCode())
                        .message(exception.getMessage()).build());
    }

    /** A technical failure is always 503, whatever its code name; a business failure keeps the code-name rules. */
    private static HttpStatus statusOf(DomainException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessStatusOf(businessException.getErrorCode());
        }
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    /** The code name sets the status: invalid values 400, not found 404, duplicates 409, others 422. */
    private static HttpStatus businessStatusOf(ErrorCode errorCode) {
        String name = errorCode.name();
        if (name.startsWith(INVALID_PREFIX)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (name.endsWith(NOT_FOUND_SUFFIX)) {
            return HttpStatus.NOT_FOUND;
        }
        if (name.startsWith(DUPLICATE_PREFIX)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
