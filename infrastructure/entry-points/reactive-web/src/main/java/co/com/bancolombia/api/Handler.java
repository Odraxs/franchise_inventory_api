package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.BranchResponse;
import co.com.bancolombia.api.dto.CreateBranchRequest;
import co.com.bancolombia.api.dto.CreateFranchiseRequest;
import co.com.bancolombia.api.dto.ErrorResponse;
import co.com.bancolombia.api.dto.CreateProductRequest;
import co.com.bancolombia.api.dto.FranchiseResponse;
import co.com.bancolombia.api.dto.ProductResponse;
import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.usecase.addbranch.AddBranchUseCase;
import co.com.bancolombia.usecase.addproduct.AddProductUseCase;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Handler {
    private static final String INVALID_PREFIX = "INVALID_";
    private static final String NOT_FOUND_SUFFIX = "_NOT_FOUND";
    private static final String DUPLICATE_PREFIX = "DUPLICATE_";

    private final CreateFranchiseUseCase createFranchiseUseCase;
    private final AddBranchUseCase addBranchUseCase;
    private final AddProductUseCase addProductUseCase;

    public Mono<ServerResponse> createFranchise(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(CreateFranchiseRequest.class)
                .onErrorMap(ServerWebInputException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                .map(request -> Franchise.builder().name(request.name()).build())
                .flatMap(createFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.FRANCHISE).build(franchise.id()))
                        .bodyValue(new FranchiseResponse(franchise.id(), franchise.name())))
                .onErrorResume(BusinessException.class, this::toErrorResponse);
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
                        .bodyValue(new BranchResponse(branch.id(), branch.name())))
                .onErrorResume(BusinessException.class, this::toErrorResponse);
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
                        .bodyValue(new ProductResponse(product.id(), product.name(), product.stock())))
                .onErrorResume(BusinessException.class, this::toErrorResponse);
    }

    private static Mono<UUID> parseId(String value) {
        return Mono.fromCallable(() -> UUID.fromString(value))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error));
    }

    private Mono<ServerResponse> toErrorResponse(BusinessException exception) {
        return ServerResponse
                .status(statusOf(exception.getErrorCode()))
                .bodyValue(new ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }

    /** The code name sets the status: invalid values 400, not found 404, duplicates 409, others 422. */
    private static HttpStatus statusOf(ErrorCode errorCode) {
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
