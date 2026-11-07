package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Texts about the decisions of the rulers of the world: decrees as the chronicle writes them, needs, reasons for declining a deal
 * and the attitude of a ruler to a player. They are built from plain values, so the client builds them the same way.
 */
public final class RulerTexts {
    private static final String KEY = "kingdomworld.ruler.";
    private static final String COUNT = "kingdomworld.count.";
    /** Roles with a word of their own in decrees; any other is named by its building. */
    public static final List<String> ROLES = List.of("house", "warehouse", "workshop", "market", "tower", "well", "barracks", "mage_tower", "tavern");
    /** Positions a ruler appoints to. */
    public static final List<String> POSITIONS = List.of("treasurer");

    private RulerTexts() {
    }

    /**
     * A decree as a deed of the ruler, without its name: "ordered a tavern built".
     */
    public static MutableComponent decree(RulerDecision decision, Gender gender) {
        String key = KEY + "decree." + decision.kind().id() + (gender == Gender.FEMALE ? ".female" : "");
        String name = decision.targetName();
        return switch (decision.kind()) {
            case BUILD, UPGRADE -> Component.translatable(key, role(decision.subject(), decision.kind() == RulerDecision.Kind.BUILD
                    ? decision.target() : decision.targetName()));
            case CLAIM -> Component.translatable(key, chunks(decision.amount()));
            case HIRE, DISMISS -> Component.translatable(key, soldiers(decision.amount()));
            case ORDER, SELL, BUY -> Component.translatable(key, item(decision.subject()));
            case TAX -> Component.translatable(key, money(decision.amount()), decision.extra() / 10);
            case WAGE -> Component.translatable(key, decision.amount());
            case ENTRY_FEE, BOUNTY, FESTIVAL -> Component.translatable(key, money(decision.amount()));
            case CONTRACT -> Component.translatable(key, money(decision.extra()));
            case OFFER -> Component.translatable(key + (decision.amount() >= 0 ? "" : ".buy"), Component.literal(name),
                    item(decision.subject()));
            case ACCEPT, ROAD, TOWN, APPOINT -> Component.translatable(key, Component.literal(name));
            case DECLINE -> Component.translatable(key, Component.literal(name), reason(decision.subject()));
            case RESEARCH -> Component.translatable(key, Component.translatable("kingdomworld.magic.research." + decision.subject()));
            default -> Component.translatable(key);
        };
    }

    /**
     * A decree as the chronicle writes it: "Queen Dobroslava ordered a tavern built".
     */
    public static MutableComponent chronicle(MutableComponent ruler, RulerDecision decision, Gender gender) {
        return Component.translatable(KEY + "chronicle", ruler, decree(decision, gender));
    }

    /**
     * A building of a role in decrees: a word of its own for the known roles, its template's name for the others.
     */
    public static MutableComponent role(String role, String template) {
        if (ROLES.contains(role)) {
            return Component.translatable(KEY + "role." + role);
        }
        ResourceLocation id = ResourceLocation.tryParse(template == null ? "" : template);
        Component name = id == null ? Component.literal(role) : Component.translatable("kingdomworld.building." + id.getPath());
        return Component.translatable(KEY + "role.other", name);
    }

    public static MutableComponent item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        if (item == null || item == Items.AIR) {
            return Component.literal(id);
        }
        return Component.translatable(item.getDescriptionId());
    }

    public static MutableComponent money(long amount) {
        return MoneyTexts.amount(amount, EconomyRules.DEFAULT.units());
    }

    public static MutableComponent reason(String reason) {
        return Component.translatable(KEY + "reason." + (RulerMind.REASONS.contains(reason) ? reason : RulerMind.NOT_NEEDED));
    }

    /**
     * What a kingdom lacks: "bread", "iron ingot x20", "saves for barracks".
     */
    public static MutableComponent need(RulerMemory.Need need) {
        String key = KEY + "need." + need.kind().id();
        return switch (need.kind()) {
            case GOODS, FOOD -> need.amount() > 0 ? Component.translatable(key + ".count", item(need.subject()), need.amount())
                    : Component.translatable(key, item(need.subject()));
            case BUILDING -> Component.translatable(key, role(need.subject(), ""));
            case MONEY -> Component.translatable(key, money(need.amount()));
            case SOLDIERS -> Component.translatable(key, soldiers(need.amount()));
            default -> Component.translatable(key);
        };
    }

    /**
     * The needs of a kingdom joined in one line, or that it lacks nothing.
     */
    public static MutableComponent needs(List<RulerMemory.Need> needs) {
        if (needs.isEmpty()) {
            return Component.translatable(KEY + "need.none");
        }
        MutableComponent line = need(needs.get(0));
        for (int i = 1; i < needs.size(); i++) {
            line = Component.translatable(KEY + "list", line, need(needs.get(i)));
        }
        return line;
    }

    public static MutableComponent attitude(int value) {
        return Component.translatable(KEY + "attitude." + RulerMind.Attitude.of(value).id());
    }

    public static MutableComponent chunks(long count) {
        return Component.translatable(COUNT + "chunks." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent soldiers(long count) {
        return Component.translatable(COUNT + "soldiers." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent decrees(long count) {
        return Component.translatable(COUNT + "decrees." + Plurals.form(count).suffix(), count);
    }
}
