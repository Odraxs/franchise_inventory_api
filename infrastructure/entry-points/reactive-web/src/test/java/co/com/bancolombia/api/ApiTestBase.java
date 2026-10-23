package co.com.bancolombia.api;

import co.com.bancolombia.api.config.JacksonConfig;
import co.com.bancolombia.usecase.addbranch.AddBranchUseCase;
import co.com.bancolombia.usecase.addproduct.AddProductUseCase;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import co.com.bancolombia.usecase.deleteproduct.DeleteProductUseCase;
import co.com.bancolombia.usecase.gettopstockreport.GetTopStockReportUseCase;
import co.com.bancolombia.usecase.renamebranch.RenameBranchUseCase;
import co.com.bancolombia.usecase.renamefranchise.RenameFranchiseUseCase;
import co.com.bancolombia.usecase.renameproduct.RenameProductUseCase;
import co.com.bancolombia.usecase.updateproductstock.UpdateProductStockUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The full route table with every use case mocked, shared by the handler tests so they reuse one context. */
@ContextConfiguration(classes = {RouterRest.class, FranchiseHandler.class, BranchHandler.class,
        ProductHandler.class, TopStockHandler.class, JacksonConfig.class})
@WebFluxTest
public abstract class ApiTestBase {

    @Autowired
    protected WebTestClient webTestClient;

    @MockitoBean
    protected CreateFranchiseUseCase createFranchiseUseCase;

    @MockitoBean
    protected RenameFranchiseUseCase renameFranchiseUseCase;

    @MockitoBean
    protected AddBranchUseCase addBranchUseCase;

    @MockitoBean
    protected RenameBranchUseCase renameBranchUseCase;

    @MockitoBean
    protected AddProductUseCase addProductUseCase;

    @MockitoBean
    protected RenameProductUseCase renameProductUseCase;

    @MockitoBean
    protected DeleteProductUseCase deleteProductUseCase;

    @MockitoBean
    protected UpdateProductStockUseCase updateProductStockUseCase;

    @MockitoBean
    protected GetTopStockReportUseCase getTopStockReportUseCase;
}
