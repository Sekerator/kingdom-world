package io.github.sekerator.kingdomworld.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The posts of lumberjacks saved before lumberjacks had a radius of their own get its default once, when the world is
 * loaded; other posts and larger zones stay as they are.
 */
class LumberjackRadiusUpgradeTest {

    private static CompoundTag post(String profession, int radius) {
        CompoundTag post = new CompoundTag();
        post.putString("profession", profession);
        post.putInt("radius", radius);
        return post;
    }

    @Test
    void lumberjackPostsWithASmallerRadiusGetTheDefault() {
        ListTag posts = new ListTag();
        posts.add(post("lumberjack", 8));
        posts.add(post("lumberjack", 24));
        posts.add(post("miner", 8));
        CompoundTag population = new CompoundTag();
        population.put(CitizenNbt.POSTS, posts);
        CompoundTag kingdom = new CompoundTag();
        kingdom.put(CitizenNbt.POPULATION, population);

        KingdomDataMigrations.widenLumberjacks(kingdom, 16);

        assertEquals(16, posts.getCompound(0).getInt("radius"));
        assertEquals(24, posts.getCompound(1).getInt("radius"), "a larger zone stays");
        assertEquals(8, posts.getCompound(2).getInt("radius"), "other professions keep their radius");
        assertTrue(KingdomDataMigrations.UPGRADES.contains("lumberjack_radius"), "data saved now lists the upgrade as done");
        // A kingdom without citizens has nothing to change.
        KingdomDataMigrations.widenLumberjacks(new CompoundTag(), 16);
    }
}
