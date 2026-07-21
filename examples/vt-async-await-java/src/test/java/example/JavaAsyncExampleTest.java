package example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaAsyncExampleTest {

    @Test
    void verifiesJavaAsyncExample() {
        assertEquals("Profile(C123) + Orders(C123)", JavaAsyncExample.loadCustomerData("C123"));
        assertEquals("Customer(C123) + Points(100)", JavaAsyncExample.loadWithCustomRuntime("C123"));
    }
}
