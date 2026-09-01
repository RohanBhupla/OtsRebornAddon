package net.rebornaddon.integration;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PluginInvalidationBus {
    private static final ConcurrentMap<String, AtomicLong> REVISIONS =
            new ConcurrentHashMap<String, AtomicLong>();
    private static final AtomicLong ALL = new AtomicLong();

    private PluginInvalidationBus() {
    }

    public static void notifyChanged(String family, long revision) {
        String key = normalize(family);
        if (key.isEmpty() || "all".equals(key)) {
            advance(ALL, revision);
            return;
        }
        AtomicLong value = REVISIONS.get(key);
        if (value == null) {
            AtomicLong created = new AtomicLong();
            AtomicLong existing = REVISIONS.putIfAbsent(key, created);
            value = existing == null ? created : existing;
        }
        advance(value, revision);
    }

    public static long revision(String family) {
        AtomicLong value = REVISIONS.get(normalize(family));
        long local = value == null ? 0L : value.get();
        long global = ALL.get();
        return Math.max(local, global);
    }

    private static void advance(AtomicLong target, long revision) {
        long requested = revision > 0L ? revision : System.nanoTime();
        for (;;) {
            long current = target.get();
            long next = Math.max(current + 1L, requested);
            if (target.compareAndSet(current, next)) return;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }
}
