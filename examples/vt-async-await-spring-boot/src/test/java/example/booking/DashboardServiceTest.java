package example.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
