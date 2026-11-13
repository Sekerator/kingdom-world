package io.github.sekerator.kingdomworld.common.diplomacy;

import io.github.sekerator.kingdomworld.core.diplomacy.CedeLand;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diplomacy format 2 keeps the provinces of claims and offers to cede a province; claims and offers of format 1 read as
 * they are.
 */
class DiplomacyFormatTest {
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);

    @Test
    void claimsKeepTheirProvincesAndOffersTheirProvinceAndPrice() {
        DiplomacyState state = new DiplomacyState();
        state.restoreClaim(new DiplomacyState.Claim(4, a, b, 4, "", false, 100, List.of(14, 13)));
        state.restoreClaim(new DiplomacyState.Claim(5, a, b, 2, "Ostbrook", false, 120));
        Proposal cede = state.proposeCede(a, b, 7, 800, new CedeLand(b, false, true), 0, 1_000, DiplomacyRules.DEFAULT).proposal().orElseThrow();
        Proposal pact = state.propose(b, new UUID(0, 3), Proposal.Kind.PACT, null, 0, PeaceTerms.WHITE, 0, 0, DiplomacyRules.DEFAULT)
                .proposal().orElseThrow();

        CompoundTag tag = DiplomacyNbt.write(state);
        assertFalse(tag.getList("claims", Tag.TAG_COMPOUND).getCompound(1).contains("provinces"), "a claim without provinces is written as before");
        assertFalse(tag.getList("proposals", Tag.TAG_COMPOUND).getCompound(1).contains("province"), "so is an offer that is not a cession");
        DiplomacyState read = new DiplomacyState();
        assertEquals(List.of(), DiplomacyNbt.read(tag, read));
        assertEquals(List.of(14, 13), read.claims().get(0).provinces());
        assertEquals(4, read.claims().get(0).chunks());
        assertEquals(List.of(), read.claims().get(1).provinces());
        assertEquals("Ostbrook", read.claims().get(1).city());
        Proposal again = read.proposal(cede.id()).orElseThrow();
        assertEquals(Proposal.Kind.CEDE, again.kind());
        assertEquals(7, again.province());
        assertEquals(800, again.price());
        assertEquals(cede, again);
        assertEquals(pact, read.proposal(pact.id()).orElseThrow());
    }

    @Test
    void anOldClaimWithoutProvincesReadsAsItIs() {
        CompoundTag claim = new CompoundTag();
        claim.putLong("war", 9);
        claim.putUUID("winner", a);
        claim.putUUID("loser", b);
        claim.putInt("chunks", 4);
        claim.putString("city", "");
        claim.putBoolean("throne", false);
        claim.putLong("made", 77);
        CompoundTag tag = new CompoundTag();
        ListTag claims = new ListTag();
        claims.add(claim);
        tag.put("claims", claims);
        tag.putInt(DiplomacySavedData.TAG_FORMAT_VERSION, 1);

        DiplomacySavedData data = DiplomacySavedData.load(tag);
        assertFalse(data.isReadOnly(), "format 1 reads as it is");
        DiplomacyState.Claim read = data.state().claims().get(0);
        assertEquals(4, read.chunks());
        assertTrue(read.provinces().isEmpty());
        assertEquals(77, read.made());

        CompoundTag damaged = claim.copy();
        damaged.put("provinces", new IntArrayTag(new int[]{3, 0}));
        CompoundTag broken = new CompoundTag();
        ListTag list = new ListTag();
        list.add(damaged);
        broken.put("claims", list);
        broken.putInt(DiplomacySavedData.TAG_FORMAT_VERSION, DiplomacySavedData.FORMAT_VERSION);
        assertTrue(DiplomacySavedData.load(broken).isReadOnly(), "a claim of a province 0 is not understood");

        CompoundTag newer = tag.copy();
        newer.putInt(DiplomacySavedData.TAG_FORMAT_VERSION, DiplomacySavedData.FORMAT_VERSION + 1);
        assertTrue(DiplomacySavedData.load(newer).isReadOnly(), "a newer format stays read-only");
        assertEquals(2, DiplomacySavedData.FORMAT_VERSION);
    }
}
