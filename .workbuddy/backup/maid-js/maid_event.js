// priority: 1000

MaidRegister.TASK
    .rangedAttackTask("fast:bow", "fast:the_hero_bow")
    .isWeapon((maid, stack) => {
        return ItemisBowsOfCrossbows(stack)
    })
    .chargeDurationTick(12)
    .walkSpeed(0.1)
    .searchRadius(48)
    .projectileRange(20)
    .enable(maid => maid.favorabilityManager.getLevel() >= 1)
    .addEnableConditionDesc("need_level_1", maid => maid.favorabilityManager.getLevel() >= 1)
    .performRangedAttack((maid, target, distanceFactor) => {
        let x = target.getX() - maid.getX();
        let y = target.getEyeY() - maid.getEyeY();
        let z = target.getZ() - maid.getZ();
        let maidData = maid.getPersistentData()
        if (!maidData.useCost) maidData.useCost = 0
        let useCost = maidData.useCost
        let skillObj = getEntityCustomData(maid, "skillList")
        let ArrowSkillObj = getArrowSkillObjFromSkillObj(skillObj)
        let ArrowSpellList = Object.keys(ArrowSkillObj)
        if (ArrowSpellList) {
            let ArrowSkill = ArrowSpellList[useCost]
            if (ArrowSkill && !maid.isCasting()) {
                let ArrowSkillLevel = ArrowSkillObj[ArrowSkill]
                if (!maid.isCasting()) {
                    mobOverLimitSpellCastWithCooldown(ArrowSkill, maid, ArrowSkillLevel)
                    maidData.useCost += 1
                }
            }
        }
        if (!maid.isCasting()) {
            let ArrowEntity = $ProjectileUtil.getMobArrow(maid, Item.of("minecraft:arrow"), distanceFactor)
            ArrowEntity.setNoGravity(true)
            ArrowEntity.shoot(x, y * 0.8, z, 1.5, 0)
            ArrowEntity.setBaseDamage(4)
            ArrowEntity.spawn()
            maid.mainHandItem.hurtAndBreak(1, maid, m => { });
        }
        if (ArrowSpellList && useCost >= ArrowSpellList.length) {
            maidData.useCost = 0
        }
    });

MaidRegister.TASK
    .rangedAttackTask("fast:shield", "fast:the_hero_shield")
    .isWeapon((maid, stack) => {
        return true
    })
    .chargeDurationTick(60)
    .walkSpeed(0.3)
    .searchRadius(48)
    .projectileRange(10)
    .addBrain(1, (task, maid) => new $MaidUseShieldTask())
    .performRangedAttack((maid, target, distanceFactor) => {
        let Owner = maid.getOwner();
        if (!Owner) return
        let item = maid.getMainHandItem();
        // if (item) {
        // if (hasSpecialProperty(item, "collectorsreap:lime_popsicle")) {
        // ShieldStanceItemEvent(Owner, maid, MaidPersistentData)
        // }
        // }
    });

MaidRegister.TASK
    .rangedAttackTask("fast:magic", "fast:the_hero_staff")
    .isWeapon((maid, stack) => {
        let type = getBasicAttackType(maid)
        if (type.magic) {
            return true
        }
        return false
    })
    .chargeDurationTick(8)
    .walkSpeed(0.1)
    .searchRadius(48)
    .projectileRange(20)
    .performRangedAttack((maid, target, distanceFactor) => {
        let Owner = maid.getOwner();
        if (!Owner) return
        let item = maid.getMainHandItem();
        let maidData = maid.getPersistentData()
        if (!maidData.useCost) maidData.useCost = 0
        let useCost = maidData.useCost
        let MaidMagicAttack = "fast_irons_spellbooks_addition:triple_magic_missile";
        let int = maid.getAttribute(`fast:int`).getValue();
        let skillObj = getEntityCustomData(maid, "skillList")
        let MagicSkillObj = getMagicSkillObjFromSkillObj(skillObj)
        let MagicSpellList = Object.keys(MagicSkillObj)
        let TeleportSkillObj = getTeleportSkillObjFromSkillObj(skillObj)
        if (MagicSpellList.length > 0) {
            if (TeleportSkillObj) {
                let teleportSkill = Object.keys(TeleportSkillObj)[0]
                let teleportLevel = TeleportSkillObj[teleportSkill]
                let targetpos = target.position()
                let maidpos = maid.position()
                if (targetpos.distanceTo(maidpos) <= 5) {
                    maid.getBrain().setMemory($MemoryModuleType.ATTACK_TARGET, Owner)
                    mobOverLimitSpellCastWithCooldown(teleportSkill, maid, teleportLevel);
                    maid.getBrain().setMemory($MemoryModuleType.ATTACK_TARGET, target)
                }
            }
            let MagicSkill = MagicSpellList[useCost]
            if (MagicSkill && !maid.isCasting()) {
                let SkillLevel = MagicSkillObj[MagicSkill]
                mobOverLimitSpellCastWithCooldown(MagicSkill, maid, SkillLevel)
                maidData.useCost += 1
            }
        }
        let AttackLevel = 1;
        if (!maid.isCasting()) {
            let BasicMagicSkillObj = getBasicMagicSkillObjFromSkillObj(skillObj)
            let BasicMagicSpellList = Object.keys(BasicMagicSkillObj)
            if (BasicMagicSpellList.length > 0) {
                MaidMagicAttack = BasicMagicSpellList[Math.floor(Math.random() * BasicMagicSpellList.length)]
                AttackLevel = BasicMagicSkillObj[MaidMagicAttack]
            }
            mobOverLimitSpellCast(MaidMagicAttack, maid, AttackLevel);
        }
        if (MagicSpellList && useCost >= MagicSpellList.length) {
            maidData.useCost = 0
        }
    });

MaidRegister.TASK
    .meleeTask("fast:physical", "fast:the_hero_sword")
    .isWeapon((maid, stack) => {
        return true
    })
    .meleeCooldownTick(20)

MaidRegister.TASK
    .rangedAttackTask("fast:physical2", "fast:the_hero_sword")
    .isWeapon((maid, stack) => {
        let type = getBasicAttackType(maid)
        if (type.physical) {
            return true
        }
        return false
    })
    .chargeDurationTick(8)
    .walkSpeed(0.3)
    .searchRadius(48)
    .projectileRange(6)
    .performRangedAttack((maid, target, distanceFactor) => {
        let Owner = maid.getOwner();
        if (!Owner) return
        let item = maid.getMainHandItem();
        let maidData = maid.getPersistentData()
        if (!maidData.useCost) maidData.useCost = 0
        let useCost = maidData.useCost
        let MaidMagicAttack = "fast_irons_spellbooks_addition:triple_strike";
        let str = maid.getAttribute(`fast:str`).getValue();
        let skillObj = getEntityCustomData(maid, "skillList")
        let BuffSpellObj = getBuffSkillObjFromSkillObj(skillObj)
        let BuffSpellList = Object.keys(BuffSpellObj)
        if (BuffSpellList) {
            let BuffSkill = BuffSpellList[useCost]
            if (BuffSkill && !maid.isCasting()) {
                let BuffSkillLevel = BuffSpellObj[BuffSkill]
                if (!maid.isCasting()) {
                    mobOverLimitSpellCastWithCooldown(BuffSkill, maid, BuffSkillLevel)
                    maidData.useCost += 1
                }
            }
        }
        let AttackLevel = 1;
        if (!maid.isCasting()) {
            mobOverLimitSpellCast(MaidMagicAttack, maid, AttackLevel);
        }
        if (BuffSpellList && useCost >= BuffSpellList.length) {
            maidData.useCost = 0
        }
    });