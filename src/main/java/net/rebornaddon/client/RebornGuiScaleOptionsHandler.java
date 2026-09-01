package net.rebornaddon.client;

import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class RebornGuiScaleOptionsHandler {
    public static final RebornGuiScaleOptionsHandler INSTANCE = new RebornGuiScaleOptionsHandler();

    private static final Logger LOGGER = LogManager.getLogger("RebornAddon GUI Scale");
    private static final int SLIDER_ID = 92840;
    private static final int INSERT_ROW = 4;

    private Field rowListField;
    private Field optionsField;
    private boolean reflectionUnavailable;

    private RebornGuiScaleOptionsHandler() {
    }

    @SubscribeEvent
    public void onVideoSettingsInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiVideoSettings) || reflectionUnavailable) {
            return;
        }

        try {
            GuiOptionsRowList rowList = getRowList((GuiVideoSettings) event.getGui());
            if (rowList == null) {
                return;
            }

            List<GuiOptionsRowList.Row> rows = getRows(rowList);
            String label = ClientLocalization.format("gui.rebornaddon.scale", "Reborn GUI Scale") + ": ";
            PersistedScaleSlider slider = new PersistedScaleSlider(SLIDER_ID,
                    event.getGui().width / 2 - 155, 0, 150, 20, label);
            rows.add(Math.min(INSERT_ROW, rows.size()), new GuiOptionsRowList.Row(slider, null));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reflectionUnavailable = true;
            LOGGER.warn("Unable to add the Reborn GUI scale control", exception);
        }
    }

    private GuiOptionsRowList getRowList(GuiVideoSettings screen) throws IllegalAccessException {
        if (rowListField == null) {
            rowListField = ReflectionHelper.findField(GuiVideoSettings.class,
                    "optionsRowList", "field_146501_h");
        }
        Object value = rowListField.get(screen);
        return value instanceof GuiOptionsRowList ? (GuiOptionsRowList) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<GuiOptionsRowList.Row> getRows(GuiOptionsRowList rowList) throws IllegalAccessException {
        if (optionsField == null) {
            optionsField = ReflectionHelper.findField(GuiOptionsRowList.class,
                    "options", "field_148184_k");
        }
        return (List<GuiOptionsRowList.Row>) optionsField.get(rowList);
    }

    private static final class PersistedScaleSlider extends GuiSlider {
        private PersistedScaleSlider(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label, "%",
                    RebornGuiScale.MIN_PERCENT, RebornGuiScale.MAX_PERCENT,
                    RebornGuiScale.getPercent(), false, true,
                    slider -> RebornGuiScale.setPercent(slider.getValueInt()));
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            super.mouseReleased(mouseX, mouseY);
            RebornGuiScale.save();
        }
    }
}
