package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.server.MinecraftServer;

/**
 * Whether something the mod keeps refers to a citizen, so that it may not be folded into the numbers of its settlement.
 */
final class SettlementRefs {
    private SettlementRefs() {
    }

    /**
     * Whether a citizen is pinned: it has a body, serves as a soldier, is an adventurer, or another store of the mod refers to it.
     */
    static boolean pinned(MinecraftServer server, Citizen citizen) {
        if (CitizenBodies.body(citizen.id()) != null) {
            return true;
        }
        if (ArmyWork.state(server).soldier(citizen.id()).isPresent()) {
            return true;
        }
        String profession = citizen.professionId();
        return profession.equals(Professions.ADVENTURER.id()) || profession.equals(Professions.WARRIOR.id())
                || profession.equals(Professions.ARCHER.id());
    }
}
