/**
 * Pure domain logic of KingdomWorld: kingdoms, economy, diplomacy and the abstract simulation.
 *
 * <p>Code in this package and its subpackages must not depend on Minecraft, Forge or Mojang libraries
 * (no {@code net.minecraft}, {@code net.minecraftforge} or {@code com.mojang} references). This keeps the
 * domain fast to unit test and lets the same rules drive both the physical simulation near players and
 * the abstract simulation far from them. The rule is enforced by {@code CorePurityTest}.
 *
 * <p>Classes here are not thread safe unless stated otherwise; the server accesses them from the server thread.
 */
package io.github.sekerator.kingdomworld.core;
