package io.github.sekerator.kingdomworld.common.commerce;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.commerce.Trade;
import io.github.sekerator.kingdomworld.core.commerce.TradeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the trades of the shops of citizens from data packs, the folder {@code data/<namespace>/kingdomworld_trades/<id>.json};
 * a data pack adds a trade with a new file and changes one with a file of the same id. A file that cannot be read is left out
 * with a warning. Nothing is saved: the trades are read again whenever data packs reload.
 */
public final class TradeLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FOLDER = "kingdomworld_trades";

    public TradeLoader() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Trade> trades = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().getPath().toLowerCase(Locale.ROOT);
            try {
                trades.add(Trade.parse(id, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                LOGGER.warn("Trade {} cannot be read: {}", entry.getKey(), e.toString());
            }
        }
        TradeRegistry.set(trades.isEmpty() ? TradeRegistry.builtIn() : new TradeRegistry(trades));
        Workshops.invalidate();
        LOGGER.info("KingdomWorld read {} trades of citizens", TradeRegistry.current().all().size());
    }
}
