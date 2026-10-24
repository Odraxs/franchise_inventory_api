package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.BranchTopStockResponse;
import co.com.bancolombia.api.dto.TopStockReportResponse;
import co.com.bancolombia.model.page.Page;
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
        return Mono.zip(RequestReader.parseId(serverRequest, ApiPaths.FRANCHISE_ID), parsePage(serverRequest))
                .flatMap(idAndPage -> getTopStockReportUseCase.execute(idAndPage.getT1(), idAndPage.getT2()))
                .flatMap(report -> Flux.fromIterable(report.branches())
                        .map(BranchTopStockResponse::from)
                        .collectList()
                        .map(branches -> TopStockReportResponse.builder()
                                .franchiseId(report.franchiseId())
                                .branches(branches)
                                .page(report.page())
                                .size(report.size())
                                .totalElements(report.totalElements())
                                .totalPages(report.totalPages())
                                .build()))
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .transform(ErrorResponses::handle);
    }

    /** Missing parameters take the defaults; Page rejects a negative page or a size out of range. */
    private static Mono<Page> parsePage(ServerRequest serverRequest) {
        return Mono.zip(RequestReader.parseIntQueryParam(serverRequest, ApiPaths.PAGE, Page.DEFAULT_NUMBER),
                        RequestReader.parseIntQueryParam(serverRequest, ApiPaths.SIZE, Page.DEFAULT_SIZE))
                .map(numberAndSize -> Page.builder().number(numberAndSize.getT1()).size(numberAndSize.getT2())
                        .build());
    }
}
