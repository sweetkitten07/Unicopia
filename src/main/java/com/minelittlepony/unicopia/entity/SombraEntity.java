package com.minelittlepony.unicopia.entity;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.entity.ai.ArenaAttackGoal;
import com.minelittlepony.unicopia.item.AmuletItem;
import com.minelittlepony.unicopia.item.UItems;
import com.minelittlepony.unicopia.particle.ParticleHandle;
import com.minelittlepony.unicopia.particle.ParticleHandle.Attachment;
import com.minelittlepony.unicopia.particle.ParticleSource;
import com.minelittlepony.unicopia.particle.ParticleUtils;
import com.minelittlepony.unicopia.particle.SphereParticleEffect;
import com.minelittlepony.unicopia.particle.UParticles;
import com.minelittlepony.unicopia.util.VecHelper;
import com.minelittlepony.unicopia.util.shape.Sphere;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.PounceAtTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.BirdPathNodeMaker;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

public class SombraEntity extends HostileEntity implements ArenaCombatant, ParticleSource<SombraEntity> {
    static final byte BITE = 70;
    static final int MAX_BITE_TIME = 20;
    static final Predicate<Entity> EFFECT_TARGET_PREDICATE = EntityPredicates.VALID_LIVING_ENTITY.and(EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);

    private static final TrackedData<Optional<BlockPos>> HOME_POS = DataTracker.registerData(SombraEntity.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS);

    private final ServerBossBar bossBar = (ServerBossBar)new ServerBossBar(getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS)
            .setDarkenSky(true)
            .setThickenFog(true);

    private final ParticleHandle shroud = new ParticleHandle();

    final EntityReference<StormCloudEntity> stormCloud = new EntityReference<>();

    private int prevBiteTime;
    private int biteTime;

    public static void startEncounter(World world, BlockPos pos) {
        StormCloudEntity cloud = UEntities.STORM_CLOUD.create(world);
        cloud.setPosition(pos.up(10).toCenterPos());
        cloud.setSize(1);
        cloud.cursed = true;
        world.spawnEntity(cloud);
    }

    public SombraEntity(EntityType<SombraEntity> type, World world) {
        super(type, world);
        bossBar.setStyle(BossBar.Style.NOTCHED_10);
    }

    public static DefaultAttributeContainer.Builder createMobAttributes() {
        return HostileEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 2000)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 22);
    }

    @Override
    public SombraEntity asEntity() {
        return this;
    }

    @Override
    protected Entity.MoveEffect getMoveEffect() {
        return Entity.MoveEffect.NONE;
    }

    @Override
    public boolean canAvoidTraps() {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(HOME_POS, Optional.empty());
    }

    @Override
    protected void initGoals() {
        goalSelector.add(2, new LongDoorInteractGoal(this, true));
        goalSelector.add(5, new FlyGoal(this, 1));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 18F));
        goalSelector.add(7, new LookAroundGoal(this));
        goalSelector.add(7, new WanderAroundGoal(this, 1));
        goalSelector.add(8, new PounceAtTargetGoal(this, 1.3F));
        goalSelector.add(8, new ArenaAttackGoal<>(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
        targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, false));
        targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        MobNavigation nav = new MobNavigation(this, world) {
            @Override
            protected PathNodeNavigator createPathNodeNavigator(int range) {
                nodeMaker = new BirdPathNodeMaker();
                nodeMaker.setCanEnterOpenDoors(true);
                return new PathNodeNavigator(nodeMaker, range);
            }
        };
        nav.setCanPathThroughDoors(true);
        nav.setCanSwim(true);
        nav.setCanEnterOpenDoors(true);
        nav.canJumpToNext(PathNodeType.UNPASSABLE_RAIL);
        return nav;
    }

    @Override
    public Optional<BlockPos> getHomePos() {
        return dataTracker.get(HOME_POS);
    }

    public void setHomePos(BlockPos pos) {
        dataTracker.set(HOME_POS, Optional.of(pos));
    }

    public float getBiteAmount(float tickDelta) {
        float progress = (MathHelper.lerp(tickDelta, prevBiteTime, biteTime) / (float)MAX_BITE_TIME);
        return 1 - Math.abs(MathHelper.sin(progress * MathHelper.PI));
    }

    @Override
    public void tick() {
        setPersistent();
        Optional<BlockPos> homePos = getHomePos();

        if (homePos.isEmpty() && !isRemoved()) {
            remove(RemovalReason.DISCARDED);
            return;
        }

        if (getBlockPos().getSquaredDistance(homePos.get()) > MathHelper.square(getAreaRadius())) {
            teleportTo(Vec3d.ofCenter(homePos.get()));
            getNavigation().stop();
        }

        prevBiteTime = biteTime;
        if (biteTime > 0) {
            biteTime--;
        }

        super.tick();

        if (getTarget() == null && getVelocity().y < 0) {
            setVelocity(getVelocity().multiply(1, 0.4, 1));
        }
        addVelocity(0, 0.0242F, 0);
        if (isSubmergedInWater()) {
            jump();
        }

        if (random.nextInt(1200) == 0) {
           playSound(USounds.ENTITY_SOMBRA_LAUGH, 1, 1);
           getWorld().sendEntityStatus(this, BITE);
        }

        if (random.nextInt(340) == 0) {
            playSound(SoundEvents.AMBIENT_CAVE.value(), 1, 0.3F);
        } else if (random.nextInt(1340) == 0) {
            playSound(USounds.ENTITY_SOMBRA_AMBIENT, 1, 1);
        }

        if (getWorld().isClient) {
            generateBodyParticles();
        } else {
            for (BlockPos p : BlockPos.iterateOutwards(getBlockPos(), 2, 1, 2)) {
                if (getWorld().getBlockState(p).getLuminance() > 3) {
                    destroyLightSource(p);
                }
            }

            for (BlockPos p : BlockPos.iterateRandomly(random, 3, getBlockPos(), 20)) {
                CrystalShardsEntity.infestBlock((ServerWorld)getWorld(), p);
            }

            if (getTarget() == null && getNavigation().isIdle()) {
                getNavigation().startMovingTo(homePos.get().getX(), homePos.get().getY() + 10, homePos.get().getZ(), 2);
            }
        }

        getHomePos().ifPresent(this::generateArenaEffects);
    }

    protected void applyAreaEffects(Entity target) {
        if (this.age % 150 == 0) {
            target.playSound(
                    random.nextInt(30) == 0 ? USounds.ENTITY_SOMBRA_AMBIENT
                            : random.nextInt(10) == 0 ? USounds.Vanilla.ENTITY_GHAST_AMBIENT
                            : USounds.Vanilla.AMBIENT_CAVE.value(),
                    (float)random.nextTriangular(1, 0.2F),
                    (float)random.nextTriangular(0.3F, 0.2F)
            );
        }

        ((LivingEntity)target).addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 26, 0, true, false));
        ((LivingEntity)target).addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 26, 0, true, false));

        if (getTarget() == null && target instanceof PlayerEntity player && player.distanceTo(this) < getAreaRadius() / 2F) {
            setTarget(player);
            if (teleportTo(target.getPos())) {
                setPosition(getPos().add(0, 4, 0));
            }
        }
    }

    protected void destroyLightSource(BlockPos pos) {
        getWorld().breakBlock(pos, true);
        playSound(USounds.ENTITY_SOMBRA_SNICKER, 1, 1);
    }

    protected void generateBodyParticles() {
        for (int i = 0; i < 23; i++) {
            getWorld().addParticle(ParticleTypes.LARGE_SMOKE,
                    random.nextTriangular(getX(), 8),
                    random.nextTriangular(getY(), 1),
                    random.nextTriangular(getZ(), 8),
                    0,
                    0,
                    0
                );
        }
    }

    private void generateArenaEffects(BlockPos home) {
        if (getWorld().isClient()) {
            Stream.concat(
                    new Sphere(false, getAreaRadius()).translate(home).randomPoints(random).filter(this::isSurfaceBlock).limit(80),
                    new Sphere(true, getAreaRadius()).translate(home).randomPoints(random).filter(this::isSurfaceBlock).limit(30))
                .forEach(pos -> {
                    ParticleEffect type = random.nextInt(3) < 1 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SOUL_FIRE_FLAME;
                    ParticleUtils.spawnParticle(getWorld(), type, pos, Vec3d.ZERO);
                    ParticleUtils.spawnParticle(getWorld(), type, pos, pos.subtract(getPos()).add(0, 0.1, 0).multiply(-0.013));
                });

            shroud.update(getUuid(), this, spawner -> {
                var radius = getAreaRadius();
                var center = home.toCenterPos();
                spawner.addParticle(new SphereParticleEffect(UParticles.SPHERE, 0xFF000000, 1, radius - 0.2F), center, Vec3d.ZERO);
            }).ifPresent(attachment -> {
                attachment.setAttribute(Attachment.ATTR_BOUND, 1);
                attachment.setAttribute(Attachment.ATTR_COLOR, 0xFF000000);
                attachment.setAttribute(Attachment.ATTR_OPACITY, 2.5F);
            });
        } else {
            for (Entity target : VecHelper.findInRange(this, getWorld(), home.toCenterPos(), getAreaRadius() - 0.2F, EFFECT_TARGET_PREDICATE)) {
                applyAreaEffects(target);
            }
        }
    }

    private boolean isSurfaceBlock(Vec3d pos) {
        BlockPos bPos = BlockPos.ofFloored(pos);
        return getWorld().isAir(bPos) && !getWorld().isAir(bPos.down());
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        bossBar.setPercent(getHealth() / getMaxHealth());
    }

    @Override
    public boolean shouldRender(double distance) {
        double d = 64 * getRenderDistanceMultiplier();
        return distance < d * d;
    }

    @Override
    public boolean handleFallDamage(float distance, float damageMultiplier, DamageSource cause) {
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof PlayerEntity player) {
            if (AmuletSelectors.ALICORN_AMULET.test(player)) {
                if (!getWorld().isClient) {
                    playSound(USounds.ENTITY_SOMBRA_SNICKER, 1, 1);
                    player.sendMessage(Text.translatable("entity.unicopia.sombra.taunt"));
                }
            }
            ItemStack amulet = AmuletItem.getForEntity(player);
            if (amulet.isOf(UItems.ALICORN_AMULET)) {
                amulet.decrement(1);
            }
        }
        boolean damaged = super.damage(source, amount);

        if (source.getAttacker() instanceof PlayerEntity player) {
            teleportRandomly(16);
        }

        return damaged;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (!dead) {
            stormCloud.ifPresent(getWorld(), cloud -> {
                cloud.setStormTicks(0);
                cloud.setDissipating(true);
            });
            stormCloud.set(null);
            getHomePos().ifPresent(home -> {
                VecHelper.findInRange(this, getWorld(), home.toCenterPos(), getAreaRadius() - 0.2F, e -> e instanceof CrystalShardsEntity).forEach(e -> {
                    ((CrystalShardsEntity)e).setDecaying(true);
                });
            });
        }
        super.onDeath(damageSource);
    }

    @Override
    protected void fall(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        return super.canTarget(target) && getHomePos().filter(home -> target.getPos().isInRange(home.toCenterPos(), getAreaRadius())).isPresent();
    }

    @Override
    public boolean tryAttack(Entity target) {
        getWorld().sendEntityStatus(this, BITE);
        return super.tryAttack(target);
    }

    @Override
    public void handleStatus(byte status) {
        if (status == BITE) {
            biteTime = MAX_BITE_TIME;
        } else {
            super.handleStatus(status);
        }
    }

    protected boolean teleportRandomly(int maxDistance) {
        if (getWorld().isClient() || !isAlive()) {
            return false;
        }
        return teleportTo(getPos().add(VecHelper.supply(() -> random.nextTriangular(0, maxDistance))));
    }

    @Override
    public boolean teleportTo(Vec3d destination) {
        Vec3d oldPos = getPos();
        if (canTeleportTo(destination) && teleport(destination.x, destination.y, destination.z, true)) {
            getWorld().emitGameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Emitter.of(this));
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean canTeleportTo(Vec3d destination) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(destination.x, destination.y, destination.z);
        while (mutable.getY() > getWorld().getBottomY() && !getWorld().getBlockState(mutable).blocksMovement()) {
            mutable.move(Direction.DOWN);
        }
        BlockState destinationState = getWorld().getBlockState(mutable);
        return destinationState.blocksMovement() && !destinationState.getFluidState().isIn(FluidTags.WATER);
    }

    @Override
    @Deprecated
    public float getBrightnessAtEyes() {
        return super.getBrightnessAtEyes() * 0.2F;
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        getHomePos().map(NbtHelper::fromBlockPos).ifPresent(pos -> {
            nbt.put("homePos", pos);
        });
        nbt.put("cloud", stormCloud.toNBT());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("homePos", NbtElement.COMPOUND_TYPE)) {
            setHomePos(NbtHelper.toBlockPos(nbt.getCompound("homePos")));
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
        stormCloud.fromNBT(nbt.getCompound("cloud"));
    }
}
