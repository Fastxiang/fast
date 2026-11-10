package com.main.fast.maid;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * 女仆职业。一个职业 = 一个 {@link com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask}
 * 的 UID + 它该用哪一类技能 + 它的基础攻击走哪条线。
 *
 * <p>UID 与旧 KubeJS 注册的完全一致（fast:physical / fast:magic / fast:bow / fast:shield / fast:support），
 * 这样已经存档的女仆职业不会丢。</p>
 *
 * <p><b>攻击技能池按 KubeJS 的技能标签（{@code skill_tag.js}）划分</b>，不再只看单一分类：</p>
 * <ul>
 *     <li>战士 = {@code physical} + {@code melee}</li>
 *     <li>游侠 = {@code physical} + {@code projectile}</li>
 *     <li>法师 = {@code magic}</li>
 * </ul>
 * <p>守卫（嘲讽）与牧师（治疗）暂时还走分类 + 专用标签，见 {@link #skillTags} 为空时的回落。</p>
 */
public enum MaidJob {

    /** 战士：近战。带勇者之剑 + 物理普攻武器时走物理连段法术，否则普通攻击 */
    WARRIOR("physical", MaidSkillRegistry.Category.PHYSICAL, "physical", true, "physical", "melee"),
    /** 法师：远程法术，普攻是基础魔法法术 */
    MAGE("magic", MaidSkillRegistry.Category.MAGIC, "magic", false, "magic"),
    /** 游侠：弓箭系（标签 bow）+ 无箭射击 */
    RANGER("bow", MaidSkillRegistry.Category.ARROW, "arrow", false, "physical", "bow"),
    /** 守卫：举盾、嘲讽、拉开距离，不主动输出（技能按 guard=防护 标签选：嘲讽 / 完善守备） */
    GUARDIAN("shield", MaidSkillRegistry.Category.TANK, null, false, "guard"),
    /** 牧师：治疗与增益优先（heal 标签），没有治疗目标时用魔法补伤害 */
    PRIEST("support", MaidSkillRegistry.Category.MAGIC, "magic", false);

    private final String path;
    private final MaidSkillRegistry.Category skillCategory;
    private final String basicSlot;
    private final boolean melee;
    /** 攻击技能必须同时具备的标签；为空表示仍按 {@link #skillCategory} 判定 */
    private final Set<String> skillTags;

    MaidJob(String path, MaidSkillRegistry.Category skillCategory, String basicSlot, boolean melee,
            String... skillTags) {
        this.path = path;
        this.skillCategory = skillCategory;
        this.basicSlot = basicSlot;
        this.melee = melee;
        this.skillTags = Set.of(skillTags);
    }

    public ResourceLocation getUid() {
        return new ResourceLocation("fast", this.path);
    }

    public String getPath() {
        return this.path;
    }

    public MaidSkillRegistry.Category getSkillCategory() {
        return this.skillCategory;
    }

    /**
     * 这个职业能不能把某个技能当成「攻击技能」用。
     *
     * <p>有标签要求的职业必须同时具备全部标签（如战士要 {@code physical} + {@code melee}）；
     * 没有标签要求的职业回落到单一分类（守卫的 tank）。</p>
     */
    public boolean acceptsAttackSkill(ResourceLocation spellId) {
        if (this.skillTags.isEmpty()) {
            return MaidSkillRegistry.getCategory(spellId) == this.skillCategory;
        }
        for (String tag : this.skillTags) {
            if (!MaidSkillRegistry.hasTag(spellId, tag)) {
                return false;
            }
        }
        return true;
    }

    /** 基础攻击法术所在的槽位；守卫没有 */
    public String getBasicSlot() {
        return this.basicSlot;
    }

    public boolean isMelee() {
        return this.melee;
    }

    /** 是否需要一个攻击目标（守卫只用来放嘲讽与后撤，不主动输出） */
    public boolean isAggressive() {
        return true;
    }

    /** 是否会把技能甩给敌人 */
    public boolean usesOffensiveSkills() {
        return this == WARRIOR || this == MAGE || this == RANGER || this == PRIEST;
    }
}
