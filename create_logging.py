import os

files = {
    "jatot-logging/build.gradle": """plugins {
    id 'java-library'
}
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
tasks.named('test') {
    useJUnitPlatform()
}
""",
    "jatot-logging/src/main/java/jatot/logging/Logging.java": """package jatot.logging;
import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Logging {}
""",
    "jatot-logging/src/main/java/jatot/logging/LogLevel.java": """package jatot.logging;

public enum LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, OFF
}
""",
    "jatot-logging/src/main/java/jatot/logging/Logger.java": """package jatot.logging;

public interface Logger {
    String name();
    void trace(String message);
    void trace(String message, Throwable throwable);
    void debug(String message);
    void debug(String message, Throwable throwable);
    void info(String message);
    void info(String message, Throwable throwable);
    void warn(String message);
    void warn(String message, Throwable throwable);
    void error(String message);
    void error(String message, Throwable throwable);
    
    boolean isTraceEnabled();
    boolean isDebugEnabled();
    boolean isInfoEnabled();
    boolean isWarnEnabled();
    boolean isErrorEnabled();
}
""",
    "jatot-logging/src/main/java/jatot/logging/LogEvent.java": """package jatot.logging;
import java.time.Instant;

public record LogEvent(Instant timestamp, LogLevel level, String loggerName, String threadName, String message, Throwable throwable) {}
""",
    "jatot-logging/src/main/java/jatot/logging/LogAppender.java": """package jatot.logging;

public interface LogAppender extends AutoCloseable {
    void append(LogEvent event);
    @Override void close();
}
""",
    "jatot-logging/src/main/java/jatot/logging/LogFormatter.java": """package jatot.logging;

public interface LogFormatter {
    String format(LogEvent event);
}
""",
    "jatot-logging/src/main/java/jatot/logging/ConsoleAppender.java": """package jatot.logging;

public class ConsoleAppender implements LogAppender {
    private final LogFormatter formatter;
    
    public ConsoleAppender(LogFormatter formatter) {
        this.formatter = formatter;
    }
    
    @Override
    public void append(LogEvent event) {
        if (event.level() == LogLevel.ERROR || event.level() == LogLevel.WARN) {
            System.err.println(formatter.format(event));
            if (event.throwable() != null) event.throwable().printStackTrace(System.err);
        } else {
            System.out.println(formatter.format(event));
            if (event.throwable() != null) event.throwable().printStackTrace(System.out);
        }
    }
    
    @Override
    public void close() {}
}
""",
    "jatot-logging/src/main/java/jatot/logging/FileAppender.java": """package jatot.logging;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileAppender implements LogAppender {
    private final LogFormatter formatter;
    private PrintWriter writer;
    
    public FileAppender(String filePath, LogFormatter formatter) {
        this.formatter = formatter;
        try {
            this.writer = new PrintWriter(new FileWriter(filePath, true), true);
        } catch (IOException e) {
            System.err.println("Failed to open file appender: " + e.getMessage());
        }
    }
    
    @Override
    public void append(LogEvent event) {
        if (writer != null) {
            writer.println(formatter.format(event));
            if (event.throwable() != null) {
                event.throwable().printStackTrace(writer);
            }
            writer.flush();
        }
    }
    
    @Override
    public void close() {
        if (writer != null) {
            writer.close();
        }
    }
}
""",
    "jatot-logging/src/main/java/jatot/logging/DefaultLogFormatter.java": """package jatot.logging;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DefaultLogFormatter implements LogFormatter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogEvent event) {
        return String.format("%s %s [%s] %s - %s",
                FORMATTER.format(event.timestamp()),
                event.level(),
                event.threadName(),
                event.loggerName(),
                event.message());
    }
}
""",
    "jatot-logging/src/main/java/jatot/logging/LogConfiguration.java": """package jatot.logging;
import java.util.Properties;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LogConfiguration {
    public LogLevel rootLevel = LogLevel.INFO;
    public boolean consoleEnabled = true;
    public boolean fileEnabled = false;
    public String filePath = "application.log";
    
    public static LogConfiguration load() {
        LogConfiguration config = new LogConfiguration();
        Properties props = new Properties();
        try {
            if (Files.exists(Paths.get("jatot-logging.properties"))) {
                try (InputStream is = new FileInputStream("jatot-logging.properties")) {
                    props.load(is);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        
        if (props.containsKey("rootLevel")) {
            config.rootLevel = LogLevel.valueOf(props.getProperty("rootLevel").toUpperCase());
        }
        if (props.containsKey("consoleEnabled")) {
            config.consoleEnabled = Boolean.parseBoolean(props.getProperty("consoleEnabled"));
        }
        if (props.containsKey("fileEnabled")) {
            config.fileEnabled = Boolean.parseBoolean(props.getProperty("fileEnabled"));
        }
        if (props.containsKey("filePath")) {
            config.filePath = props.getProperty("filePath");
        }
        return config;
    }
}
""",
    "jatot-logging/src/main/java/jatot/logging/LoggerImpl.java": """package jatot.logging;
import java.time.Instant;

public class LoggerImpl implements Logger {
    private final String name;

    public LoggerImpl(String name) {
        this.name = name;
    }

    @Override public String name() { return name; }
    
    private void log(LogLevel level, String message, Throwable t) {
        if (!isEnabled(level)) return;
        LogEvent event = new LogEvent(Instant.now(), level, name, Thread.currentThread().getName(), message, t);
        LogManager.dispatch(event);
    }

    private boolean isEnabled(LogLevel level) {
        return level.ordinal() >= LogManager.configuration().rootLevel.ordinal();
    }

    @Override public void trace(String message) { log(LogLevel.TRACE, message, null); }
    @Override public void trace(String message, Throwable throwable) { log(LogLevel.TRACE, message, throwable); }
    @Override public void debug(String message) { log(LogLevel.DEBUG, message, null); }
    @Override public void debug(String message, Throwable throwable) { log(LogLevel.DEBUG, message, throwable); }
    @Override public void info(String message) { log(LogLevel.INFO, message, null); }
    @Override public void info(String message, Throwable throwable) { log(LogLevel.INFO, message, throwable); }
    @Override public void warn(String message) { log(LogLevel.WARN, message, null); }
    @Override public void warn(String message, Throwable throwable) { log(LogLevel.WARN, message, throwable); }
    @Override public void error(String message) { log(LogLevel.ERROR, message, null); }
    @Override public void error(String message, Throwable throwable) { log(LogLevel.ERROR, message, throwable); }
    
    @Override public boolean isTraceEnabled() { return isEnabled(LogLevel.TRACE); }
    @Override public boolean isDebugEnabled() { return isEnabled(LogLevel.DEBUG); }
    @Override public boolean isInfoEnabled() { return isEnabled(LogLevel.INFO); }
    @Override public boolean isWarnEnabled() { return isEnabled(LogLevel.WARN); }
    @Override public boolean isErrorEnabled() { return isEnabled(LogLevel.ERROR); }
}
""",
    "jatot-logging/src/main/java/jatot/logging/LogManager.java": """package jatot.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LogManager {
    private static LogConfiguration config;
    private static final Map<String, Logger> loggers = new ConcurrentHashMap<>();
    private static final List<LogAppender> appenders = new ArrayList<>();
    
    static {
        reload();
    }
    
    public static Logger getLogger(Class<?> type) {
        return getLogger(type.getName());
    }
    
    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerImpl::new);
    }
    
    public static void configure(LogConfiguration configuration) {
        shutdown();
        config = configuration;
        LogFormatter formatter = new DefaultLogFormatter();
        if (config.consoleEnabled) {
            appenders.add(new ConsoleAppender(formatter));
        }
        if (config.fileEnabled) {
            appenders.add(new FileAppender(config.filePath, formatter));
        }
    }
    
    public static LogConfiguration configuration() {
        return config;
    }
    
    public static void reload() {
        configure(LogConfiguration.load());
    }
    
    public static void shutdown() {
        for (LogAppender appender : appenders) {
            appender.close();
        }
        appenders.clear();
    }
    
    static void dispatch(LogEvent event) {
        for (LogAppender appender : appenders) {
            appender.append(event);
        }
    }
}
""",
    "jatot-logging/src/test/java/jatot/logging/LoggerTest.java": """package jatot.logging;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LoggerTest {
    @Test
    public void testLogger() {
        Logger logger = LogManager.getLogger(LoggerTest.class);
        logger.info("Test message");
        assertNotNull(logger);
    }
}
""",
    "jatot-logging-spring-boot-starter/build.gradle": """plugins {
    id 'java-library'
}
dependencies {
    api project(':jatot-logging')
    implementation 'org.springframework.boot:spring-boot-autoconfigure:3.1.2'
    implementation 'org.springframework.boot:spring-boot-starter:3.1.2'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor:3.1.2'
    testImplementation 'org.springframework.boot:spring-boot-starter-test:3.1.2'
}
tasks.named('test') {
    useJUnitPlatform()
}
""",
    "jatot-logging-spring-boot-starter/src/main/java/jatot/logging/spring/boot/JatotLoggingProperties.java": """package jatot.logging.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jatot.logging")
public class JatotLoggingProperties {
    private String rootLevel = "INFO";
    private boolean consoleEnabled = true;
    private boolean fileEnabled = false;
    private String filePath = "application.log";
    
    public String getRootLevel() { return rootLevel; }
    public void setRootLevel(String rootLevel) { this.rootLevel = rootLevel; }
    
    public boolean isConsoleEnabled() { return consoleEnabled; }
    public void setConsoleEnabled(boolean consoleEnabled) { this.consoleEnabled = consoleEnabled; }
    
    public boolean isFileEnabled() { return fileEnabled; }
    public void setFileEnabled(boolean fileEnabled) { this.fileEnabled = fileEnabled; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
""",
    "jatot-logging-spring-boot-starter/src/main/java/jatot/logging/spring/boot/JatotLoggingAutoConfiguration.java": """package jatot.logging.spring.boot;

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
    
    @Bean
    public JatotLoggingProperties jatotLoggingProperties() {
        return properties;
    }
}
""",
    "jatot-logging-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports": """jatot.logging.spring.boot.JatotLoggingAutoConfiguration
""",
    "jatot-logging-spring-boot-starter/src/test/java/jatot/logging/spring/boot/JatotLoggingAutoConfigurationTest.java": """package jatot.logging.spring.boot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JatotLoggingAutoConfigurationTest.TestConfig.class)
public class JatotLoggingAutoConfigurationTest {

    @Configuration
    @Import(JatotLoggingAutoConfiguration.class)
    static class TestConfig {}

    @Test
    public void testContextLoads() {
        assertNotNull(jatot.logging.LogManager.configuration());
    }
}
"""
}

base_dir = "/home/lem/Projects/java/jatot"
for file_path, content in files.items():
    full_path = os.path.join(base_dir, file_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w") as f:
        f.write(content)

print("Files created successfully.")
