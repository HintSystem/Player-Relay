package dev.hintsystem.playerrelay.mixin.minecraft;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Accessor("LIVING_ENTITY_FLAG_SPIN_ATTACK")
    static int getRiptideFlag() {
        throw new AssertionError();
    }

    @Invoker("setLivingEntityFlag")
    void invokeSetLivingFlag(int mask, boolean value);
}

