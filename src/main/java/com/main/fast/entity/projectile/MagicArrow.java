package com.main.fast.entity.projectile;

import com.main.fast.registry.FastEntities;
import com.main.fast_irons_spellbooks_addition.entity.spells.magic.TripleMagicMissileProjectile;
import com.main.fast_irons_spellbooks_addition.spells.physical.HeroResonanceSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 魔法箭：伤害类型为魔法/元素伤害，元素由「勇者调律」决定。
 * <p>
 * 伤害数值沿用箭矢自身伤害（原版公式 {@code 速度 * baseDamage}），
 * 但走铁魔法的 {@link DamageSources#applyDamage} 链路，因此受魔法抗性等影响。
 */
public class MagicArrow extends Arrow {

    private static final String TAG_ELEMENT = "magicElement";

    /** 总寿命：与技能箭一致，20 秒 */
    private static final int MAX_LIFE = 400;

    /** 插地后的寿命：与技能箭一致，落地 3 秒 */
    private static final int IN_GROUND_LIFE = 60;

    /** 命中时记录的勇者调律元素，供表现层（粒子/颜色）使用 */
    private TripleMagicMissileProjectile.ElementType element = null;

    /** 总寿命计数 */
    private int tickLife = 0;

    /** 插地后的寿命计数 */
    private int life = 0;

    public MagicArrow(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public MagicArrow(Level level, LivingEntity shooter) {
        this(FastEntities.MAGIC_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();

        ++this.tickLife;
        if (this.tickLife >= MAX_LIFE) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        if (target == this.getOwner()) {
            this.discard();
            return;
        }

        if (target instanceof LivingEntity livingTarget) {
            int damage = (int) this.getBaseDamage();

            LivingEntity shooter = this.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
            this.element = HeroResonanceSpell.getCurrentElement(shooter);
            Entity attacker = shooter != null ? shooter : this;

            if (shooter != null) {
                shooter.setLastHurtMob(livingTarget);
            }

            livingTarget.invulnerableTime = 0;
            DamageSources.applyDamage(livingTarget, damage, getElementDamageSource(this.element, this, attacker));
            livingTarget.invulnerableTime = 20;

            if (this.getKnockback() > 0) {
                double resistance = Math.max(0.0D, 1.0D - livingTarget.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                Vec3 push = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale((double) this.getKnockback() * 0.6D * resistance);
                if (push.lengthSqr() > 0.0D) {
                    livingTarget.push(push.x, 0.1D, push.z);
                }
            }

            this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        }

        this.discard();
    }

    /**
     * 勇者调律：按元素返回对应的学派伤害类型（与 MagicBeamSpell 一致） */
    private static SpellDamageSource getElementDamageSource(
            TripleMagicMissileProjectile.ElementType element,
            Entity projectile,
            Entity attacker
    ) {
        return switch (element) {
            case FIRE -> SpellRegistry.FIREBALL_SPELL.get().getDamageSource(projectile, attacker);
            case LIGHTNING -> SpellRegistry.BALL_LIGHTNING_SPELL.get().getDamageSource(projectile, attacker);
            case BLOOD -> SpellRegistry.BLOOD_SLASH_SPELL.get()
                    .getDamageSource(projectile, attacker)
                    .setLifestealPercent(0f); // 取消猩红伤害自带的吸血
            case HOLY -> SpellRegistry.WISP_SPELL.get().getDamageSource(projectile, attacker);
            case EVOCATION -> SpellRegistry.ARROW_VOLLEY_SPELL.get().getDamageSource(projectile, attacker);
            case NATURE -> SpellRegistry.EARTHQUAKE_SPELL.get().getDamageSource(projectile, attacker);
            case ICE -> SpellRegistry.ICICLE_SPELL.get().getDamageSource(projectile, attacker);
            case ENDER -> SpellRegistry.MAGIC_MISSILE_SPELL.get().getDamageSource(projectile, attacker);
        };
    }

    @Override
    protected @NotNull ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void tickDespawn() {
        ++this.life;
        if (this.life >= IN_GROUND_LIFE) {
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.element != null) {
            tag.putString(TAG_ELEMENT, this.element.name());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ELEMENT)) {
            try {
                this.element = TripleMagicMissileProjectile.ElementType.valueOf(tag.getString(TAG_ELEMENT));
            } catch (IllegalArgumentException ignored) {
                this.element = null;
            }
        }
    }
}