package net.rebornaddon.advancement.client;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.advancements.GuiScreenAdvancements;
import net.minecraft.client.multiplayer.ClientAdvancementManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Vanilla advancement screen with the external roots and children filtered out. */
@SideOnly(Side.CLIENT)
public final class GuiRebornAdvancements extends GuiScreenAdvancements {
    public GuiRebornAdvancements(ClientAdvancementManager manager) {
        super(manager);
    }

    @Override
    public void rootAdvancementAdded(Advancement advancement) {
        if (AdvancementClientGuard.isVisible(advancement)) {
            super.rootAdvancementAdded(advancement);
        }
    }

    @Override
    public void nonRootAdvancementAdded(Advancement advancement) {
        if (AdvancementClientGuard.isVisible(advancement)) {
            super.nonRootAdvancementAdded(advancement);
        }
    }

    @Override
    public void onUpdateAdvancementProgress(Advancement advancement, AdvancementProgress progress) {
        if (AdvancementClientGuard.isVisible(advancement)) {
            super.onUpdateAdvancementProgress(advancement, progress);
        }
    }

    @Override
    public void setSelectedTab(Advancement advancement) {
        if (AdvancementClientGuard.isVisible(advancement)) {
            super.setSelectedTab(advancement);
        }
    }
}
