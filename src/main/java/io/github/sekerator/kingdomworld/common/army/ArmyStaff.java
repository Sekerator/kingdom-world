package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.ForceRules;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Who serves where in the army of a kingdom. A king or a marshal moves soldiers between the guard and the army and gives
 * the posts of vice-captain and captain; an officer is paid more than a fighter. A kingdom of the world sees to its staff
 * itself, once a day: it replaces a few of the fallen, lets the best fighter of a branch take the post of an officer who
 * fell some days ago, and issues every soldier the kit of its branch and post, all paid from its treasury. A post it
 * cannot pay the kit of stays vacant.
 */
public final class ArmyStaff {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * What a day's care of the staff did.
     *
     * @param recruited fallen soldiers replaced
     * @param promoted  posts of officers filled
     * @param issued    pieces of gear issued
     * @param paid      what the treasury paid for the gear
     */
    public record Report(int recruited, int promoted, int issued, long paid) {
        public static final Report NOTHING = new Report(0, 0, 0, 0);
    }

    private ArmyStaff() {
    }

    /**
     * The wage of a citizen in thousandths of the wage of its profession: more for an officer of the guard or the army.
     */
    public static int wagePermille(MinecraftServer server, UUID citizenId) {
        return ArmyWork.state(server).soldier(citizenId).map(soldier -> ArmySettings.force().wagePermille(soldier.post())).orElse(1_000);
    }

    // ---------------------------------------------------------------- decisions of a king, a marshal or an operator

    /**
     * Moves a soldier to the guard or to the army. An officer leaves its post, and the issued gear of the post, behind.
     *
     * @param actor a member with the right to run the army; {@code null} for an operator or the kingdom itself
     */
    public static Outcome setBranch(MinecraftServer server, @Nullable UUID actor, UUID citizenId, Branch branch) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        Outcome outcome = ArmyWork.change(server, "move a soldier to a branch", army -> army.setBranch(actor, citizenId, branch, world));
        if (outcome.ok()) {
            world.citizens().get(citizenId).ifPresent(citizen -> ArmyKits.giveUp(server, citizen, true));
        }
        return outcome;
    }

    /**
     * Gives a soldier a post in its branch, or takes it away. Whoever held the post before serves as a fighter again. The
     * new officer takes armor from the storehouses at once: its post counts only in armor of the post.
     */
    public static Outcome appoint(MinecraftServer server, @Nullable UUID actor, UUID citizenId, Post post) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        ArmyState army = ArmyWork.state(server);
        Optional<Soldier> before = army.soldier(citizenId);
        if (before.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_A_SOLDIER);
        }
        Optional<Soldier> holder = army.officer(before.get().kingdomId(), before.get().branch(), post);
        Outcome outcome = ArmyWork.change(server, "give a soldier a post", state -> state.appoint(actor, citizenId, post, world));
        if (!outcome.ok()) {
            return outcome;
        }
        // Issued gear of a post stays with the post, not with whoever leaves it.
        holder.flatMap(old -> world.citizens().get(old.citizenId())).ifPresent(citizen -> ArmyKits.giveUp(server, citizen, true));
        world.citizens().get(citizenId).ifPresent(citizen -> {
            ArmyKits.giveUp(server, citizen, true);
            ArmyWork.equip(server, citizen);
        });
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- the staff of a kingdom of the world

    /**
     * Sees to the staff of a kingdom once a game day; a second call on the same day does nothing.
     */
    public static Report daily(MinecraftServer server, UUID kingdomId) {
        if (ArmyWork.isReadOnly(server) || ArmyWork.state(server).staffDay(kingdomId) >= ArmyWork.day(server)) {
            return Report.NOTHING;
        }
        return muster(server, kingdomId, false);
    }

    /**
     * Sees to the staff of a kingdom now: replaces fallen soldiers (a few a day), fills the posts of officers that are open
     * and issues the kits.
     *
     * @param free whether the kingdom has the gear already and pays nothing: at its founding the gear is a part of its
     *             wealth
     */
    public static Report muster(MinecraftServer server, UUID kingdomId, boolean free) {
        if (ArmyWork.isReadOnly(server) || KingdomWorldApi.kingdom(server, kingdomId).isEmpty()) {
            return Report.NOTHING;
        }
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ForceRules rules = ArmySettings.force();
        long day = ArmyWork.day(server);
        int recruited = replace(server, kingdomId, army, rules);
        int promoted = 0;
        for (Branch branch : Branch.values()) {
            for (Post post : new Post[]{Post.CAPTAIN, Post.VICE_CAPTAIN}) {
                if (promote(server, kingdomId, branch, post, day, free)) {
                    promoted++;
                }
            }
        }
        int issued = 0;
        long paid = 0;
        for (Soldier soldier : army.soldiersOf(kingdomId)) {
            Optional<Citizen> citizen = world.citizens().get(soldier.citizenId());
            if (citizen.isEmpty() || ArmyWork.isAwayFromHome(server, soldier.citizenId())) {
                continue;
            }
            ArmyKits.Issued result = ArmyKits.issue(server, citizen.get(), soldier, free);
            issued += result.pieces();
            paid += result.paid();
        }
        army.setStaffDay(kingdomId, day);
        if (recruited + promoted + issued > 0) {
            LOGGER.info("Staff of {}: {} fallen replaced, {} post(s) filled, {} piece(s) of gear issued for {}", kingdomId, recruited, promoted,
                    issued, paid);
        }
        return new Report(recruited, promoted, issued, paid);
    }

    /**
     * Replaces fallen soldiers by citizens without work, a few a day, each into the branch that lost one.
     */
    private static int replace(MinecraftServer server, UUID kingdomId, ArmyState army, ForceRules rules) {
        WorldPos capital = ArmyWork.capitalOf(server, kingdomId);
        int recruited = 0;
        for (Branch branch : Branch.values()) {
            while (recruited < rules.replacePerDay && army.losses(kingdomId, branch) > 0) {
                Optional<Citizen> recruit = capital == null ? Optional.empty() : ArmyActions.nearestUnemployed(server, kingdomId, capital);
                if (recruit.isEmpty()) {
                    return recruited;
                }
                // Warriors and archers by turns, two warriors to an archer.
                String profession = army.branchOf(kingdomId, branch).size() % 3 == 2 ? Professions.ARCHER.id() : Professions.WARRIOR.id();
                if (!ArmyWork.recruit(server, null, kingdomId, recruit.get().id(), profession).ok()) {
                    return recruited;
                }
                if (branch != Branch.GUARD) {
                    army.setBranch(null, recruit.get().id(), branch, KingdomWorldApi.state(server));
                }
                army.replaced(kingdomId, branch);
                recruited++;
            }
        }
        return recruited;
    }

    /**
     * Fills a post that is open: the vice-captain rises to captain, otherwise the best fighter of the branch takes it. The
     * kingdom pays the kit of the post; a kit it cannot pay leaves the post vacant.
     */
    private static boolean promote(MinecraftServer server, UUID kingdomId, Branch branch, Post post, long day, boolean free) {
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        if (!army.postOpen(kingdomId, branch, post, day, ArmySettings.force())) {
            return false;
        }
        Optional<Soldier> best = army.successor(kingdomId, branch, post);
        Optional<Citizen> citizen = best.flatMap(soldier -> world.citizens().get(soldier.citizenId()));
        if (citizen.isEmpty()) {
            return false;
        }
        Soldier promoted = best.get().withPost(post);
        long price = free ? 0 : ArmyKits.missingPrice(server, citizen.get(), promoted);
        if (price > 0 && world.economy().treasury(kingdomId).map(treasury -> treasury.funds() < price).orElse(true)) {
            LOGGER.debug("The post of {} of the {} of {} stays vacant: its kit costs {}", post.id(), branch.id(), kingdomId, price);
            return false;
        }
        if (!army.appoint(null, promoted.citizenId(), post, world).ok()) {
            return false;
        }
        ArmyKits.issue(server, citizen.get(), promoted, free);
        LOGGER.info("Soldier {} of {} is the {} of the {} now", citizen.get().shortId(), kingdomId, post.id(), branch.id());
        return true;
    }
}
