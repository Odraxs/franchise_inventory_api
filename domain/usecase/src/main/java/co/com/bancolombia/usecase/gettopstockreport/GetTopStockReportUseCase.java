package co.com.bancolombia.usecase.gettopstockreport;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.page.Page;
import co.com.bancolombia.model.product.TopStockReport;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetTopStockReportUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;
    private final ProductGateway productGateway;

    /** The gateway sorts and cuts the page, so the Branches keep the order it emits them in. */
    public Mono<TopStockReport> execute(UUID franchiseId, Page page) {
        return franchiseGateway.existsById(franchiseId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> Mono.zip(
                        productGateway.findTopStockByFranchiseId(franchiseId, page).collectList(),
                        branchGateway.countByFranchiseId(franchiseId))))
                .map(pageAndTotal -> TopStockReport.builder()
                        .franchiseId(franchiseId)
                        .branches(pageAndTotal.getT1())
                        .page(page.number())
                        .size(page.size())
                        .totalElements(pageAndTotal.getT2())
                        .totalPages(page.totalPages(pageAndTotal.getT2()))
                        .build());
    }
}
