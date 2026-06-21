package dev.hintsystem.playerrelay.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.*;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PaperDollRenderer {
    public final float BODY_YAW_DEG;
    public final float MAX_HEAD_YAW_DEG;
    public final float HEAD_YAW_RETURN_SPEED;

    private float centerYaw;
    private float headYawOffset;
    private float headYawOffsetO;

    public boolean headYawEnabled = false; // Head yaw visualization is a bit buggy due to interpolation
    public boolean headPitchEnabled = false;
    public AnchorPoint anchorPoint = AnchorPoint.TOP_LEFT;

    public PaperDollRenderer() { this(10.0f, 40.0f, 4.0f); }

    // TODO replace with custom render state extraction
    public static class FakePlayer extends AbstractClientPlayer {
        protected Pose lastPose = Pose.STANDING;

        public FakePlayer(ClientLevel clientLevel, GameProfile gameProfile) {
            super(clientLevel, gameProfile);
            setId(-1);
        }

        // noPhysics doesn't work because of Player.tick(), so override methods
        @Override public boolean isInWall() { return false; }
        @Override protected boolean isAffectedByBlocks() { return false; }
        @Override protected void pushEntities() {}

        @Override public boolean isPassenger() {
            return hasPose(Pose.SITTING);
        }

        @Override public boolean isVisuallySwimming() {
            return hasPose(Pose.SWIMMING);
        }

        @Override public boolean isAutoSpinAttack() {
            return hasPose(Pose.SPIN_ATTACK);
        }

        @Override public void setPose(Pose pose) {}

        public void applyPose(Pose newPose) {
            if (newPose == lastPose) return;

            // Clean up previous pose
            if (lastPose == Pose.FALL_FLYING) {
                setDeltaMovement(0, 0, 0);
                stopFallFlying();
            }

            // Apply new pose
            if (newPose == Pose.FALL_FLYING) {
                setDeltaMovement(0, 5, 0);
                startFallFlying();
            }

            super.setPose(newPose);
            lastPose = newPose;
        }
    }

    public PaperDollRenderer(float bodyYawDeg, float maxHeadYawDeg, float headYawReturnSpeed) {
        this.BODY_YAW_DEG = bodyYawDeg;
        this.MAX_HEAD_YAW_DEG = maxHeadYawDeg;
        this.HEAD_YAW_RETURN_SPEED = headYawReturnSpeed;
    }

    public void applyHealth(LivingEntity livingEntity, float health) {
        if (health == livingEntity.getHealth()) return;

        float healthDif = health - livingEntity.getHealth();
        if (healthDif < 0.0f) { livingEntity.animateHurt(10); }

        livingEntity.setHealth(health);
    }

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

    public void renderPaperDoll(
        GuiGraphics context, int x1, int y1, int x2, int y2, int scale,
        FakePlayer player, DeltaTracker tickCounter
    ) {
        renderPaperDoll(context,
            x1, y1,
            x2, y2,
            scale,
            0.0F, player, tickCounter);
    }

    public void renderPaperDoll(
        GuiGraphics context, int x1, int y1, int x2, int y2, int scale,
        float yOffset, FakePlayer player, DeltaTracker tickCounter
    ) {
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf overrideCameraAngle = new Quaternionf().rotateX((float) Math.toRadians(15.0F));
        rotation.mul(overrideCameraAngle);

        float xRot = player.getXRot();
        float xRotO = player.xRotO;
        float yRot = player.getYRot();
        float yRotO = player.yRotO;
        float yBodyRot = player.getVisualRotationYInDegrees();
        float yBodyRotO = player.yBodyRotO;
        float yHeadRot = player.getYHeadRot();
        float yHeadRotO = player.yHeadRotO;

        float entityScale = player.getScale();
        float relativeScale = scale / entityScale;
        if (player.isPassenger()) { yOffset += 0.25f; }
        Vector3f translation = new Vector3f(0.0F, player.getBbHeight() / 1.7F + yOffset * entityScale, 0.0F);

        applyEntityTransforms(player, translation, rotation);
        drawEntity(context, x1, y1, x2, y2, relativeScale, translation, rotation, overrideCameraAngle, player, tickCounter);

        player.setXRot(xRot);
        player.xRotO = xRotO;
        player.setYRot(yRot);
        player.yRotO = yRotO;
        player.setYBodyRot(yBodyRot);
        player.yBodyRotO = yBodyRotO;
        player.setYHeadRot(yHeadRot);
        player.yHeadRotO = yHeadRotO;
    }

    /** @see net.minecraft.client.gui.screens.inventory.InventoryScreen#extractRenderState(LivingEntity) **/
    public static void drawEntity(
        GuiGraphics context, int x1, int y1, int x2, int y2, float scale, Vector3f translation,
        Quaternionf rotation, @Nullable Quaternionf overrideCameraAngle, FakePlayer fakePlayer, DeltaTracker tickCounter
    ) {
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var playerRenderer = entityRenderDispatcher.getPlayerRenderer(fakePlayer);

        AvatarRenderState renderState = playerRenderer.createRenderState(fakePlayer, tickCounter.getGameTimeDeltaPartialTick(false));
        renderState.nameTag = null;
        renderState.lightCoords = 15728880;
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        context.submitEntityRenderState(renderState, scale, translation, rotation, overrideCameraAngle, x1, y1, x2, y2);
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
