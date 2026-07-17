package jatot.logging.spring.boot;

import jatot.logging.LogConfiguration;
import jatot.logging.LogLevel;
import jatot.logging.LogManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PostConstruct;

@AutoConfiguration
@EnableConfigurationProperties(JatotLoggingProperties.class)
public class JatotLoggingAutoConfiguration {

    private final JatotLoggingProperties properties;

    public JatotLoggingAutoConfiguration(JatotLoggingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        LogConfiguration config = new LogConfiguration();
        config.rootLevel = LogLevel.valueOf(properties.getRootLevel().toUpperCase());
        config.consoleEnabled = properties.isConsoleEnabled();
        config.fileEnabled = properties.isFileEnabled();
        config.filePath = properties.getFilePath();
        
        LogManager.configure(config);
    }
}
