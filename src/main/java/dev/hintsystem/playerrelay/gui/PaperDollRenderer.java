package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.mixin.minecraft.EntityAccessor;
import dev.hintsystem.playerrelay.mixin.minecraft.LivingEntityInvoker;

import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PaperDollRenderer {
    public final float BODY_YAW_DEG;
    public final float MAX_HEAD_YAW_DEG;
    public final float HEAD_YAW_RETURN_SPEED;

    private Pose lastPose = Pose.STANDING;
    private Entity fakeVehicle;
    private float centerYaw;
    private float headYawOffset;
    private float headYawOffsetO;

    public boolean headYawEnabled = false; // Head yaw visualization is a bit buggy due to interpolation
    public boolean headPitchEnabled = false;
    public AnchorPoint anchorPoint = AnchorPoint.TOP_LEFT;

    public PaperDollRenderer() { this(10.0f, 40.0f, 4.0f); }

    public PaperDollRenderer(float bodyYawDeg, float maxHeadYawDeg, float headYawReturnSpeed) {
        this.BODY_YAW_DEG = bodyYawDeg;
        this.MAX_HEAD_YAW_DEG = maxHeadYawDeg;
        this.HEAD_YAW_RETURN_SPEED = headYawReturnSpeed;
    }

    public void applyPoseToPlayer(Player player, Pose newPose) {
        if (newPose == lastPose) return;

        // Clean up previous pose
        switch (lastPose) {
            case Pose.FALL_FLYING -> {
                player.setDeltaMovement(0, 0, 0);
                player.stopFallFlying();
            }
            case Pose.SITTING -> removeVehicle(player);
            case Pose.SPIN_ATTACK -> ((LivingEntityInvoker) player)
                .invokeSetLivingFlag(LivingEntityInvoker.getRiptideFlag(), false);
        }

        // Apply new pose
        switch (newPose) {
            case Pose.FALL_FLYING -> {
                player.setDeltaMovement(0, 5, 0);
                player.startFallFlying();
            }
            case Pose.SITTING -> setFakeVehicle(player);
            case Pose.SPIN_ATTACK -> ((LivingEntityInvoker)player)
                .invokeSetLivingFlag(LivingEntityInvoker.getRiptideFlag(), true);
        }

        player.setPose(newPose);
        lastPose = newPose;
    }

    public void applyHealth(LivingEntity livingEntity, float health) {
        if (health == livingEntity.getHealth()) return;

        float healthDif = health - livingEntity.getHealth();
        if (healthDif < 0.0f) { livingEntity.animateHurt(10); }

        livingEntity.setHealth(health);
    }

    public void setFakeVehicle(LivingEntity livingEntity) {
        if (fakeVehicle == null) {
            fakeVehicle = new Entity(EntityType.ARMOR_STAND, livingEntity.level()) {
                @Override
                protected void defineSynchedData(SynchedEntityData.Builder builder) {}
                @Override
                public boolean hurtServer(ServerLevel world, DamageSource source, float amount) { return false; }
                @Override
                protected void readAdditionalSaveData(ValueInput view) {}
                @Override
                protected void addAdditionalSaveData(ValueOutput view) {}
            };
            fakeVehicle.setInvisible(true);
        }

        ((EntityAccessor)livingEntity).setVehicle(fakeVehicle);
    }

    public void removeVehicle(LivingEntity livingEntity) { ((EntityAccessor)livingEntity).setVehicle(null); }

    public void tick(LivingEntity livingEntity) {
        if (headYawEnabled) {
            headYawOffsetO = headYawOffset;

            float currentYaw = livingEntity.getYRot();
            if (currentYaw > centerYaw + MAX_HEAD_YAW_DEG) {
                centerYaw = currentYaw - MAX_HEAD_YAW_DEG;
            } else if (currentYaw < centerYaw - MAX_HEAD_YAW_DEG) {
                centerYaw = currentYaw + MAX_HEAD_YAW_DEG;
            }

            headYawOffset = Math.clamp(currentYaw - centerYaw, -MAX_HEAD_YAW_DEG, MAX_HEAD_YAW_DEG);

            // Smooth return toward center
            float centerAdjust = (headYawOffset) / HEAD_YAW_RETURN_SPEED;
            centerYaw = (centerYaw + centerAdjust);
        } else {
            headYawOffset = headYawOffsetO = centerYaw = 0.0f;
        }

        if (livingEntity.isAlive()) {
            livingEntity.tick();
            livingEntity.tickCount++;
        }
    }

    public void renderPaperDoll(GuiGraphics context, int x1, int y1, int x2, int y2, int scale, LivingEntity livingEntity, DeltaTracker tickCounter) {
        renderPaperDoll(context,
            x1, y1,
            x2, y2,
            scale,
            0.0F, livingEntity, tickCounter);
    }

    public void renderPaperDoll(GuiGraphics context, int x1, int y1, int x2, int y2, int scale, float yOffset, LivingEntity livingEntity, DeltaTracker tickCounter) {
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf overrideCameraAngle = new Quaternionf().rotateX((float) Math.toRadians(15.0F));
        rotation.mul(overrideCameraAngle);

        float xRot = livingEntity.getXRot();
        float xRotO = livingEntity.xRotO;
        float yRot = livingEntity.getYRot();
        float yRotO = livingEntity.yRotO;
        float yBodyRot = livingEntity.getVisualRotationYInDegrees();
        float yBodyRotO = livingEntity.yBodyRotO;
        float yHeadRot = livingEntity.getYHeadRot();
        float yHeadRotO = livingEntity.yHeadRotO;

        float entityScale = livingEntity.getScale();
        float relativeScale = scale / entityScale;
        if (livingEntity.isPassenger()) { yOffset += 0.25f; }
        Vector3f translation = new Vector3f(0.0F, livingEntity.getBbHeight() / 2.0F + yOffset * entityScale, 0.0F);

        applyEntityTransforms(livingEntity, translation, rotation);
        drawEntity(context, x1, y1, x2, y2, relativeScale, translation, rotation, overrideCameraAngle, livingEntity, tickCounter);

        livingEntity.setXRot(xRot);
        livingEntity.xRotO = xRotO;
        livingEntity.setYRot(yRot);
        livingEntity.yRotO = yRotO;
        livingEntity.setYBodyRot(yBodyRot);
        livingEntity.yBodyRotO = yBodyRotO;
        livingEntity.setYHeadRot(yHeadRot);
        livingEntity.yHeadRotO = yHeadRotO;
    }

    /** @see net.minecraft.client.gui.screens.inventory.InventoryScreen#extractRenderState(LivingEntity) **/
    public static void drawEntity(GuiGraphics context, int x1, int y1, int x2, int y2, float scale, Vector3f translation,
                                  Quaternionf rotation, @Nullable Quaternionf overrideCameraAngle, LivingEntity livingEntity, DeltaTracker tickCounter) {
        EntityRenderDispatcher entityRenderManager = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> entityRenderer = entityRenderManager.getRenderer(livingEntity);
        EntityRenderState entityRenderState = entityRenderer.createRenderState(livingEntity, tickCounter.getGameTimeDeltaPartialTick(false));
        entityRenderState.nameTag = null;
        entityRenderState.lightCoords = 15728880;
        entityRenderState.shadowPieces.clear();
        entityRenderState.outlineColor = 0;
        context.submitEntityRenderState(entityRenderState, scale, translation, rotation, overrideCameraAngle, x1, y1, x2, y2);
    }

    private void applyEntityTransforms(LivingEntity livingEntity, Vector3f translation, Quaternionf rotation) {
        if (!headPitchEnabled || livingEntity.isFallFlying()) {
            livingEntity.setXRot(7.5f);
            livingEntity.xRotO = 7.5f;
        }

        float defaultRotationYaw = getDefaultRotationYaw();
        if (livingEntity.getPose() == Pose.SLEEPING) {
            translation.add(0.0f, livingEntity.getBbHeight() * 2, 0.0f);

            rotation.mul(Axis.XN.rotationDegrees(40f));
            defaultRotationYaw = 90.0f - defaultRotationYaw;
        } else {
            defaultRotationYaw = 180.0f + defaultRotationYaw;
        }

        livingEntity.yBodyRot = livingEntity.yBodyRotO = defaultRotationYaw;
        livingEntity.yHeadRot = defaultRotationYaw + headYawOffset;
        livingEntity.yHeadRotO = defaultRotationYaw + headYawOffsetO;
    }

    private float getDefaultRotationYaw() { return (anchorPoint.x == 1) ? this.BODY_YAW_DEG : -this.BODY_YAW_DEG; }
}
