package net.rebornaddon.ranked.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rebornaddon.ranked.RankedLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class ArenaManager {

    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, Arena> arenas = new LinkedHashMap<>();

    // Admin in-progress builder state: adminUUID -> pos1/pos2. Never persisted -
    // this is just workflow state while someone is actively setting up an arena.
    private final Map<UUID, RankedLocation> pendingPos1 = new HashMap<>();
    private final Map<UUID, RankedLocation> pendingPos2 = new HashMap<>();

    public ArenaManager(File dataFolder) {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "arenas.json");
        load();
    }

    public void setPos1(UUID admin, RankedLocation loc) {
        pendingPos1.put(admin, loc);
    }

    public void setPos2(UUID admin, RankedLocation loc) {
        pendingPos2.put(admin, loc);
    }

    public Arena createFromPending(UUID admin, String id) {
        RankedLocation p1 = pendingPos1.get(admin);
        RankedLocation p2 = pendingPos2.get(admin);
        if (p1 == null || p2 == null) return null;

        Arena arena = new Arena(id);
        arena.corner1 = p1;
        arena.corner2 = p2;
        arenas.put(id, arena);
        save();
        return arena;
    }

    public boolean resizeFromPending(UUID admin, String id) {
        Arena arena = arenas.get(id);
        if (arena == null) return false;

        RankedLocation p1 = pendingPos1.get(admin);
        RankedLocation p2 = pendingPos2.get(admin);
        if (p1 == null || p2 == null) return false;

        arena.corner1 = p1;
        arena.corner2 = p2;
        save();
        return true;
    }

    public boolean addSpawn(String id, int team, RankedLocation loc) {
        Arena a = arenas.get(id);
        if (a == null) return false;
        a.addSpawn(team, loc);
        save();
        return true;
    }

    /** team == -1 clears both teams' spawns. */
    public boolean clearSpawns(String id, int team) {
        Arena a = arenas.get(id);
        if (a == null) return false;
        if (team == -1) a.clearAllSpawns();
        else a.clearSpawns(team);
        save();
        return true;
    }

    public Arena get(String id) {
        return arenas.get(id);
    }

    public boolean remove(String id) {
        boolean removed = arenas.remove(id) != null;
        if (removed) save();
        return removed;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public Arena findFreeArena(int teamSize) {
        for (Arena a : arenas.values()) {
            if (!a.inUse && a.isFullyConfigured() && a.canHost(teamSize)) {
                return a;
            }
        }
        return null;
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Arena>>(){}.getType();
            Map<String, Arena> loaded = gson.fromJson(reader, type);
            if (loaded != null) this.arenas = loaded;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(arenas, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
