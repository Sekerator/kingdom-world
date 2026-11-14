package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * What the board on the sides of a work post shows: the profession of the post. Only a look: the profession itself lives
 * in the data of the kingdom, and the block follows it ({@code WorkPostLooks}).
 *
 * <p>The values are written by hand and never follow the registry of professions: the innkeeper joins it only when its
 * behaviour loads, and other mods add professions whenever they like. A value is never renamed or removed, because worlds
 * and templates keep the names; new ones may be added, with a new network protocol.
 */
public enum WorkPostLook implements StringRepresentable {
    NONE("none"),
    FARMER("farmer"),
    LUMBERJACK("lumberjack"),
    MINER("miner"),
    FISHER("fisher"),
    HERDER("herder"),
    CARRIER("carrier"),
    SMELTER("smelter"),
    COOK("cook"),
    CRAFTER("crafter"),
    SMITH("smith"),
    MINTER("minter"),
    MERCHANT("merchant"),
    REGISTRAR("registrar"),
    BUILDER("builder"),
    MAGE("mage"),
    INNKEEPER("innkeeper"),
    OTHER("other");

    private final String name;

    WorkPostLook(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * The look of a post with the profession: none for a post without one, the look of the same name, or a sheet of paper
     * for a profession of another mod and for one that is gone.
     */
    public static WorkPostLook of(@Nullable String professionId) {
        if (professionId == null || Professions.UNEMPLOYED.id().equals(professionId)) {
            return NONE;
        }
        for (WorkPostLook look : values()) {
            if (look != NONE && look != OTHER && look.name.equals(professionId)) {
                return look;
            }
        }
        return OTHER;
    }
}
