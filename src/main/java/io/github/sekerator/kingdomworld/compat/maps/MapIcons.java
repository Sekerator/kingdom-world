package io.github.sekerator.kingdomworld.compat.maps;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Icons and label markup of the web maps. The icons are the textures of the client maps, read from the mod's jar.
 */
public final class MapIcons {
    /** Side of the icons in pixels. */
    public static final int SIZE = 24;
    private static final Logger LOGGER = LogUtils.getLogger();

    private MapIcons() {
    }

    public static byte[] capital() {
        return read("capital.png");
    }

    public static byte[] caravan() {
        return read("caravan.png");
    }

    private static byte[] read(String name) {
        try {
            Path path = ModList.get().getModFileById(KingdomWorld.MOD_ID).getFile()
                    .findResource("assets", KingdomWorld.MOD_ID, "textures", "map", name);
            return Files.readAllBytes(path);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("KingdomWorld web maps: icon {} not found: {}", name, e.toString());
            return new byte[0];
        }
    }

    /**
     * The name of a kingdom written over its land: white letters on a band of the kingdom's color.
     */
    public static String labelHtml(String name, int color) {
        String background = String.format(Locale.ROOT, "rgba(%d,%d,%d,0.75)", (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        return "<div style=\"transform:translate(-50%,-50%);white-space:nowrap;font:bold 13px sans-serif;color:#fff;"
                + "text-shadow:0 0 2px #000,0 0 2px #000;padding:1px 6px;border-radius:3px;border:1px solid rgba(0,0,0,0.6);"
                + "background:" + background + ";pointer-events:none;\">" + MapTexts.html(name) + "</div>";
    }
}
