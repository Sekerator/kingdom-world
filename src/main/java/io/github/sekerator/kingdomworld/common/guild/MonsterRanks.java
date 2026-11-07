package io.github.sekerator.kingdomworld.common.guild;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.MonsterRanking;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks of the monsters of the running game, vanilla and of every mod, by type: from the default attributes of the type
 * (health, attack, armor, speed), the {@code forge:bosses} tag, a boss bar of its own and the overrides of the config.
 * Worked out once per type and kept until the rules or the tags change. Server thread only.
 */
public final class MonsterRanks {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "bosses"));
    private static final Map<EntityType<?>, MonsterRanking.Ranked> CACHE = new HashMap<>();
    private static final Map<Class<?>, Boolean> BOSS_BARS = new HashMap<>();
    private static String rulesKey = "";

    private MonsterRanks() {
    }

    public static void invalidate() {
        CACHE.clear();
        BOSS_BARS.clear();
        rulesKey = "";
    }

    private static void checkRules(GuildRules rules) {
        StringBuilder key = new StringBuilder();
        key.append(rules.healthWeight).append('/').append(rules.damageWeight).append('/').append(rules.armorWeight).append('/')
                .append(rules.speedWeight).append('/').append(rules.bossRankBonus).append('/').append(rules.bossMinRank);
        for (Rank rank : Rank.values()) {
            key.append('/').append(rules.rankScore(rank));
        }
        key.append(rules.idOverrides).append(rules.tagOverrides);
        String next = key.toString();
        if (!next.equals(rulesKey)) {
            CACHE.clear();
            rulesKey = next;
        }
    }

    /**
     * Whether an entity counts as a hostile monster for the guild.
     */
    public static boolean isMonster(Entity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
    }

    public static String id(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? "" : id.toString();
    }

    /**
     * The rank of a monster in the world.
     */
    public static MonsterRanking.Ranked of(LivingEntity entity) {
        GuildRules rules = GuildSettings.rules();
        checkRules(rules);
        MonsterRanking.Ranked known = CACHE.get(entity.getType());
        if (known != null) {
            return known;
        }
        MonsterRanking.Ranked ranked = MonsterRanking.rank(stats(entity.getType(), entity), rules);
        CACHE.put(entity.getType(), ranked);
        return ranked;
    }

    /**
     * The rank of a type of monster; a monster of the type is made (never added to the world) to see whether it has a boss
     * bar, if the level is given.
     */
    public static MonsterRanking.Ranked of(EntityType<?> type, @Nullable ServerLevel level) {
        GuildRules rules = GuildSettings.rules();
        checkRules(rules);
        MonsterRanking.Ranked known = CACHE.get(type);
        if (known != null) {
            return known;
        }
        Entity sample = null;
        if (level != null) {
            try {
                sample = type.create(level);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.debug("KingdomWorld could not make a {} to rank it", id(type), e);
            }
        }
        MonsterRanking.Ranked ranked;
        try {
            ranked = MonsterRanking.rank(stats(type, sample), rules);
        } finally {
            if (sample != null) {
                sample.discard();
            }
        }
        CACHE.put(type, ranked);
        return ranked;
    }

    @SuppressWarnings("unchecked")
    static MonsterRanking.Stats stats(EntityType<?> type, @Nullable Entity sample) {
        AttributeSupplier supplier = null;
        try {
            if (DefaultAttributes.hasSupplier(type)) {
                supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            }
        } catch (RuntimeException e) {
            supplier = null;
        }
        LivingEntity living = sample instanceof LivingEntity entity ? entity : null;
        double health = value(supplier, living, Attributes.MAX_HEALTH);
        double attack = value(supplier, living, Attributes.ATTACK_DAMAGE);
        double armor = value(supplier, living, Attributes.ARMOR);
        double speed = value(supplier, living, Attributes.MOVEMENT_SPEED);
        List<String> tags = new ArrayList<>();
        type.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
        boolean boss = type.is(BOSSES) || (sample != null && hasBossBar(sample.getClass()));
        return new MonsterRanking.Stats(id(type), health, attack, armor, speed, boss, tags);
    }

    private static double value(@Nullable AttributeSupplier supplier, @Nullable LivingEntity living, Attribute attribute) {
        try {
            if (supplier != null && supplier.hasAttribute(attribute)) {
                return supplier.getBaseValue(attribute);
            }
            if (living != null && living.getAttributes().hasAttribute(attribute)) {
                return living.getAttributeBaseValue(attribute);
            }
        } catch (RuntimeException e) {
            return 0.0;
        }
        return 0.0;
    }

    /**
     * Whether the class of a monster keeps a boss bar of its own, as bosses of every mod do.
     */
    static boolean hasBossBar(Class<?> type) {
        Boolean known = BOSS_BARS.get(type);
        if (known != null) {
            return known;
        }
        boolean found = false;
        try {
            for (Class<?> current = type; current != null && current != Object.class && !found; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (ServerBossEvent.class.isAssignableFrom(field.getType())) {
                        found = true;
                        break;
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            found = false;
        }
        BOSS_BARS.put(type, found);
        return found;
    }
}
