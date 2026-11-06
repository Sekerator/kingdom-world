package io.github.sekerator.kingdomworld.common.mixin;

import io.github.sekerator.kingdomworld.common.protection.FireProtectionHook;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps fire out of kingdom land. The game has no event for fire spreading or burning blocks, so the two
 * places of the fire block that decide it are hooked: the chance to ignite an empty position next to a fire,
 * and the attempt to burn (and replace) a neighbouring block.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin implements FireProtectionHook {

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    private void kingdomworld$keepFireOut(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> result) {
        if (level instanceof Level realLevel && ProtectionService.protectsFromFire(realLevel, pos)) {
            result.setReturnValue(0);
        }
    }

    // tryCatchFire is added by Forge and is not obfuscated, so it is not remapped.
    @Inject(method = "tryCatchFire", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdomworld$keepBlocksFromBurning(Level level, BlockPos pos, int chance, RandomSource random, int age,
                                                    Direction face, CallbackInfo callback) {
        if (ProtectionService.protectsFromFire(level, pos)) {
            callback.cancel();
        }
    }
}
