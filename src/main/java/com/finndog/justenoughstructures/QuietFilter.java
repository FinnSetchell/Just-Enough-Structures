package com.finndog.justenoughstructures;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
 * Drops other code's log lines on threads inside {@link JesLog#quietly} and copies its warnings to
 * the debug log. The first error of each kind still reaches the game log; repeats go to the debug
 * log. A kind is the logger name plus the message format with numbers replaced, so the same problem
 * in another place or with other values is a repeat. Installed on the whole logging context, so it
 * sees every logger; threads outside a quiet scope are untouched.
 */
final class QuietFilter extends AbstractFilter {
    private static final String OURS = "Just Enough Structures";
    /** Error kinds already let through: logger name plus message format with numbers replaced. */
    private static final Set<String> ERRORS_SEEN = ConcurrentHashMap.newKeySet();
    /** After this many kinds, errors go to the debug log too, so they can't flood the game log. */
    private static final int MOST_ERROR_KINDS = 500;
    /**
     * A standalone number, like a coordinate or a value in saved data, with its sign and a suffix like
     * the f of 0.5f. Not one that's part of a name, like the 1 of house_1.
     */
    private static final Pattern VALUE = Pattern.compile("(?<![\\w./-])-?\\d+(?:\\.\\d+)*(?:[eE][-+]?\\d+)?[bBsSlLfFdD]?(?!\\w)");

    private QuietFilter() {
    }

    static void install() {
        // Log4j reads the context's filter on every call, so loggers that already exist pick it up too.
        if (LogManager.getContext(false) instanceof LoggerContext context) {
            QuietFilter filter = new QuietFilter();
            filter.start();
            // Put first, because the first filter that accepts or denies decides. Forge's filter
            // accepts every warning, which would leave nothing to quiet.
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
            return decide(event.getLoggerName(), event.getLevel(), kind(message), () -> message.getFormattedMessage(), event.getThrown());
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
            Throwable thrown = params != null && params.length > 0 && params[params.length - 1] instanceof Throwable t ? t : null;
            return decide(logger.getName(), level, msg, () -> new ParameterizedMessage(msg, params).getFormattedMessage(), thrown);
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
            String text = msg == null ? null : String.valueOf(msg);
            return decide(logger.getName(), level, text, () -> text, t);
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
            return decide(logger.getName(), level, kind(msg), () -> msg.getFormattedMessage(), t != null || msg == null ? t : msg.getThrowable());
        } catch (Throwable e) {
            return Result.NEUTRAL;
        }
    }

    /**
     * JES's lines and the first error of each kind pass. Everything else is denied, with warnings and
     * errors copied to the debug log. A null {@code kind} is a level check like isErrorEnabled; errors
     * pass it, so code that checks first still logs them.
     */
    private static Result decide(String loggerName, Level level, String kind, Supplier<String> text, Throwable thrown) {
        if (OURS.equals(loggerName)) {
            return Result.NEUTRAL;
        }
        if (outOfMemory(thrown)) {
            JesLog.sawOutOfMemory();
        }
        if (serious(level) && (kind == null || firstOfKind(loggerName + "|" + withoutValues(kind)))) {
            return Result.NEUTRAL;
        }
        if (kind != null && loud(level) && JesLog.enabled()) {
            JesLog.debug("[{}] ({}) {}", level, loggerName, text.get(), thrown);
        }
        return Result.DENY;
    }

    private static boolean firstOfKind(String key) {
        return ERRORS_SEEN.size() < MOST_ERROR_KINDS && ERRORS_SEEN.add(key);
    }

    /** The message's format string, or its formatted text when it has none. */
    private static String kind(Message message) {
        if (message == null) {
            return null;
        }
        String format = message.getFormat();
        return format != null ? format : message.getFormattedMessage();
    }

    /**
     * Replaces each standalone number in a kind with {}, as in a message format. Much of the game
     * formats a message before logging it, which leaves no other way to tell values from words: old
     * sculk data in a structure makes the game log each sensor's position and readings, one line per
     * sensor.
     */
    private static String withoutValues(String kind) {
        return VALUE.matcher(kind).replaceAll("{}");
    }

    private static boolean outOfMemory(Throwable thrown) {
        for (int depth = 0; thrown != null && depth < 10; depth++, thrown = thrown.getCause()) {
            if (thrown instanceof OutOfMemoryError) {
                return true;
            }
        }
        return false;
    }

    private static boolean loud(Level level) {
        return level != null && level.isMoreSpecificThan(Level.WARN);
    }

    private static boolean serious(Level level) {
        return level != null && level.isMoreSpecificThan(Level.ERROR);
    }
}
