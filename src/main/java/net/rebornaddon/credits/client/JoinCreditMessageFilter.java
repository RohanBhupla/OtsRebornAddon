package net.rebornaddon.credits.client;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;

public final class JoinCreditMessageFilter {
    public static final JoinCreditMessageFilter INSTANCE = new JoinCreditMessageFilter();

    private JoinCreditMessageFilter() {
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.getMessage() == null) {
            return;
        }

        String text = event.getMessage().getUnformattedText();
        if (shouldHide(text)) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldHide(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        if (text.contains("join the decentstudio discord server")
                || text.contains("naruto add-ons")
                || text.contains("discord.gg/hdfbhpep")
                || text.contains("modified version made by spring")
                || text.contains("support me i have paypal")
                || text.contains("suport me i have paypal")
                || text.contains("discord.gg/mjecvv8ftn")
                || text.contains("you are using 'cursemark addon'")
                || text.contains("made by spring. any bugs or questions join my discord")) {
            return true;
        }

        return text.equals("enjoy it.") || (text.contains("join discord") && text.contains("click here"));
    }
}
