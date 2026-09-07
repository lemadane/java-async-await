package example.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vt.async.await.AsyncTask;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Test
    void loadsDashboardConcurrently() {
        DashboardService.Dashboard dashboard = dashboardService.load("cust-100");
        assertNotNull(dashboard);
        assertEquals("Customer(cust-100)", dashboard.customer());
        assertEquals(2, dashboard.orders().size());
    }

    @Test
    void verifiesScopedLambdaInSpringBoot() {
        String result = dashboardService.loadScopedLambda("cust-100");
        assertEquals("Customer(cust-100)", result);
    }

    @Test
    void verifiesOnCompleteCallbackInSpringBoot() {
        AtomicBoolean called = new AtomicBoolean(false);
        String result = dashboardService.loadWithCallback("cust-100", () -> called.set(true));
        assertEquals("Customer(cust-100)", result);
        assertTrue(called.get());
    }

    @Test
    void verifiesLazyTaskInSpringBoot() {
        String result = dashboardService.loadLazyTask("cust-100");
        assertEquals("Customer(cust-100)", result);
    }

    @Test
    void verifiesAllCombinatorInSpringBoot() {
        List<String> results = dashboardService.loadAll("cust-100");
        assertEquals(Arrays.asList("Customer(cust-100)", "Orders(cust-100)"), results);
    }

    @Test
    void verifiesRaceCombinatorInSpringBoot() {
        String recommendation = dashboardService.loadFastestRecommendation();
        assertEquals("Fast Rules Engine", recommendation);
    }

    @Test
    void verifiesAnyCombinatorInSpringBoot() {
        String paymentResult = dashboardService.loadAnyPaymentGateway();
        assertEquals("Secondary Gateway OK", paymentResult);
    }

    @Test
    void verifiesAllSettledCombinatorInSpringBoot() {
        Collection<AsyncTask<? extends String>> auditTasks = dashboardService.loadSettledAudit();
        assertEquals(2, auditTasks.size());
    }

    @Test
    void verifiesAwaitAndCancelInSpringBoot() {
        String customer = dashboardService.loadWithTimeoutAndCancel("cust-100", Duration.ofSeconds(2));
        assertEquals("Customer(cust-100)", customer);
    }
}
