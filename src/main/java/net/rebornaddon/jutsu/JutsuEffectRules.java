package net.rebornaddon.jutsu;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JutsuEffectRules {
    public static final int INHERIT = -1;
    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    private JutsuEffectRules() {
    }

    public static Map<String, Rule> parse(String encoded) {
        Map<String, Rule> result = new LinkedHashMap<String, Rule>();
        if (encoded == null || encoded.trim().isEmpty()) return result;
        for (String entry : encoded.split(";")) {
            String[] fields = entry.trim().split("\\|", -1);
            if (fields.length != 4) continue;
            try {
                String id = fields[0].trim().toLowerCase(java.util.Locale.ROOT);
                Potion potion = ForgeRegistries.POTIONS.getValue(new ResourceLocation(id));
                int state = Integer.parseInt(fields[1]);
                double duration = Double.parseDouble(fields[2]);
                int amplifier = Integer.parseInt(fields[3]);
                if (potion == null || !potion.isBadEffect() || state < INHERIT || state > ENABLED
                        || !Double.isFinite(duration) || duration < 0.0D || duration > 1000.0D
                        || amplifier < -255 || amplifier > 255) continue;
                result.put(id, new Rule(state, duration, amplifier));
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    public static String normalize(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (String entry : encoded.split(";")) {
            String[] fields = entry.trim().split("\\|", -1);
            if (fields.length != 4) {
                throw new IllegalArgumentException("Effect rules contain an invalid entry.");
            }
            String id = fields[0].trim().toLowerCase(java.util.Locale.ROOT);
            Potion potion;
            int state;
            double duration;
            int amplifier;
            try {
                potion = ForgeRegistries.POTIONS.getValue(new ResourceLocation(id));
                state = Integer.parseInt(fields[1].trim());
                duration = Double.parseDouble(fields[2].trim());
                amplifier = Integer.parseInt(fields[3].trim());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Effect rules contain an invalid value.");
            }
            if (potion == null || !potion.isBadEffect()) {
                throw new IllegalArgumentException("Unknown harmful effect: " + id);
            }
            if (state < INHERIT || state > ENABLED) {
                throw new IllegalArgumentException("Effect behavior must inherit, enable, or disable.");
            }
            if (!Double.isFinite(duration) || duration < 0.0D || duration > 1000.0D) {
                throw new IllegalArgumentException("Effect duration multiplier must be from 0 to 1000.");
            }
            if (amplifier < -255 || amplifier > 255) {
                throw new IllegalArgumentException("Effect strength adjustment must be from -255 to 255.");
            }
            if (++count > 64) throw new IllegalArgumentException("At most 64 effect rules are allowed.");
            if (result.length() > 0) result.append(';');
            result.append(id).append('|').append(state).append('|')
                    .append(BigDecimal.valueOf(duration).stripTrailingZeros().toPlainString())
                    .append('|').append(amplifier);
        }
        if (result.length() > 8192) throw new IllegalArgumentException("The effect rules are too long.");
        return result.toString();
    }

    public static Map<String, Rule> immutable(String encoded) {
        return Collections.unmodifiableMap(parse(encoded));
    }

    public static final class Rule {
        private final int state;
        private final double durationMultiplier;
        private final int amplifierAdjustment;

        public Rule(int state, double durationMultiplier, int amplifierAdjustment) {
            this.state = state;
            this.durationMultiplier = durationMultiplier;
            this.amplifierAdjustment = amplifierAdjustment;
        }

        public int state() { return state; }
        public double durationMultiplier() { return durationMultiplier; }
        public int amplifierAdjustment() { return amplifierAdjustment; }
    }
}
