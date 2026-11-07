package io.github.sekerator.kingdomworld.common.construction;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.construction.BuildingRole;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Roles of buildings from data packs, read from {@code data/<namespace>/kingdomworld_roles/<role>.json}:
 *
 * <pre>
 * { "fallback_name": "Tavern", "requires": "none" | "bed" | "container", "params": { "mood": 3 }, "per_level": { "mood": 1 } }
 * </pre>
 *
 * The id of the role is the name of the file. A data role does nothing by itself in the world; templates name it, the
 * register keeps it with its state, and other mods read it through the API. Read again on {@code /reload}.
 */
public final class BuildingRoleLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FOLDER = "kingdomworld_roles";
    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();

    public BuildingRoleLoader() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<BuildingRole> roles = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().getPath().toLowerCase(Locale.ROOT);
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                BuildingRole.Requirement requirement = BuildingRole.Requirement.byId(json.has("requires") ? json.get("requires").getAsString() : "none");
                roles.add(new BuildingRole(id, requirement, BuildingTemplates.numbers(json, "params", entry.getKey()),
                        BuildingTemplates.numbers(json, "per_level", entry.getKey())));
                if (json.has("fallback_name")) {
                    names.put(id, json.get("fallback_name").getAsString());
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Building role {} cannot be read: {}", entry.getKey(), e.toString());
            }
        }
        BuildingRoles.replaceData(roles);
        NAMES.clear();
        NAMES.putAll(names);
        BuildingTemplates.tagsChanged();
        LOGGER.info("KingdomWorld loaded {} building role(s) from data packs", roles.size());
    }

    /**
     * The name of a role in the player's language: the mod's own key, with the name a data pack gave as the fallback.
     */
    public static Component name(String role) {
        String fallback = NAMES.getOrDefault(role, role);
        return Component.translatableWithFallback("kingdomworld.building.role." + role, fallback);
    }
}
