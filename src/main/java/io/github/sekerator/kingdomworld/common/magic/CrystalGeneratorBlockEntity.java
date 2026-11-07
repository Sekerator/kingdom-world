package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.ForgeEnergyRate;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The block entity of a crystal generator. It takes whole units of the energy of the kingdom whose land it stands on only
 * when a machine asks for Forge Energy, keeps the Forge Energy of those units in a small buffer and gives at most the limit
 * of the config per tick: to the machines around it every tick, and to any mod that pulls through its capability. It
 * receives nothing, so Forge Energy never becomes energy of a kingdom.
 */
public class CrystalGeneratorBlockEntity extends BlockEntity {
    private static final String BUFFER = "Buffer";
    /** The most the buffer may hold after loading: the largest tick and unit the config allows. */
    private static final long MAX_BUFFER = 2_000_000;

    /** Forge Energy already paid for and not given yet. */
    private long buffer;
    private long tick = Long.MIN_VALUE;
    private long givenThisTick;
    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return give(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, buffer);
        }

        @Override
        public int getMaxEnergyStored() {
            return Math.max(getEnergyStored(), MagicSettings.rules().feMaxPerTick);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };
    private LazyOptional<IEnergyStorage> capability = LazyOptional.of(() -> storage);

    public CrystalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(MagicRegistries.GENERATOR_ENTITY.get(), pos, state);
    }

    /**
     * Forge Energy waiting in the buffer.
     */
    public long buffer() {
        return buffer;
    }

    /**
     * The kingdom whose energy the generator gives: only a generator a member with the right set up, on the land of that
     * same kingdom; {@code null} for any other.
     */
    @Nullable
    private UUID kingdom() {
        return level == null ? null : MagicDevices.working(level, worldPosition, io.github.sekerator.kingdomworld.core.magic.MagicDevice.Kind.GENERATOR)
                .map(io.github.sekerator.kingdomworld.core.magic.MagicDevice::kingdomId).orElse(null);
    }

    private void resetTick() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != tick) {
            tick = now;
            givenThisTick = 0;
        }
    }

    /**
     * Gives up to {@code wanted} Forge Energy, paying the kingdom's energy for what the buffer lacks.
     *
     * @return the Forge Energy given, or that would be given
     */
    int give(int wanted, boolean simulate) {
        if (level == null || level.isClientSide || wanted <= 0) {
            return 0;
        }
        MinecraftServer server = level.getServer();
        UUID kingdom = kingdom();
        MagicRules rules = MagicSettings.rules();
        resetTick();
        long room = Math.max(0, rules.feMaxPerTick - givenThisTick);
        long want = Math.min(wanted, room);
        if (want <= 0) {
            return 0;
        }
        long stored = kingdom == null || server == null || MagicWork.isReadOnly(server) ? 0 : MagicWork.state(server).energy(kingdom);
        if (simulate) {
            return (int) Math.min(want, Math.min(Integer.MAX_VALUE, buffer + ForgeEnergyRate.feFor(stored, rules.feRate)));
        }
        long units = ForgeEnergyRate.unitsToTake(want, buffer, rules.feMaxPerTick, rules.feRate, stored);
        if (units > 0 && MagicWork.state(server).spend(kingdom, units, EnergyCategory.MACHINES, TreasuryActions.day(server)).ok()) {
            buffer += ForgeEnergyRate.feFor(units, rules.feRate);
            setChanged();
        }
        int given = (int) Math.min(want, buffer);
        buffer -= given;
        givenThisTick += given;
        if (given > 0) {
            setChanged();
        }
        return given;
    }

    /**
     * Offers Forge Energy to a machine: what it accepts is paid and given.
     *
     * @return the Forge Energy the machine took
     */
    public int supply(IEnergyStorage sink, int max) {
        if (sink == null || !sink.canReceive()) {
            return 0;
        }
        int offer = give(max, true);
        if (offer <= 0) {
            return 0;
        }
        int accepted = sink.receiveEnergy(offer, true);
        if (accepted <= 0) {
            return 0;
        }
        int given = give(accepted, false);
        int taken = sink.receiveEnergy(given, false);
        if (taken < given) {
            // The machine took less than it said: keep the rest for the next tick.
            buffer += given - taken;
            givenThisTick -= given - taken;
        }
        return taken;
    }

    void serverTick() {
        if (level == null || MagicSettings.rules().feMaxPerTick <= 0) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null || neighbor instanceof CrystalGeneratorBlockEntity) {
                continue;
            }
            neighbor.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).ifPresent(sink -> supply(sink, Integer.MAX_VALUE));
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return capability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capability = LazyOptional.of(() -> storage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(BUFFER, buffer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // The buffer never holds more than a tick's worth and one unit; anything else in the data is not trusted.
        buffer = Math.max(0, Math.min(tag.getLong(BUFFER), MAX_BUFFER));
    }
}
