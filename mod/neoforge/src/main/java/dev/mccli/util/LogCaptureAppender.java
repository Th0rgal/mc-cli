package dev.mccli.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j appender that forwards log entries into LogCapture.
 */
public class LogCaptureAppender extends AbstractAppender {
    public LogCaptureAppender() {
        super("McCliLogCapture", null, PatternLayout.createDefaultLayout(), false, null);
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) {
            return;
        }
        String level = event.getLevel() != null ? event.getLevel().name().toLowerCase() : Level.INFO.name().toLowerCase();
        String logger = event.getLoggerName() != null ? event.getLoggerName() : "unknown";
        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
        LogCapture.addEntry(level, logger, message);
    }
}
