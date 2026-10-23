package co.com.bancolombia.usecase.gettopstockreport;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.BranchTopStock;
import co.com.bancolombia.model.product.TopStockReport;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.UUID;

@RequiredArgsConstructor
public class GetTopStockReportUseCase {

    private static final Comparator<BranchTopStock> BY_BRANCH_NAME_IGNORING_CASE =
            Comparator.comparing(BranchTopStock::branchName, String.CASE_INSENSITIVE_ORDER);

    private final FranchiseGateway franchiseGateway;
    private final ProductGateway productGateway;

    public Mono<TopStockReport> execute(UUID franchiseId) {
        return franchiseGateway.existsById(franchiseId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .thenMany(Flux.defer(() -> productGateway.findTopStockByFranchiseId(franchiseId)))
                .sort(BY_BRANCH_NAME_IGNORING_CASE)
                .collectList()
                .map(branches -> TopStockReport.builder().franchiseId(franchiseId).branches(branches).build());
    }
}
