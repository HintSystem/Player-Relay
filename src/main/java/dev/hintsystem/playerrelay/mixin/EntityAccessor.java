package dev.hintsystem.playerrelay.mixin;

import net.minecraft.entity.Entity;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("vehicle")
    void setVehicle(@Nullable Entity vehicle);
}
