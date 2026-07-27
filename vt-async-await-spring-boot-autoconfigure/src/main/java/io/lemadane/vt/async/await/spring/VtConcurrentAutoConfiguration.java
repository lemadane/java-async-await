package io.lemadane.vt.async.await.spring;

import io.lemadane.vt.async.await.AsyncRuntime;
import io.lemadane.vt.async.await.TaskDecorator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring Boot auto-configuration for {@link AsyncRuntime}.
 */
@AutoConfiguration
@ConditionalOnClass(AsyncRuntime.class)
@EnableConfigurationProperties(VtConcurrentProperties.class)
@ConditionalOnProperty(prefix = "vt.concurrent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VtConcurrentAutoConfiguration {

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, VtConcurrentAutoConfiguration.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static TaskDecorator loadDecorator(String decoratorClassName, String guardClassName) {
        if (isClassPresent(guardClassName)) {
            try {
                return (TaskDecorator) Class.forName(decoratorClassName)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Composes all auto-configured TaskDecorators in deterministic order.
     *
     * @return the composite task decorator
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator defaultTaskDecorator() {
        List<TaskDecorator> decorators = new java.util.ArrayList<>();

        // 1. Locale context propagation
        TaskDecorator locale = loadDecorator(
                "io.lemadane.vt.async.await.spring.LocaleTaskDecorator",
                "org.springframework.context.i18n.LocaleContextHolder");
        if (locale != null) {
            decorators.add(locale);
        }

        // 2. MDC context propagation
        TaskDecorator mdc = loadDecorator(
                "io.lemadane.vt.async.await.spring.MdcTaskDecorator",
                "org.slf4j.MDC");
        if (mdc != null) {
            decorators.add(mdc);
        }

        // 3. Request context propagation
        TaskDecorator request = loadDecorator(
                "io.lemadane.vt.async.await.spring.RequestContextTaskDecorator",
                "org.springframework.web.context.request.RequestContextHolder");
        if (request != null) {
            decorators.add(request);
        }

        // 4. Security context propagation
        TaskDecorator security = loadDecorator(
                "io.lemadane.vt.async.await.spring.SecurityTaskDecorator",
                "org.springframework.security.core.context.SecurityContextHolder");
        if (security != null) {
            decorators.add(security);
        }

        if (decorators.isEmpty()) {
            return TaskDecorator.identity();
        }

        return operation -> {
            Runnable current = operation;
            for (int i = decorators.size() - 1; i >= 0; i--) {
                current = decorators.get(i).decorate(current);
            }
            return current;
        };
    }

    /**
     * Auto-configures an {@link AsyncRuntime} bean if no existing bean is present.
     *
     * @param properties configuration properties
     * @param taskDecorator the resolved TaskDecorator (either custom or default composite)
     * @return the configured AsyncRuntime bean
     */
    @Bean
    @ConditionalOnMissingBean(AsyncRuntime.class)
    public AsyncRuntime asyncRuntime(VtConcurrentProperties properties, TaskDecorator taskDecorator) {
        return AsyncRuntime.builder()
                .threadNamePrefix(properties.getThreadNamePrefix())
                .taskDecorator(taskDecorator)
                .build();
    }
}
