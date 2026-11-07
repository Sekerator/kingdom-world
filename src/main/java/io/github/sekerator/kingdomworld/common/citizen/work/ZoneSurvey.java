package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A look over the zone of a work post a little at a time, to estimate what the post yields before its work was
 * ever measured. {@link ZoneSurveys} gives every survey a share of a block budget per tick, so no tick looks at a
 * whole zone.
 */
public abstract class ZoneSurvey {
    protected final ServerLevel level;
    protected final WorkPost post;
    protected final WorkZone zone;
    private long index;
    private final long total;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final LandCheck land;

    protected ZoneSurvey(ServerLevel level, WorkPost post, WorkZone zone) {
        this.level = level;
        this.post = post;
        this.zone = zone;
        this.total = zone.volume();
        this.land = new LandCheck(level, post.kingdomId());
    }

    /**
     * Looks at up to {@code budget} blocks of the zone, column after column from the top.
     *
     * @return blocks looked at
     */
    public int step(int budget) {
        int used = 0;
        int width = zone.width();
        int height = zone.height();
        while (used < budget && index < total) {
            long column = index / height;
            int y = zone.maxY() - (int) (index % height);
            int x = zone.minX() + (int) (column % width);
            int z = zone.minZ() + (int) (column / width);
            index++;
            used++;
            cursor.set(x, y, z);
            if (!land.owns(x, z) || !level.isLoaded(cursor)) {
                continue;
            }
            visit(cursor, level.getBlockState(cursor));
        }
        return used;
    }

    public boolean done() {
        return index >= total;
    }

    /**
     * Called for every loaded block of the zone; {@code pos} is reused, copy it to keep it.
     */
    protected abstract void visit(BlockPos pos, BlockState state);

    /**
     * Called once after the last block; may look at entities of the zone.
     */
    protected void finish() {
    }

    /**
     * The estimated rates per worker and game day.
     */
    public abstract PostYield result();

    /**
     * What the zone means for the work: {@link WorkStatus#WORKING} if there is something to do, otherwise the
     * reason there is not (no trees, no water).
     */
    public abstract WorkStatus status();

    final void complete() {
        finish();
    }
}
