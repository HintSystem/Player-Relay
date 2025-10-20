package dev.hintsystem.playerrelay.config;

import com.google.gson.*;
import java.awt.Color;
import java.lang.reflect.Type;

public class ColorTypeAdapter implements JsonSerializer<Color>, JsonDeserializer<Color> {
    @Override
    public JsonElement serialize(Color src, Type typeOfSrc, JsonSerializationContext context) {
        // Format as #RRGGBBAA or #RRGGBB if alpha is 255
        String hex = String.format("#%02X%02X%02X", src.getRed(), src.getGreen(), src.getBlue());
        if (src.getAlpha() != 255) {
            hex += String.format("%02X", src.getAlpha());
        }
        return new JsonPrimitive(hex);
    }

    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        String hex = json.getAsString();

        // Remove # if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        // Parse based on length
        int r, g, b, a = 255;
        if (hex.length() == 6) {
            // #RRGGBB
            r = Integer.parseInt(hex.substring(0, 2), 16);
            g = Integer.parseInt(hex.substring(2, 4), 16);
            b = Integer.parseInt(hex.substring(4, 6), 16);
        } else if (hex.length() == 8) {
            // #RRGGBBAA
            r = Integer.parseInt(hex.substring(0, 2), 16);
            g = Integer.parseInt(hex.substring(2, 4), 16);
            b = Integer.parseInt(hex.substring(4, 6), 16);
            a = Integer.parseInt(hex.substring(6, 8), 16);
        } else {
            throw new JsonParseException("Invalid color format: " + json.getAsString());
        }

        return new Color(r, g, b, a);
    }
}
