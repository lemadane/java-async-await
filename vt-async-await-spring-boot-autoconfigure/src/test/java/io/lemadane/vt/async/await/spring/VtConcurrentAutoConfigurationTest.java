package io.lemadane.vt.async.await.spring;

import io.lemadane.vt.async.await.AsyncRuntime;
import io.lemadane.vt.async.await.Task;
import io.lemadane.vt.async.await.TaskDecorator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class VtConcurrentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VtConcurrentAutoConfiguration.class));

    @Test
    void createsDefaultAsyncRuntimeBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AsyncRuntime.class);
            AsyncRuntime runtime = context.getBean(AsyncRuntime.class);
            Task<String> task = runtime.async(() -> Thread.currentThread().getName());
            assertThat(runtime.await(task)).startsWith("vt-task-");
        });
    }

    @Test
    void respectsCustomThreadNamePrefixProperty() {
        contextRunner
                .withPropertyValues("vt.concurrent.thread-name-prefix=custom-prefix-")
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncRuntime.class);
                    AsyncRuntime runtime = context.getBean(AsyncRuntime.class);
                    Task<String> task = runtime.async(() -> Thread.currentThread().getName());
                    assertThat(runtime.await(task)).startsWith("custom-prefix-");
                });
    }

    @Test
    void appliesCustomTaskDecoratorBean() {
        contextRunner
                .withUserConfiguration(CustomDecoratorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncRuntime.class);
                    AsyncRuntime runtime = context.getBean(AsyncRuntime.class);
                    Task<String> task = runtime.async(() -> CustomDecoratorConfiguration.TEST_VALUE.get());
                    assertThat(runtime.await(task)).isEqualTo("decorated");
                });
    }

    @Test
    void backsOffWhenCustomAsyncRuntimeBeanProvided() {
        contextRunner
                .withUserConfiguration(CustomRuntimeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncRuntime.class);
                    assertThat(context.getBean(AsyncRuntime.class)).isSameAs(CustomRuntimeConfiguration.CUSTOM_RUNTIME);
                });
    }

    @Test
    void canBeDisabledViaProperty() {
        contextRunner
                .withPropertyValues("vt.concurrent.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AsyncRuntime.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoratorConfiguration {
        static final ThreadLocal<String> TEST_VALUE = new ThreadLocal<>();

        @Bean
        TaskDecorator taskDecorator() {
            return runnable -> () -> {
                TEST_VALUE.set("decorated");
                try {
                    runnable.run();
                } finally {
                    TEST_VALUE.remove();
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRuntimeConfiguration {
        static final AsyncRuntime CUSTOM_RUNTIME = AsyncRuntime.builder().threadNamePrefix("custom-").build();

        @Bean
        AsyncRuntime asyncRuntime() {
            return CUSTOM_RUNTIME;
        }
    }
}
