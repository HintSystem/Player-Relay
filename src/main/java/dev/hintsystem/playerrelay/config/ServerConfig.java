package dev.hintsystem.playerrelay.config;

import dev.hintsystem.playerrelay.PlayerRelay;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class ServerConfig extends CommonConfig {
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(PlayerRelay.MOD_ID + "-server.json");
    public static final ServerConfig DEFAULTS = new ServerConfig();

    public Path getConfigPath() { return PATH; }

    @Override
    public ServerConfig getDefaults() { return DEFAULTS; }
}
