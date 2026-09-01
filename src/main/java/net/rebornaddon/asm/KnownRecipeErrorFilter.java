package net.rebornaddon.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.HashSet;
import java.util.Set;

public final class KnownRecipeErrorFilter extends AbstractFilter {
    private static final KnownRecipeErrorFilter INSTANCE = new KnownRecipeErrorFilter();
    private static final Set<String> INSTALLED_LOGGERS = new HashSet<>();

    private KnownRecipeErrorFilter() {
    }

    public static synchronized void install() {
        installOn("FML");
        installOn("TickCentral");
    }

    @Override
    public Result filter(LogEvent event) {
        String loggerName = event == null ? "" : event.getLoggerName();
        String message = event == null || event.getMessage() == null
                ? "" : event.getMessage().getFormattedMessage();
        return isKnownHarmless(loggerName, message) ? Result.DENY : Result.NEUTRAL;
    }

    static boolean isKnownHarmless(String loggerName, String message) {
        if ("FML".equals(loggerName) && message.startsWith("Parsing error loading recipe ")
                && (message.contains("futuremc:else/soul_torch")
                || message.contains("nanexscherrybiome:cherrysaplingjarcraft")
                || message.contains("shinobiaddon:steel_ore_smelting"))) {
            return true;
        }
        return "TickCentral".equals(loggerName)
                && message.startsWith("Unable to get superclass as resource: net/minecraft/client/")
                && message.contains(" Do you have a broken installation? It is referenced in ");
    }

    private static void installOn(String loggerName) {
        if (INSTALLED_LOGGERS.contains(loggerName)) return;
        org.apache.logging.log4j.Logger logger = LogManager.getLogger(loggerName);
        if (logger instanceof Logger) {
            ((Logger) logger).addFilter(INSTANCE);
            INSTALLED_LOGGERS.add(loggerName);
        }
    }
}
