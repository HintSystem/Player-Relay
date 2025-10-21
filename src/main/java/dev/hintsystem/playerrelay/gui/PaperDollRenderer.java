package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.mixin.minecraft.EntityAccessor;
import dev.hintsystem.playerrelay.mixin.minecraft.LivingEntityInvoker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.RotationAxis;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PaperDollRenderer {
    public final float BODY_YAW_DEG;
    public final float MAX_HEAD_YAW_DEG;
    public final float HEAD_YAW_RETURN_SPEED;

    private EntityPose lastPose = EntityPose.STANDING;
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

    public void applyPoseToPlayer(PlayerEntity player, EntityPose newPose) {
        if (newPose == lastPose) return;

        // Clean up previous pose
        switch (lastPose) {
            case EntityPose.GLIDING -> {
                player.setVelocity(0, 0, 0);
                player.stopGliding();
            }
            case EntityPose.SITTING -> removeVehicle(player);
            case EntityPose.SPIN_ATTACK -> ((LivingEntityInvoker) player)
                .invokeSetLivingFlag(LivingEntityInvoker.getRiptideFlag(), false);
        }

        // Apply new pose
        switch (newPose) {
            case EntityPose.GLIDING -> {
                player.setVelocity(0, 5, 0);
                player.startGliding();
            }
            case EntityPose.SITTING -> setFakeVehicle(player);
            case EntityPose.SPIN_ATTACK -> ((LivingEntityInvoker)player)
                .invokeSetLivingFlag(LivingEntityInvoker.getRiptideFlag(), true);
        }

        player.setPose(newPose);
        lastPose = newPose;
    }

    public void applyHealth(LivingEntity livingEntity, float health) {
        if (health == livingEntity.getHealth()) return;

        float healthDif = health - livingEntity.getHealth();
        if (healthDif < 0.0f) { livingEntity.animateDamage(10); }

        livingEntity.setHealth(health);
    }

    public void setFakeVehicle(LivingEntity livingEntity) {
        if (fakeVehicle == null) {
            fakeVehicle = new Entity(EntityType.ARMOR_STAND, livingEntity.getWorld()) {
                @Override
                protected void initDataTracker(DataTracker.Builder builder) {}
                @Override
                public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
                @Override
                protected void readCustomData(ReadView view) {}
                @Override
                protected void writeCustomData(WriteView view) {}
            };
            fakeVehicle.setInvisible(true);
        }

        ((EntityAccessor)livingEntity).setVehicle(fakeVehicle);
    }

    public void removeVehicle(LivingEntity livingEntity) { ((EntityAccessor)livingEntity).setVehicle(null); }

    public void tick(LivingEntity livingEntity) {
        if (headYawEnabled) {
            headYawOffsetO = headYawOffset;

            float currentYaw = livingEntity.getYaw();
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

        if (livingEntity.isAlive()) livingEntity.tick();
    }

    public void renderPaperDoll(DrawContext context, int x1, int y1, int x2, int y2, int scale, LivingEntity livingEntity, RenderTickCounter tickCounter) {
        renderPaperDoll(context,
            x1, y1,
            x2, y2,
            scale,
            0.0F, livingEntity, tickCounter);
    }

    public void renderPaperDoll(DrawContext context, int x1, int y1, int x2, int y2, int scale, float yOffset, LivingEntity livingEntity, RenderTickCounter tickCounter) {
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf overrideCameraAngle = new Quaternionf().rotateX((float) Math.toRadians(15.0F));
        rotation.mul(overrideCameraAngle);

        float xRot = livingEntity.getPitch();
        float xRotO = livingEntity.lastPitch;
        float yRot = livingEntity.getYaw();
        float yRotO = livingEntity.lastYaw;
        float yBodyRot = livingEntity.getBodyYaw();
        float yBodyRotO = livingEntity.lastBodyYaw;
        float yHeadRot = livingEntity.getHeadYaw();
        float yHeadRotO = livingEntity.lastHeadYaw;

        float entityScale = livingEntity.getScale();
        float relativeScale = scale / entityScale;
        if (livingEntity.hasVehicle()) { yOffset += 0.25f; }
        Vector3f translation = new Vector3f(0.0F, livingEntity.getHeight() / 2.0F + yOffset * entityScale, 0.0F);

        applyEntityTransforms(livingEntity, translation, rotation);
        drawEntity(context, x1, y1, x2, y2, relativeScale, translation, rotation, overrideCameraAngle, livingEntity, tickCounter);

        livingEntity.setPitch(xRot);
        livingEntity.lastPitch = xRotO;
        livingEntity.setYaw(yRot);
        livingEntity.lastYaw = yRotO;
        livingEntity.setBodyYaw(yBodyRot);
        livingEntity.lastBodyYaw = yBodyRotO;
        livingEntity.setHeadYaw(yHeadRot);
        livingEntity.lastHeadYaw = yHeadRotO;
    }

    /** @see net.minecraft.client.gui.screen.ingame.InventoryScreen#drawEntity(DrawContext, int, int, int, int, float, Vector3f, Quaternionf, Quaternionf, LivingEntity)  **/
    public static void drawEntity(DrawContext context, int x1, int y1, int x2, int y2, float scale, Vector3f translation,
                                  Quaternionf rotation, @Nullable Quaternionf overrideCameraAngle, LivingEntity livingEntity, RenderTickCounter tickCounter) {
        EntityRenderDispatcher entityRenderDispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> entityRenderer = entityRenderDispatcher.getRenderer(livingEntity);
        EntityRenderState entityRenderState = entityRenderer.getAndUpdateRenderState(livingEntity, tickCounter.getTickProgress(false));
        entityRenderState.displayName = null;
        entityRenderState.hitbox = null;
        context.addEntity(entityRenderState, scale, translation, rotation, overrideCameraAngle, x1, y1, x2, y2);
    }

    private void applyEntityTransforms(LivingEntity livingEntity, Vector3f translation, Quaternionf rotation) {
        if (!headPitchEnabled || livingEntity.isGliding()) {
            livingEntity.setPitch(7.5f);
            livingEntity.lastPitch = 7.5f;
        }

        float defaultRotationYaw = getDefaultRotationYaw();
        if (livingEntity.getPose() == EntityPose.SLEEPING) {
            translation.add(0.0f, livingEntity.getHeight() * 2, 0.0f);

            rotation.mul(RotationAxis.NEGATIVE_X.rotationDegrees(40f));
            defaultRotationYaw = 90.0f - defaultRotationYaw;
        } else {
            defaultRotationYaw = 180.0f + defaultRotationYaw;
        }

        livingEntity.bodyYaw = livingEntity.lastBodyYaw = defaultRotationYaw;
        livingEntity.headYaw = defaultRotationYaw + headYawOffset;
        livingEntity.lastHeadYaw = defaultRotationYaw + headYawOffsetO;
    }

    private float getDefaultRotationYaw() { return (anchorPoint.x == 1) ? this.BODY_YAW_DEG : -this.BODY_YAW_DEG; }
}
