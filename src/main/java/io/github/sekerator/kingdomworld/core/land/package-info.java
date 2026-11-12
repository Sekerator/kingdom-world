/**
 * The plan of large states, worked out on a raster of the land without the game: the survey of the land in cells of 64 blocks
 * ({@link io.github.sekerator.kingdomworld.core.land.SurveyRaster}), the states grown over it from their seeds
 * ({@link io.github.sekerator.kingdomworld.core.land.StatePlanner}), their provinces and the wild provinces of the neutral land
 * ({@link io.github.sekerator.kingdomworld.core.land.ProvincePlanner}), the borders drawn chunk by chunk
 * ({@link io.github.sekerator.kingdomworld.core.land.BorderRefiner}), the places of the settlements
 * ({@link io.github.sekerator.kingdomworld.core.land.SettlementPlanner}) and the roads between them
 * ({@link io.github.sekerator.kingdomworld.core.land.RoadPlanner}).
 *
 * <p>Every planner is a pure function of its inputs and of the seed of the world: the same raster and seed give the same plan,
 * nothing depends on the clock or on the order of a hash map. Inputs and results are immutable, so the heavy steps may run on a
 * worker thread while the server goes on; only the checks of places in the world (the biome and the lay of the ground around a
 * throne or a settlement) belong to the server thread.
 */
package io.github.sekerator.kingdomworld.core.land;
