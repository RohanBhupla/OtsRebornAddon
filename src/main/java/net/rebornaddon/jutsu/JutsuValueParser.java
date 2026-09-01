package net.rebornaddon.jutsu;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JutsuValueParser {
    private static final Pattern DURATION_PART = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([smh])");
    private JutsuValueParser() {
    }

    public static String normalize(String property, String input) {
        if ("enabled".equals(property) || "negative-effects-enabled".equals(property)) {
            if (!"true".equalsIgnoreCase(input) && !"false".equalsIgnoreCase(input)) {
                throw new IllegalArgumentException("Enabled must be true or false.");
            }
            return input.toLowerCase(Locale.ROOT);
        }
        if (isDuration(property)) {
            if ("instant".equalsIgnoreCase(input)) {
                if (!"charge-time".equals(property)) {
                    throw new IllegalArgumentException("Instant is only valid for charge time.");
                }
                return "0";
            }
            return Long.toString(parseDuration(input));
        }
        if ("negative-effect-rules".equals(property)) {
            return JutsuEffectRules.normalize(input);
        }
        if ("additional-negative-effects".equals(property)) {
            String value = input == null ? "" : input.trim().replace(';', ',');
            if (value.length() > 1000) throw new IllegalArgumentException("The effect list is too long.");
            return value;
        }
        if ("effect-amplifier-adjustment".equals(property)) {
            try {
                int number = Integer.parseInt(input);
                if (number < -255 || number > 255) throw new NumberFormatException();
                return Integer.toString(number);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Effect amplifier adjustment must be from -255 to 255.");
            }
        }
        try {
            double number = Double.parseDouble(input);
            if (!Double.isFinite(number) || number < 0.0D) {
                throw new NumberFormatException();
            }
            return new BigDecimal(input).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Value must be a finite non-negative number.");
        }
    }

    public static String display(String property, String value) {
        if (value == null || value.length() == 0) {
            return "mod default";
        }
        if (isDuration(property)) {
            try {
                long ticks = Long.parseLong(value);
                return "charge-time".equals(property) && ticks == 0L
                        ? "instant" : JutsuRuntimeHooks.formatDuration(ticks);
            } catch (NumberFormatException ignored) {
            }
        }
        return value;
    }

    public static String editable(String property, String value) {
        if (value == null || value.length() == 0 || !isDuration(property)) {
            return value == null ? "" : value;
        }
        try {
            long ticks = Long.parseLong(value);
            if ("charge-time".equals(property) && ticks == 0L) {
                return "instant";
            }
            long hours = ticks / 72000L;
            long remainingTicks = ticks % 72000L;
            long minutes = remainingTicks / 1200L;
            remainingTicks %= 1200L;
            StringBuilder result = new StringBuilder();
            if (hours > 0L) result.append(hours).append('h');
            if (minutes > 0L) result.append(minutes).append('m');
            if (remainingTicks > 0L || result.length() == 0) {
                result.append(seconds(remainingTicks)).append('s');
            }
            return result.toString();
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static boolean isDuration(String property) {
        return "cooldown".equals(property) || "charge-time".equals(property)
                || "max-duration".equals(property);
    }

    private static long parseDuration(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        if (value.length() == 0) {
            throw new IllegalArgumentException("Duration is required, for example 20s, 5m, or 1h30m.");
        }
        BigDecimal ticks = BigDecimal.ZERO;
        Matcher matcher = DURATION_PART.matcher(value);
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException("Use a duration such as 20s, 1.5s, 5m, or 1h30m.");
            }
            BigDecimal multiplier = "h".equals(matcher.group(2)) ? BigDecimal.valueOf(72000L)
                    : "m".equals(matcher.group(2)) ? BigDecimal.valueOf(1200L)
                    : BigDecimal.valueOf(20L);
            ticks = ticks.add(new BigDecimal(matcher.group(1)).multiply(multiplier));
            end = matcher.end();
        }
        if (end == 0 || end != value.length()) {
            throw new IllegalArgumentException("Use a duration such as 20s, 5m, 2h, or 1h30m.");
        }
        try {
            return ticks.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Duration is too large.");
        }
    }

    public static long parseEffectDuration(String input) {
        return parseDuration(input);
    }

    static String seconds(long ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20L))
                .stripTrailingZeros().toPlainString();
    }
}
