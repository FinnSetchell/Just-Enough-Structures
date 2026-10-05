package com.finndog.justenoughstructures;

import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * Drops other code's log lines on a thread that's inside {@link JesLog#quietly}, sending its
 * warnings and errors to the debug log instead. Added to the whole logging context, so it sees
 * every logger, and it does nothing on threads that aren't in a quiet scope.
 */
final class QuietFilter extends AbstractFilter {
    private static final String OURS = "Just Enough Structures";

    private QuietFilter() {
    }

    static void install() {
        // Log4j reads the context's filter on every call, so loggers that already exist pick it up too.
        if (LogManager.getContext(false) instanceof LoggerContext context) {
            QuietFilter filter = new QuietFilter();
            filter.start();
            // First, as the first filter with an answer decides: Forge's own starts by letting every
            // warning through, which would leave this nothing to quiet.
            Configuration config = context.getConfiguration();
            Filter existing = config.getFilter();
            List<Filter> others = existing instanceof CompositeFilter composite ? List.of(composite.getFiltersArray())
                    : existing == null ? List.of() : List.of(existing);
            others.forEach(config::removeFilter);
            config.addFilter(filter);
            others.forEach(config::addFilter);
        }
    }

    @Override
    public Result filter(LogEvent event) {
        if (!JesLog.quiet()) {
            return Result.NEUTRAL;
        }
        try {
            Message message = event.getMessage();
            return decide(event.getLoggerName(), event.getLevel(), message == null ? null : message.getFormattedMessage(), event.getThrown());
        } catch (Throwable t) {
            return Result.NEUTRAL;
        }
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        if (!JesLog.quiet()) {
            return Result.NEUTRAL;
        }
        try {
            if (msg == null || !loud(level) || !JesLog.enabled()) {
                return decide(logger.getName(), level, null, null);
            }
            ParameterizedMessage message = new ParameterizedMessage(msg, params);
            return decide(logger.getName(), level, message.getFormattedMessage(), message.getThrowable());
        } catch (Throwable t) {
            return Result.NEUTRAL;
        }
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        if (!JesLog.quiet()) {
            return Result.NEUTRAL;
        }
        try {
            return decide(logger.getName(), level, msg == null ? null : String.valueOf(msg), t);
        } catch (Throwable e) {
            return Result.NEUTRAL;
        }
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (!JesLog.quiet()) {
            return Result.NEUTRAL;
        }
        try {
            if (msg == null) {
                return decide(logger.getName(), level, null, t);
            }
            return decide(logger.getName(), level, msg.getFormattedMessage(), t != null ? t : msg.getThrowable());
        } catch (Throwable e) {
            return Result.NEUTRAL;
        }
    }

    /**
     * Our own lines always go through. Anything else is dropped, after copying warnings and errors
     * to the debug log. A null message is a level check, like isWarnEnabled, with nothing to copy.
     */
    private static Result decide(String loggerName, Level level, String text, Throwable thrown) {
        if (OURS.equals(loggerName)) {
            return Result.NEUTRAL;
        }
        if (text != null && loud(level) && JesLog.enabled()) {
            JesLog.debug("[{}] ({}) {}", level, loggerName, text, thrown);
        }
        return Result.DENY;
    }

    private static boolean loud(Level level) {
        return level != null && level.isMoreSpecificThan(Level.WARN);
    }
}
