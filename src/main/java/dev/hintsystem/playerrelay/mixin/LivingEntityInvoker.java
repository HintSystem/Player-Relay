package dev.hintsystem.playerrelay.mixin;

import net.minecraft.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Accessor("USING_RIPTIDE_FLAG")
    static int getRiptideFlag() { return 4; }

    @Invoker("setLivingFlag")
    void invokeSetLivingFlag(int mask, boolean value);
}

