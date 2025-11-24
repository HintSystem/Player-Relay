package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.PlayerRelayClient;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class SupportModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> PlayerRelayClient.config.createScreen(parent);
    }
}
