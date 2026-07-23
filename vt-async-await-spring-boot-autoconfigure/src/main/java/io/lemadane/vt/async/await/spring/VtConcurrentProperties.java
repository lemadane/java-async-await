package io.lemadane.vt.async.await.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for virtual-thread concurrent async/await integration.
 */
@ConfigurationProperties("vt.concurrent")
public final class VtConcurrentProperties {

    /**
     * Whether to enable virtual-thread concurrent auto-configuration.
     */
    private boolean enabled = true;

    /**
     * Virtual thread name prefix for the auto-configured AsyncRuntime.
     */
    private String threadNamePrefix = "vt-task-";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
}
