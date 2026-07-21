package io.lemonade.vtasyncawait.spring;

import io.lemonade.vtasyncawait.AsyncRuntime;
import io.lemonade.vtasyncawait.TaskDecorator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for {@link AsyncRuntime}.
 */
@AutoConfiguration
@ConditionalOnClass(AsyncRuntime.class)
@EnableConfigurationProperties(VtConcurrentProperties.class)
@ConditionalOnProperty(prefix = "vt.concurrent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VtConcurrentAutoConfiguration {

    /**
     * Auto-configures an {@link AsyncRuntime} bean if no existing bean is present.
     *
     * @param properties configuration properties
     * @param taskDecoratorProvider optional custom TaskDecorator provider
     * @return the configured AsyncRuntime bean
     */
    @Bean
    @ConditionalOnMissingBean(AsyncRuntime.class)
    public AsyncRuntime asyncRuntime(VtConcurrentProperties properties, ObjectProvider<TaskDecorator> taskDecoratorProvider) {
        AsyncRuntime.Builder builder = AsyncRuntime.builder()
                .threadNamePrefix(properties.getThreadNamePrefix());

        TaskDecorator decorator = taskDecoratorProvider.getIfAvailable();
        if (decorator != null) {
            builder.taskDecorator(decorator);
        }

        return builder.build();
    }
}
