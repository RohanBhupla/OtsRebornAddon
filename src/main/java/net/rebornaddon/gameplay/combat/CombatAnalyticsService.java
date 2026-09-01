package net.rebornaddon.gameplay.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.rebornaddon.content.entity.EntityMissionNpc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CombatAnalyticsService {
    public static final CombatAnalyticsService INSTANCE = new CombatAnalyticsService();
    private static final int MAX_HITS = 64;
    private static final long SESSION_IDLE_MILLIS = 10L * 60L * 1000L;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();

    private CombatAnalyticsService() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (event.getEntityLiving().world.isRemote || event.getAmount() <= 0.0F) return;
        Entity source = event.getSource().getTrueSource();
        long now = System.currentTimeMillis();
        if (source instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) source;
            String target = event.getEntityLiving().getName();
            record(player.getUniqueID(), true, event.getAmount(), label(player, event.getSource().damageType),
                    target, now);
        }
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            record(player.getUniqueID(), false, event.getAmount(), event.getSource().damageType,
                    source == null ? "Environment" : source.getName(), now);
        }
        if (event.getEntityLiving() instanceof EntityMissionNpc
                && ((EntityMissionNpc) event.getEntityLiving()).isTrainingTarget()) {
            float maximum = event.getEntityLiving().getHealth() - 1.0F;
            event.setAmount(Math.max(0.0F, Math.min(event.getAmount(), maximum)));
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || event.getEntityLiving().world.isRemote) return;
        Session session = session(((EntityPlayerMP) event.getEntityLiving()).getUniqueID());
        session.finishedAt = System.currentTimeMillis();
        session.finishedReason = "Defeated by " + (event.getSource().getTrueSource() == null
                ? event.getSource().damageType : event.getSource().getTrueSource().getName());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        synchronized (this) { sessions.remove(event.player.getUniqueID()); }
    }

    public synchronized String snapshot(EntityPlayerMP player) {
        Session session = session(player.getUniqueID());
        JsonObject root = new JsonObject();
        root.addProperty("available", true);
        root.addProperty("title", "Training and Combat Recap");
        root.addProperty("message", session.hits.isEmpty()
                ? "Damage measurements appear here after you attack or take damage."
                : "This recap is private to your character and keeps only the latest bounded combat session.");
        JsonArray metrics = new JsonArray();
        metric(metrics, "Damage dealt", decimal(session.dealt));
        metric(metrics, "Damage received", decimal(session.received));
        metric(metrics, "Hits dealt", Integer.toString(session.dealtHits));
        metric(metrics, "Hits received", Integer.toString(session.receivedHits));
        metric(metrics, "Largest hit", decimal(session.largestHit));
        metric(metrics, "Damage / second", decimal(damagePerSecond(session)));
        metric(metrics, "Fight length", duration(session));
        root.add("metrics", metrics);
        JsonArray entries = new JsonArray();
        List<SourceTotal> totals = new ArrayList<SourceTotal>(session.sources.values());
        Collections.sort(totals, new Comparator<SourceTotal>() {
            @Override
            public int compare(SourceTotal left, SourceTotal right) {
                return Double.compare(right.damage, left.damage);
            }
        });
        for (int i = 0; i < totals.size() && i < 6; i++) {
            SourceTotal total = totals.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("id", "source:" + i);
            row.addProperty("title", total.source);
            row.addProperty("subtitle", decimal(total.damage) + " damage across " + total.hits + " hits");
            row.addProperty("detail", decimal(total.damage / Math.max(1, total.hits)) + " average damage per hit");
            entries.add(row);
        }
        int skip = Math.max(0, session.hits.size() - 20);
        int index = 0;
        for (Hit hit : session.hits) {
            if (index++ < skip) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", Long.toString(hit.at));
            row.addProperty("title", (hit.dealt ? "Dealt " : "Received ") + decimal(hit.amount));
            row.addProperty("subtitle", hit.source);
            row.addProperty("detail", hit.target);
            entries.add(row);
        }
        root.add("entries", entries);
        return root.toString();
    }

    public synchronized void reset(UUID player) {
        if (player != null) sessions.remove(player);
    }

    private synchronized void record(UUID id, boolean dealt, float amount, String source,
                                     String target, long now) {
        Session session = session(id);
        if (now - session.lastAt > SESSION_IDLE_MILLIS) session.clear(now);
        session.lastAt = now;
        if (dealt) {
            session.dealt += amount;
            session.dealtHits++;
        } else {
            session.received += amount;
            session.receivedHits++;
        }
        session.largestHit = Math.max(session.largestHit, amount);
        if (dealt) {
            SourceTotal total = session.sources.get(source);
            if (total == null && session.sources.size() < 32) {
                total = new SourceTotal(source);
                session.sources.put(source, total);
            }
            if (total != null) {
                total.damage += amount;
                total.hits++;
            }
        }
        session.hits.addLast(new Hit(dealt, amount, source, target, now));
        while (session.hits.size() > MAX_HITS) session.hits.removeFirst();
    }

    private synchronized Session session(UUID id) {
        Session value = sessions.get(id);
        if (value == null) {
            value = new Session();
            value.clear(System.currentTimeMillis());
            sessions.put(id, value);
        }
        return value;
    }

    private static String label(EntityPlayerMP player, String fallback) {
        ItemStack held = player.getHeldItemMainhand();
        return held == null || held.isEmpty() ? fallback : held.getDisplayName();
    }

    private static void metric(JsonArray values, String label, String value) {
        JsonObject metric = new JsonObject();
        metric.addProperty("label", label);
        metric.addProperty("value", value);
        values.add(metric);
    }

    private static String duration(Session session) {
        long end = session.finishedAt > 0L ? session.finishedAt : session.lastAt;
        return Math.max(0L, end - session.startedAt) / 1000L + "s";
    }

    private static double damagePerSecond(Session session) {
        long end = session.finishedAt > 0L ? session.finishedAt : session.lastAt;
        return session.dealt / Math.max(1.0D, (end - session.startedAt) / 1000.0D);
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class Session {
        private final Deque<Hit> hits = new ArrayDeque<Hit>();
        private long startedAt;
        private long lastAt;
        private long finishedAt;
        private String finishedReason = "";
        private double dealt;
        private double received;
        private double largestHit;
        private int dealtHits;
        private int receivedHits;
        private final Map<String, SourceTotal> sources = new LinkedHashMap<String, SourceTotal>();

        private void clear(long now) {
            hits.clear();
            startedAt = now;
            lastAt = now;
            finishedAt = 0L;
            finishedReason = "";
            dealt = 0.0D;
            received = 0.0D;
            largestHit = 0.0D;
            dealtHits = 0;
            receivedHits = 0;
            sources.clear();
        }
    }

    private static final class SourceTotal {
        private final String source;
        private double damage;
        private int hits;

        private SourceTotal(String source) {
            this.source = source == null || source.isEmpty() ? "Unknown" : source;
        }
    }

    private static final class Hit {
        private final boolean dealt;
        private final float amount;
        private final String source;
        private final String target;
        private final long at;

        private Hit(boolean dealt, float amount, String source, String target, long at) {
            this.dealt = dealt;
            this.amount = amount;
            this.source = source == null ? "Unknown" : source;
            this.target = target == null ? "Unknown" : target;
            this.at = at;
        }
    }
}
