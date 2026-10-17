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
 * Drops other code's log lines on a thread that's inside {@link JesLog#quietly}, sending its
 * warnings to the debug log instead. Errors are worth seeing, so the first of each kind still goes
 * to the game log, and only repeats of it go to the debug log. A kind is the logger and the text
 * without what's filled in, so the same problem in another place or with other values is a repeat.
 * Added to the whole logging context, so it sees every logger, and it does nothing on threads that
 * aren't in a quiet scope.
 */
final class QuietFilter extends AbstractFilter {
    private static final String OURS = "Just Enough Structures";
    /** Each logger's error messages let through so far, by their text without what's filled in. */
    private static final Set<String> ERRORS_SEEN = ConcurrentHashMap.newKeySet();
    /** After this many kinds, errors go to the debug log too, rather than fill the game log. */
    private static final int MOST_ERROR_KINDS = 500;
    /**
     * A number on its own, like a coordinate or a value in saved data, with its sign and an ending
     * like the f of 0.5f. Not one that's part of a name, like the 1 of house_1.
     */
    private static final Pattern VALUE = Pattern.compile("(?<![\\w./-])-?\\d+(?:\\.\\d+)*(?:[eE][-+]?\\d+)?[bBsSlLfFdD]?(?!\\w)");

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
     * Our own lines always go through, and so does the first error of each kind, by its logger and
     * its text without what's filled in. Anything else is dropped, after copying warnings and errors
     * to the debug log as they are. A null {@code kind} is a level check, like isErrorEnabled, with
     * nothing to copy, which errors pass so code that asks first still logs them.
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

    /** A message's text before it's filled in, or all of it for a message logged filled in already. */
    private static String kind(Message message) {
        if (message == null) {
            return null;
        }
        String format = message.getFormat();
        return format != null ? format : message.getFormattedMessage();
    }

    /**
     * A kind's text with each number on its own in it as {}, the way a message reads before it's
     * filled in. Much of the game fills a message in before logging it, which leaves nothing else to
     * tell the values from the words: old sculk data in a structure has the game log each sensor's
     * own position and readings, one line per sensor.
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
