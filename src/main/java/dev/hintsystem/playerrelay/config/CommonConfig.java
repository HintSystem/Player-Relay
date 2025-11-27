package dev.hintsystem.playerrelay.config;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.NetworkConfig;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;

import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class CommonConfig extends NetworkConfig {
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(PlayerRelay.MOD_ID + ".json");
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    public static final CommonConfig DEFAULTS = new CommonConfig();

    public double minPlayerMove = 0.2;

    /** @return false if only changed fields are saved to file */
    public boolean defaultsAreSerialized() { return false; }

    /** Get the default instance for comparison during serialization */
    public CommonConfig getDefaults() { return DEFAULTS; }

    public Path getConfigPath() { return PATH; }

    /** Get the GSON instance to use for serialization */
    protected Gson getGson() { return GSON; }

    public void serialize() {
        JsonObject root = new JsonObject();

        try {
            CommonConfig defaults = getDefaults();

            for (Field f : this.getClass().getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    Object current = f.get(this);
                    Object def = f.get(defaults);

                    if (defaultsAreSerialized() || !Objects.equals(current, def)) {
                        root.add(f.getName(), getGson().toJsonTree(current));
                    }
                }
            }

            Files.writeString(getConfigPath(), getGson().toJson(root));
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to serialize config at {}", getConfigPath(), e);
        }
    }

    public void deserialize() {
        Path path = getConfigPath();

        if (!Files.exists(path)) {
            PlayerRelay.LOGGER.info("Config file not found at {}, using default", path);
            serialize();
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

            for (Field f : this.getClass().getFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && root.has(f.getName())) {
                    Object val = getGson().fromJson(root.get(f.getName()), f.getType());
                    f.set(this, val);
                }
            }
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to deserialize config from {}", path, e);
        }
    }
}
