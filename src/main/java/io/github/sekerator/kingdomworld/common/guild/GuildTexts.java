package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Texts of the guild made of translation keys, with the right plural forms in both languages: what a contract asks for,
 * numbers of monsters, points and crystals, ranks. Works on both sides.
 */
public final class GuildTexts {
    private static final String COUNT = "kingdomworld.count.";
    private static final String CONTRACT = "kingdomworld.guild.contract.";

    private GuildTexts() {
    }

    /**
     * A number of monsters: «5 монстров», "5 monsters".
     */
    public static MutableComponent monsters(long count) {
        return Component.translatable(COUNT + "monsters." + Plurals.form(count).suffix(), count);
    }

    /**
     * A number of bosses: «1 босс».
     */
    public static MutableComponent bosses(long count) {
        return Component.translatable(COUNT + "bosses." + Plurals.form(count).suffix(), count);
    }

    /**
     * A number of guild points: «21 очко», "21 points".
     */
    public static MutableComponent points(long count) {
        return Component.translatable(COUNT + "points." + Plurals.form(count).suffix(), count);
    }

    /**
     * A number of crystals: «3 кристалла».
     */
    public static MutableComponent crystals(long count) {
        return Component.translatable(COUNT + "crystals." + Plurals.form(count).suffix(), count);
    }

    /**
     * A rank as it is written: «C».
     */
    public static MutableComponent rank(Rank rank) {
        return Component.literal(rank.label()).withStyle(style -> style.withColor(MonsterCrystalItem.color(rank)));
    }

    @SuppressWarnings("deprecation")
    public static EntityType<?> monsterType(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.ENTITY_TYPES.containsKey(key)) {
            return null;
        }
        return ForgeRegistries.ENTITY_TYPES.getValue(key);
    }

    /**
     * The name of a monster by id, or the id itself if the game does not know it.
     */
    public static Component monsterName(String id) {
        EntityType<?> type = monsterType(id);
        return type == null ? Component.literal(id) : type.getDescription();
    }

    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
            return null;
        }
        return ForgeRegistries.ITEMS.getValue(key);
    }

    /**
     * The icon of a contract: the item to gather, the spawn egg of the monster, or a crystal of the rank.
     */
    public static ItemStack icon(Contract.Kind kind, String target, Rank rank) {
        if (kind == Contract.Kind.OUTLAW) {
            return new ItemStack(Items.PLAYER_HEAD);
        }
        if (kind == Contract.Kind.GATHER) {
            Item item = item(target);
            return item == null ? new ItemStack(Items.BARRIER) : new ItemStack(item);
        }
        if (!target.isEmpty()) {
            EntityType<?> type = monsterType(target);
            if (type != null) {
                SpawnEggItem egg = SpawnEggItem.byId(type);
                if (egg != null) {
                    return new ItemStack(egg);
                }
            }
        }
        return MonsterCrystalItem.stack(rank, 1);
    }

    /**
     * What a contract asks for: «Охота: 5 × Зомби», «Охота: 5 монстров ранга C и выше», «Сбор: Кость, 32 предмета»,
     * «Награда за голову: Разоритель», «Экзамен на ранг D: 5 монстров ранга D и выше».
     */
    public static MutableComponent task(Contract.Kind kind, String target, Rank targetRank, boolean boss, int count) {
        return switch (kind) {
            case HUNT -> target.isEmpty()
                    ? Component.translatable(CONTRACT + "hunt_rank", monsters(count), rank(targetRank))
                    : Component.translatable(CONTRACT + "hunt", count, monsterName(target));
            case BOUNTY -> count > 1 ? Component.translatable(CONTRACT + "bounty_many", monsterName(target), count)
                    : Component.translatable(CONTRACT + "bounty", monsterName(target));
            case GATHER -> Component.translatable(CONTRACT + "gather", item(target) == null ? Component.literal(target)
                    : new ItemStack(item(target)).getHoverName(), TradeTexts.items(count));
            case EXAM -> boss ? Component.translatable(CONTRACT + "exam_boss", rank(targetRank), bosses(count), rank(targetRank))
                    : Component.translatable(CONTRACT + "exam", rank(targetRank), monsters(count), rank(targetRank));
            case ESCORT -> Component.translatable(CONTRACT + "escort");
            case WAR -> Component.translatable(CONTRACT + "war");
            case OUTLAW -> Component.translatable(CONTRACT + "outlaw", Contract.outlawName(target));
        };
    }

    /**
     * What a contract counts, in a few words for the line above the hotbar: the monster, the rank, or the item.
     */
    public static MutableComponent shortTarget(Contract contract) {
        if (contract.kind() == Contract.Kind.GATHER) {
            Item found = item(contract.target());
            return found == null ? Component.literal(contract.target()) : new ItemStack(found).getHoverName().copy();
        }
        if (contract.kind() == Contract.Kind.OUTLAW) {
            return Component.literal(Contract.outlawName(contract.target()));
        }
        if (contract.target().isEmpty()) {
            return Component.translatable(CONTRACT + "short_rank", rank(contract.targetRank()));
        }
        return monsterName(contract.target()).copy();
    }

    public static MutableComponent task(Contract contract) {
        return task(contract.kind(), contract.target(), contract.targetRank(), contract.boss(), contract.count());
    }

    /**
     * The name of a kind of contract: «Охота».
     */
    public static MutableComponent kind(Contract.Kind kind) {
        return Component.translatable(CONTRACT + "kind." + kind.id());
    }
}
