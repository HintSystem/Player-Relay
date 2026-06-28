package dev.hintsystem.playerrelay.logging.handler;

import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;

import org.slf4j.spi.LoggingEventBuilder;

public class ConsoleOutputHandler implements LogHandler {
    private final org.slf4j.Logger slf4jLogger;

    public ConsoleOutputHandler(org.slf4j.Logger logger) {
        this.slf4jLogger = logger;
    }

    @Override
    public void handle(AbstractLogEvent event) {
        if (!slf4jLogger.isEnabledForLevel(event.getLevel())) return;

        logToSlf4j(event);
    }

    private void logToSlf4j(AbstractLogEvent event) {
        String logMessage = formatForSlf4j(event);

        Throwable cause = event.getCause();

        LoggingEventBuilder eventBuilder = slf4jLogger.atLevel(event.getLevel())
            .setMessage(logMessage);

        if (cause != null) eventBuilder = eventBuilder.setCause(cause);

        eventBuilder.log();
    }

    private String formatForSlf4j(AbstractLogEvent event) {
        StringBuilder sb = new StringBuilder();

        if (event.getTitle() != null) {
            sb.append(event.getTitle()).append(": ");
        }

        String message = event.getMessage();
        if (message == null && event.getCause() != null) {
            message = event.getCause().getMessage();
        }
        sb.append(message);

        if (message != null && event.getCause() != null) {
            sb.append(" | Exception: ").append(event.getCause().getMessage());
        }

        return sb.toString();
    }
}
