package com.litness.optimizer;

import com.litness.optimizer.data.EnchantmentDatabase;
import com.litness.optimizer.data.UpdateChecker;
import com.litness.optimizer.gui.LitnessOptimizerScreen;
import com.litness.optimizer.tooltip.ApplicableItemsTooltipData;
import com.litness.optimizer.tooltip.ClientApplicableItemsTooltip;
import com.litness.optimizer.tooltip.LitnessTooltipHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public class LitnessOptimizerClient implements ClientModInitializer {
    public static final String MOD_ID = "litnessoptimizer";
    public static KeyMapping openGuiKeyMapping;

    @Override
    public void onInitializeClient() {
        System.out.println("[LitnessOptimizer] Inizializzazione mod client per Fabric 26.2...");

        // Controllo asincrono aggiornamenti in background
        UpdateChecker.checkForUpdatesAsync();

        // Registrazione del KeyMapping per il tasto 'N'
        openGuiKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.litnessoptimizer.open_gui",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_N,
            KeyMapping.Category.MISC
        ));

        // Listener per aprire la GUI quando viene premuto il tasto N
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKeyMapping.consumeClick()) {
                if (client.player != null) {
                    client.setScreenAndShow(new LitnessOptimizerScreen(Component.translatable("gui.litnessoptimizer.title")));
                }
            }
        });

        // Messaggio all'ingresso nel server per ricordare il tasto [N] e notificare aggiornamenti
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§6⚡ §eLitness Enchant Helper §7attivo! Premi §a[N] §7per aprire il menu."));
                UpdateChecker.notifyPlayerIfUpdateAvailable(client);
            }
        });

        // Pre-caricamento del database degli incantesimi
        EnchantmentDatabase.load();

        // Registrazione componente tooltip per rendering icone oggetti compatibili sui libri
        ClientTooltipComponentCallback.EVENT.register(tooltipComponent -> {
            if (tooltipComponent instanceof ApplicableItemsTooltipData data) {
                return new ClientApplicableItemsTooltip(data);
            }
            return null;
        });

        // Registrazione tooltip lore per mostrare automaticamente il livello massimo
        LitnessTooltipHandler.register();
    }
}
