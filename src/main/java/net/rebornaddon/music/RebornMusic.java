package net.rebornaddon.music;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.RebornAddonMod;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID)
public final class RebornMusic {

    public static final Track[] TRACKS = new Track[]{
            track("wind", "Wind"),
            track("haruka_kanata", "Haruka Kanata"),
            track("fake", "Fake!"),
            track("strong_and_strike", "Strong and Strike"),
            track("go", "GO!!!"),
            track("sadness_and_sorrow", "Sadness and Sorrow"),
            track("girei", "Girei"),
            track("lovers", "Lovers"),
            track("hotaru_no_hikari", "Hotaru no Hikari"),
            track("sign", "Sign"),
            track("silhouette", "Silhouette"),
            track("heroes_come_back", "Hero's Come Back!!"),
            track("blue_bird", "Blue Bird"),
            track("samidare", "Samidare"),
            track("sasuke_theme", "Sasuke's Theme"),
            track("nine_tail_demon_fox", "Nine-Tail Demon Fox"),
            track("toumei_datta_sekai", "Toumei Datta Sekai")
    };

    private RebornMusic() {
    }

    @SubscribeEvent
    public static void registerSoundEvents(RegistryEvent.Register<SoundEvent> event) {
        for (Track track : TRACKS) {
            event.getRegistry().register(track.sound);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        for (Track track : TRACKS) {
            event.getRegistry().register(track.record);
        }
    }

    private static Track track(String id, String title) {
        ResourceLocation soundName = new ResourceLocation(RebornAddonMod.MODID, "music." + id);
        SoundEvent sound = new SoundEvent(soundName).setRegistryName(soundName);
        return new Track(id, title, sound, new RebornMusicRecord(id, sound));
    }

    public static final class Track {
        public final String id;
        public final String title;
        public final SoundEvent sound;
        public final RebornMusicRecord record;

        private Track(String id, String title, SoundEvent sound, RebornMusicRecord record) {
            this.id = id;
            this.title = title;
            this.sound = sound;
            this.record = record;
        }
    }
}
