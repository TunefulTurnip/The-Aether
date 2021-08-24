package com.gildedgames.aether.common.entity.passive;

import com.gildedgames.aether.Aether;
import com.gildedgames.aether.client.registry.AetherParticleTypes;
import com.gildedgames.aether.client.registry.AetherSoundEvents;
import com.gildedgames.aether.common.entity.ai.FallingRandomWalkingGoal;
import com.gildedgames.aether.common.registry.AetherEntityTypes;
import com.gildedgames.aether.common.registry.AetherItems;
import com.gildedgames.aether.core.capability.interfaces.IAetherPlayer;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.JumpController;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class AerbunnyEntity extends AetherAnimalEntity
{
    public static final DataParameter<Integer> DATA_PUFFINESS_ID = EntityDataManager.defineId(AerbunnyEntity.class, DataSerializers.INT);
    private int jumps;
    private int jumpTicks;

    public AerbunnyEntity(EntityType<? extends AnimalEntity> type, World worldIn) {
        super(type, worldIn);
    }

    public AerbunnyEntity(World worldIn) {
        this(AetherEntityTypes.AERBUNNY.get(), worldIn);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, Ingredient.of(AetherItems.BLUE_BERRY.get()), false));
        this.goalSelector.addGoal(4, new LookAtGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(5, new HopGoal(this));
        this.goalSelector.addGoal(6, new FallingRandomWalkingGoal(this, 2.0D, 6));
    }

    public static AttributeModifierMap.MutableAttribute createMobAttributes() {
        return MobEntity.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.MAX_HEALTH, 5.0D);
    }

    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PUFFINESS_ID, 0);
    }

    //TODO: Need to stop desync when entering water.
    @Override
    public void tick() {
        super.tick();
        this.setPuffiness(this.getPuffiness() - 1);
        if (this.getPuffiness() < 0) {
            this.setPuffiness(0);
        }
        if (this.getVehicle() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) this.getVehicle();
            this.yRot = player.yRot;
            this.yRotO = this.yRot;
            this.xRot = player.xRot * 0.5F;
            this.setRot(this.yRot, this.xRot);
            this.yBodyRot = this.yRot;
            this.yHeadRot = this.yBodyRot;

            player.fallDistance = 0.0F;

            if (!player.isOnGround() && !player.isFallFlying()) {
                if (!player.abilities.flying) {
                    player.setDeltaMovement(player.getDeltaMovement().add(0.0D, 0.05D, 0.0D));
                }
                IAetherPlayer.get(player).ifPresent(aetherPlayer -> {
                    if (aetherPlayer.isJumping() && player.getDeltaMovement().y < -0.225D) {
                        player.setDeltaMovement(player.getDeltaMovement().x, 0.125D, player.getDeltaMovement().z);
                        if (!this.level.isClientSide) {
                            this.puff();
                        }
                    }
                });
            }
            if (this.level instanceof ServerWorld) {
                //TODO: doesn't seem to work, might be because this is not actually spawning in the right position.
                ServerWorld serverWorld = (ServerWorld) this.level;
                double d1 = (float) this.getX() + this.random.nextFloat() * 0.1F;
                double d2 = (float) this.getY() + this.getBbHeight() + 0.125F;
                double d3 = (float) this.getZ() + this.random.nextFloat() * 0.1F;
                float f1 = this.random.nextFloat() * 360.0F;
                serverWorld.sendParticles(ParticleTypes.POOF, -Math.sin(Math.PI / 180 * f1) * 0.75D, d2 - 0.25D, Math.cos(Math.PI / 180 * f1) * 0.75D, 3, d1, 0.125D, d3, 0.0F);
            }
        }
    }

    //TODO: Organize.
    @Override
    public void aiStep() {

        if (this.onGround) {
            this.jumps = 1;
            this.jumpTicks = 10;
        }
        else if (this.jumpTicks > 0) {
            --this.jumpTicks;
        }

        if (this.jumping && !this.isInWater() && !this.isInLava() && !this.onGround && this.jumpTicks == 0 && this.jumps > 0) {
            if(this.getDeltaMovement().x != 0.0F || this.getDeltaMovement().z != 0.0F) {
                this.jumpFromGround();
            }
            this.jumpTicks = 10;
        }

        if (this.getDeltaMovement().y < -0.1D) {
            this.setDeltaMovement(getDeltaMovement().x, -0.1D, getDeltaMovement().z);
        }


        super.aiStep();
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (player.isShiftKeyDown()) {
            return this.ridePlayer(player);
        } else {
            ActionResultType result = super.mobInteract(player, hand);
            if (result == ActionResultType.PASS || result == ActionResultType.FAIL) {
                return this.ridePlayer(player);
            }
            return result;
        }
    }

    private ActionResultType ridePlayer(PlayerEntity player) {
        this.level.playSound(player, this, AetherSoundEvents.ENTITY_AERBUNNY_LIFT.get(), SoundCategory.NEUTRAL, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        if (this.isPassenger()) {
            this.navigation.recomputePath();
            this.stopRiding();
        } else {
            this.startRiding(player);
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    protected void jumpFromGround() {
        super.jumpFromGround();
        this.puff();
        --this.jumps;
    }

    private void puff() {
        this.setPuffiness(11);
        this.spawnExplosionParticle();
    }

    private void spawnExplosionParticle() {
        if (this.level instanceof ServerWorld) {
            ServerWorld world = (ServerWorld) this.level;
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;
            double d3 = 10.0D;
            double x = this.getX() + (double) (this.random.nextFloat() * this.getBbWidth() * 2.0F) - (this.getBbWidth() - d0 * d3);
            double y = this.getY() + (double) (this.random.nextFloat() * this.getBbHeight()) - d1 * d3;
            double z = this.getZ() + (double) (this.random.nextFloat() * this.getBbWidth() * 2.0F) - (this.getBbWidth() - d2 * d3);
            world.sendParticles(ParticleTypes.POOF, x, y, z, 5, d0, d1, d2, 0.0F);
        }
    }

    public int getPuffiness() {
        return this.entityData.get(DATA_PUFFINESS_ID);
    }

    public void setPuffiness(int i) {
        this.entityData.set(DATA_PUFFINESS_ID, i);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AetherSoundEvents.ENTITY_AERBUNNY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AetherSoundEvents.ENTITY_AERBUNNY_DEATH.get();
    }

    @Override
    public boolean canRiderInteract() {
        return true;
    }

    @Override //TODO: Change based on if the player is sneaking.
    public double getMyRidingOffset() {
        return 0.4D;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return (this.getVehicle() != null && this.getVehicle() == damageSource.getEntity()) || super.isInvulnerableTo(damageSource);
    }

    @Override
    public boolean isInWall() {
        return !this.isPassenger() && super.isInWall();
    }

    @Override
    protected int calculateFallDamage(float distance, float damageMultiplier) {
        return 0;
    }

    @Nullable
    @Override
    public AgeableEntity getBreedOffspring(ServerWorld p_241840_1_, AgeableEntity p_241840_2_) {
        return AetherEntityTypes.AERBUNNY.get().create(this.level);
    }




    //TODO: Rewrite and possibly edit other stuff like move controller and navigator.
    public static class HopGoal extends Goal {
        private AerbunnyEntity aerbunny;
        public HopGoal(AerbunnyEntity entity) {
            aerbunny = entity;
            setFlags(EnumSet.of(Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.aerbunny.getDeltaMovement().z > 0.0D || this.aerbunny.getDeltaMovement().x > 0.0D || this.aerbunny.onGround;
        }

        @Override
        public void tick() {
            if(aerbunny.getDeltaMovement().x != 0.0F || aerbunny.getDeltaMovement().z != 0.0F) {
                this.aerbunny.jumpControl.jump();
            }
        }
    }
}
