package net.rebornaddon.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public final class KnownRecipeErrorFilter extends AbstractFilter {
    private static final KnownRecipeErrorFilter INSTANCE = new KnownRecipeErrorFilter();
    private static boolean installed;

    private KnownRecipeErrorFilter() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        org.apache.logging.log4j.Logger logger = LogManager.getLogger("FML");
        if (logger instanceof Logger) {
            ((Logger) logger).addFilter(INSTANCE);
            installed = true;
        }
    }

    @Override
    public Result filter(LogEvent event) {
        String message = event == null || event.getMessage() == null
                ? "" : event.getMessage().getFormattedMessage();
        if (message.startsWith("Parsing error loading recipe ")
                && (message.contains("futuremc:else/soul_torch")
                || message.contains("nanexscherrybiome:cherrysaplingjarcraft")
                || message.contains("shinobiaddon:steel_ore_smelting"))) {
            return Result.DENY;
        }
        return Result.NEUTRAL;
    }
}
