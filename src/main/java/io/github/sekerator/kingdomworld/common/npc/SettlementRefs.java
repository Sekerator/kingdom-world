package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Whether something the mod keeps refers to a citizen by its id, so that it may not be folded into the numbers of its
 * settlement. The stores that refer to citizens and how each is closed:
 * <ul>
 * <li>bodies ({@code CitizenBodies}): a citizen with a body stays named;</li>
 * <li>the army (soldiers, their posts and squads, the escorts of caravans): a soldier or a member of a squad stays named;</li>
 * <li>the guild (hirelings, squads, expeditions): an adventurer, a hireling or one away stays named;</li>
 * <li>the life of cities (the sick, evenings at the tavern): the sick stay named, the evenings are forgotten;</li>
 * <li>the reservations of the workshops: one who holds a reservation stays named;</li>
 * <li>homes and work posts: freed when it folds; the holders of the posts of a standing town stay named anyway;</li>
 * <li>builders on a construction: they work with a body, which pins them; their claims are let go when it folds;</li>
 * <li>its gear: goes to the storehouses of its kingdom by the books when it folds.</li>
 * </ul>
 * Companions, parties, deals, caravans, the law (victims by name, wanted players) and the nobility (spies and houses by name
 * seed) keep no citizen ids.
 */
final class SettlementRefs {
    private SettlementRefs() {
    }

    /**
     * Whether a citizen is pinned: something keeps referring to it.
     */
    static boolean pinned(MinecraftServer server, Citizen citizen) {
        UUID id = citizen.id();
        if (CitizenBodies.body(id) != null) {
            return true;
        }
        ArmyState army = ArmyWork.state(server);
        if (army.soldier(id).isPresent() || army.squadOf(id).isPresent()) {
            return true;
        }
        String profession = citizen.professionId();
        if (profession.equals(Professions.ADVENTURER.id()) || profession.equals(Professions.WARRIOR.id())
                || profession.equals(Professions.ARCHER.id())) {
            return true;
        }
        GuildState guild = GuildWork.state(server);
        if (guild.hireling(id).isPresent() || guild.squadOf(id).isPresent() || guild.isAway(id)) {
            return true;
        }
        if (LifeWork.state(server).isSick(id)) {
            return true;
        }
        return Production.reservations().has(id);
    }
}
