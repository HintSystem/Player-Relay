package dev.hintsystem.playerrelay.logging;

import org.slf4j.event.Level;

public class ConsoleLogHandler implements LogHandler {
    private final org.slf4j.Logger slf4jLogger;

    public ConsoleLogHandler(org.slf4j.Logger logger) {
        this.slf4jLogger = logger;
    }

    @Override
    public void handle(LogEvent event) {
        logToSlf4j(event);
    }

    private void logToSlf4j(LogEvent event) {
        String logMessage = formatForSlf4j(event);

        Throwable exception = event.getException();
        boolean hasException = exception != null;

        switch (event.getLevel()) {
            case TRACE -> {
                if (hasException) slf4jLogger.trace(logMessage, exception);
                else slf4jLogger.trace(logMessage);
            }
            case DEBUG -> {
                if (hasException) slf4jLogger.debug(logMessage, exception);
                else slf4jLogger.debug(logMessage);
            }
            case INFO -> {
                if (hasException) slf4jLogger.info(logMessage, exception);
                else slf4jLogger.info(logMessage);
            }
            case WARN -> {
                if (hasException) slf4jLogger.warn(logMessage, exception);
                else slf4jLogger.warn(logMessage);
            }
            case ERROR -> {
                if (hasException) slf4jLogger.error(logMessage, exception);
                else slf4jLogger.error(logMessage);
            }
        }
    }

    private String formatForSlf4j(LogEvent event) {
        StringBuilder sb = new StringBuilder();

        if (event.getTitle() != null) {
            sb.append(event.getTitle()).append(": ");
        }

        String message = event.getMessage();
        if (message == null && event.getException() != null) {
            message = event.getException().getMessage();
        }
        sb.append(message);

        if (message != null && event.getException() != null) {
            sb.append(" | Exception: ").append(event.getException().getMessage());
        }

        if (event.getDescription() != null) {
            sb.append(" | Details: ").append(event.getDescription());
        }

//        if (!event.getContext().isEmpty()) {
//            sb.append(" | Context: ").append(event.getContext());
//        }

        return sb.toString();
    }

    @Override
    public boolean isEnabled(Level level) {
        return slf4jLogger.isEnabledForLevel(level);
    }
}
