package com.minelittlepony.unicopia.ability.magic.spell.effect;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.ability.magic.Affine;
import com.minelittlepony.unicopia.ability.magic.Caster;
import com.minelittlepony.unicopia.ability.magic.spell.CastingMethod;
import com.minelittlepony.unicopia.ability.magic.spell.Situation;
import com.minelittlepony.unicopia.ability.magic.spell.Spell;
import com.minelittlepony.unicopia.ability.magic.spell.trait.SpellTraits;
import com.minelittlepony.unicopia.ability.magic.spell.trait.Trait;
import com.minelittlepony.unicopia.entity.Living;
import com.minelittlepony.unicopia.entity.damage.UDamageTypes;
import com.minelittlepony.unicopia.particle.FollowingParticleEffect;
import com.minelittlepony.unicopia.particle.LightningBoltParticleEffect;
import com.minelittlepony.unicopia.particle.ParticleUtils;
import com.minelittlepony.unicopia.particle.UParticles;
import com.minelittlepony.unicopia.projectile.MagicProjectileEntity;
import com.minelittlepony.unicopia.projectile.ProjectileDelegate;
import com.minelittlepony.unicopia.util.shape.Sphere;

import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World.ExplosionSourceType;

/**
 * More powerful version of the vortex spell which creates a black hole.
 */
public class DarkVortexSpell extends AttractiveSpell implements ProjectileDelegate.BlockHitListener {
    public static final SpellTraits DEFAULT_TRAITS = new SpellTraits.Builder()
            .with(Trait.CHAOS, 5)
            .with(Trait.KNOWLEDGE, 1)
            .with(Trait.STRENGTH, 70)
            .with(Trait.DARKNESS, 100)
            .build();

    private static final Vec3d SPHERE_OFFSET = new Vec3d(0, 2, 0);

    private float accumulatedMass = 0;

    protected DarkVortexSpell(CustomisedSpellType<?> type) {
        super(type);
    }

    @Override
    public void onImpact(MagicProjectileEntity projectile, BlockHitResult hit) {
        if (!projectile.isClient()) {
            BlockPos pos = hit.getBlockPos();
            projectile.getWorld().createExplosion(projectile, pos.getX(), pos.getY(), pos.getZ(), 3, ExplosionSourceType.NONE);
            toPlaceable().tick(projectile, Situation.BODY);
        }
    }

    @Override
    public Spell prepareForCast(Caster<?> caster, CastingMethod method) {
        return method == CastingMethod.STAFF ? toThrowable() : toPlaceable();
    }

    @Override
    public boolean tick(Caster<?> source, Situation situation) {

        if (situation == Situation.PROJECTILE) {
            return false;
        }

        if (situation == Situation.BODY) {
            return true;
        }

        if (source.asEntity().age % 20 == 0) {
            source.asWorld().playSound(null, source.getOrigin(), USounds.AMBIENT_DARK_VORTEX_ADDITIONS, SoundCategory.AMBIENT, 1, 1);
        }

        if (!source.isClient() && source.asWorld().random.nextInt(300) == 0) {
            ParticleUtils.spawnParticle(source.asWorld(), LightningBoltParticleEffect.DEFAULT, getOrigin(source), Vec3d.ZERO);
        }

        return super.tick(source, situation);
    }

    @Override
    protected void consumeManage(Caster<?> source, long costMultiplier, float knowledge) {
        if (!source.subtractEnergyCost(-accumulatedMass)) {
            setDead();
        }
    }

    @Override
    public boolean isFriendlyTogether(Affine other) {
        return accumulatedMass < 4;
    }

    @Override
    protected boolean isValidTarget(Caster<?> source, Entity entity) {
        return EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(entity) && getAttractiveForce(source, entity) > 0;
    }

    @Override
    public void generateParticles(Caster<?> source) {
        super.generateParticles(source);

        if (getEventHorizonRadius() > 0.3) {
            double range = getDrawDropOffRange(source);
            Vec3d origin = getOrigin(source);
            source.spawnParticles(origin, new Sphere(false, range), 1, p -> {
                if (!source.asWorld().isAir(BlockPos.ofFloored(p))) {
                    source.addParticle(
                            new FollowingParticleEffect(UParticles.HEALTH_DRAIN, origin, 0.4F)
                                .withChild(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE),
                            p,
                            Vec3d.ZERO
                    );
                }
            });
        }
    }

    @Override
    public double getDrawDropOffRange(Caster<?> source) {
        return getEventHorizonRadius() * 20;
    }

    @Override
    protected Vec3d getOrigin(Caster<?> source) {
        return source.getOriginVector().add(SPHERE_OFFSET);
    }

    @Override
    protected long applyEntities(Caster<?> source) {
        if (!source.isClient()) {

            double radius = getEventHorizonRadius();

            if (radius > 2) {
                Vec3d origin = getOrigin(source);
                new Sphere(false, radius).translate(origin).getBlockPositions().forEach(i -> {
                    if (!canAffect(source, i)) {
                        return;
                    }
                    if (source.getOrigin().isWithinDistance(i, getEventHorizonRadius() / 2)) {
                        source.asWorld().breakBlock(i, false);
                    } else {
                        CatapultSpell.createBlockEntity(source.asWorld(), i, e -> {
                            applyRadialEffect(source, e, e.getPos().distanceTo(origin), radius);
                        });
                    }
                });
            }
        }

        return super.applyEntities(source);
    }

    protected boolean canAffect(Caster<?> source, BlockPos pos) {
        return source.canModifyAt(pos)
            && source.asWorld().getFluidState(pos).isEmpty()
            && source.asWorld().getBlockState(pos).getHardness(source.asWorld(), pos) >= 0;
    }

    // 1. force decreases with distance: distance scale 1 -> 0
    // 2. max force (at dist 0) is taken from accumulated mass
    // 3. force reaches 0 at distance of drawDropOffRange

    public double getEventHorizonRadius() {
        return Math.sqrt(Math.max(0.001, getMass() / 3F));
    }

    private double getAttractiveForce(Caster<?> source, Entity target) {
        return AttractionUtils.getAttractiveForce(getMass(), getOrigin(source), target);
    }

    private double getMass() {
        return Math.min(15, 0.1F + accumulatedMass / 10F);
    }

    @Override
    protected void applyRadialEffect(Caster<?> source, Entity target, double distance, double radius) {

        if (target instanceof FallingBlockEntity && source.isClient()) {
            return;
        }

        if (distance <= getEventHorizonRadius() + 0.5) {
            target.setVelocity(target.getVelocity().multiply(distance / (2 * radius)));
            if (distance < 1) {
                target.setVelocity(target.getVelocity().multiply(distance));

            }
            Living.updateVelocity(target);

            @Nullable
            Entity master = source.getMaster();

            if (target instanceof MagicProjectileEntity projectile) {
                Item item = projectile.getStack().getItem();
                if (item instanceof ProjectileDelegate.EntityHitListener p && master != null) {
                    p.onImpact(projectile, new EntityHitResult(master));
                }
            } else if (target instanceof PersistentProjectileEntity) {
                if (master != null) {
                    master.damage(master.getDamageSources().thrown(target, ((PersistentProjectileEntity)target).getOwner()), 4);
                }
                target.discard();
                return;
            }

            double massOfTarget = AttractionUtils.getMass(target);

            if (!source.isClient() && massOfTarget != 0) {
                accumulatedMass += massOfTarget;
                setDirty();
            }

            target.damage(source.damageOf(UDamageTypes.GAVITY_WELL_RECOIL, source), Integer.MAX_VALUE);
            if (!(target instanceof PlayerEntity)) {
                target.discard();
                source.asWorld().playSound(null, source.getOrigin(), USounds.ENCHANTMENT_CONSUMPTION_CONSUME, SoundCategory.AMBIENT, 2, 0.02F);
            }
            if (target.isAlive()) {
                target.damage(source.asEntity().getDamageSources().outOfWorld(), Integer.MAX_VALUE);
            }

            source.subtractEnergyCost(-massOfTarget * 10);
            source.asWorld().playSound(null, source.getOrigin(), USounds.AMBIENT_DARK_VORTEX_MOOD, SoundCategory.AMBIENT, 2, 0.02F);
        } else {
            double force = getAttractiveForce(source, target);

            AttractionUtils.applyForce(getOrigin(source), target, -force, 0, true);

            source.subtractEnergyCost(-2);
        }
    }

    @Override
    public void toNBT(NbtCompound compound) {
        super.toNBT(compound);
        compound.putFloat("accumulatedMass", accumulatedMass);
    }

    @Override
    public void fromNBT(NbtCompound compound) {
        super.fromNBT(compound);
        accumulatedMass = compound.getFloat("accumulatedMass");
    }
}
