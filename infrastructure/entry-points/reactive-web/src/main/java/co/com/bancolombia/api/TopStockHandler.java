package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.BranchTopStockResponse;
import co.com.bancolombia.api.dto.TopStockReportResponse;
import co.com.bancolombia.usecase.gettopstockreport.GetTopStockReportUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TopStockHandler {
    private final GetTopStockReportUseCase getTopStockReportUseCase;

    public Mono<ServerResponse> getTopStockProducts(ServerRequest serverRequest) {
        return RequestReader.parseId(serverRequest, ApiPaths.FRANCHISE_ID)
                .flatMap(getTopStockReportUseCase::execute)
                .flatMap(report -> Flux.fromIterable(report.branches())
                        .map(BranchTopStockResponse::from)
                        .collectList()
                        .map(branches -> TopStockReportResponse.builder()
                                .franchiseId(report.franchiseId())
                                .branches(branches)
                                .build()))
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .transform(ErrorResponses::handle);
    }
}
