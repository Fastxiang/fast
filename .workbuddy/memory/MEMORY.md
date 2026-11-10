# fast 模组 · 长期约定（精简版）

## 定位 / 依赖
实例 `D:/Minecraft/异界随行诗/.minecraft/versions/otherworld_traveling_poems` 核心 mod。前置 `fast_irons_spellbooks_addition-*`
（jar 同步 `libs/` 与实例 `mods/`）、`libs/ApricityUI-*.jar`。软依赖全走反射桥（PinTooltipsCompat / ApricityCompat /
CreatePackages / SkillsXp）。灾变包在实例 `mods/[灾变] L_Enders_Cataclysm-3.31.jar`。

## 构建 / 部署
- `./gradlew build --offline --console=plain --no-daemon`，正常 25~35 秒；一次只跑一个；输出别接 grep/head 管道。
- **DSH 沙箱下 `./gradlew` 不可用**：`GRADLE_USER_HOME=<项目>\.gradle\sandbox-gh`，直接调
  `C:\Users\ZhuanZ（无密码）\.gradle\wrapper\dists\gradle-8.8-bin\...\bin\gradle.bat build`；`sandbox-gh\gradle.properties`
  必须 `org.gradle.vfs.watch=false`，否则静默卡死。卡住：杀 java → 删 `build`、`.gradle/vcs-1`、`file-access.*`、
  `journal-1.lock`。沙箱只可写项目目录，中间文件放 `build\`。
- `mixin.env.remapRefMap=false`：@Inject/@Redirect 的 method/target 要么写 SRG 名，要么整个 @Mixin `remap=false`；
  Mojang 名 + 默认 remap 会运行期静默不生效。SRG 必须 javap 核对（`~/.gradle/jdks/eclipse_adoptium-21-*/bin/javap.exe`）：
  `Level#destroyBlock`=m_46953_、`DamageSource#getDirectEntity`=m_7640_、`setNoGravity`=m_20242_、
  `setDeltaMovement`=m_20256_/m_20334_。
- 部署：BUILD SUCCESSFUL 后 `cp build/libs/fast-1.20.1-forge-0.0.1.jar <实例>/mods/` + 双端 hash；
  **换 jar 必须完全退出游戏**。

## 副本系统（Java 跑，JS 只注册）
`com/main/fast/dungeon/`：DungeonManager / DungeonInstance / DungeonKeys / DungeonSpawner / DungeonRewards /
DungeonSpellNbt / DungeonHooks / DungeonCommand / compat/{CreatePackages,SkillsXp} / api/FastDungeon。
- 注册 `startup_scripts/Instance/config.js`：双写 `global.dungeonData` + `$FastDungeon.registerJson`；动态用 `DungeonRegisterEvent`。
- 副本内禁物品：`FastDungeon.isInDungeon(player)`（ACTIVE 100 格内且同维度 + 竞技场维度），别用 `hasInstanceNear`。
- 返回点绝不默认主世界：竞技场 `ArenaLastEntry`，普通副本 `run.originDimension/originPos`。
- **KubeJS `entity.persistentData` ≠ Java `getPersistentData()`**：副本数据只走 `api/DungeonMobData`（ForgeData 键 `FastDungeon`）。
- 生命制：每人 3 条 + `State.FAILING` + 1200 tick 等待；`releaseParticipants` 任何结束都还原重生点、只有失败才传送；
  死亡界面者走 `PENDING_RETURN` + `PlayerRespawnEvent`。失败/通关等待都是「空手潜行右键方块」跳过。
- 竞技场重生点不算历史：`TRANSIENT_RESPAWN_DIMENSIONS`（`boss_arena`），真实重生点另存 `ArenaRespawnOrigin`；
  必须先 `captureParticipant` 再改重生点，`start(...,false,false)` + `adoptParticipants(...,false)`。
- 奖励包裹走 Create 反射（`PackageItem.containing` + `PackageEntity` + `setBox`），9 格/包，无 Create 回落掉物品。
- 存活判定一律全维度 `DungeonManager.hasLiveArenaMobs(server, instance)`，别用 `instance.getArenaMobs()`。
- `reclaimStrayMobs` 每 10 tick：横向/竖向 1.75×NEARBY_RADIUS、低于地板 16 格；跨维度先 `setPortalCooldown(0)` + `changeDimension`。
- `onMobDeath` 用 ForgeData `FastDungeonCountedDead` 防重复结算。
- 竞技场清场：`ArenaBuilder.build` 先 `wipe`（半径 `max(ceil(r√2),outer)+4`，竖直 `-4..wall+seats+8`）再建样式。

## 难度系统
`com.main.fast.difficulty`：MobLevelDef / MobLevelRegistry / MobLevels / DifficultyHooks(`EntityJoinLevelEvent`, HIGHEST) /
api/FastDifficulty；表 `startup_scripts/entity/mob_level.js`。
- 等级写 ForgeData `MobLevel`；追踪条件必须与 JS isMonster 一致（`!category.isFriendly()` + 黑名单 + 主人非玩家 + 排除玩家），
  不能用 `instanceof Monster`（本模组 Boss 都是 PathfinderMob）。
- 属性 max_health / attack_damage / `fast:defense` / magic_attack / `fast:str|agi|int|vit`；hp=20+6*(lv-1)、atk=35+(lv-1)。

## 竞技场维度
`fast:boss_arena`：虚空超平坦 + 末地天空 + `fixed_time 6000` + 不刷怪；每场一槽位间距 512，`FLOOR_Y=64`。
- 注册 `startup_scripts/Instance/arena.js` → `$FastArena.registerDungeon(id, JSON)`；默认灯具 `glowstone`；
  样式 `plain`/`colosseum`（外径 = radius + seats + 1，别超 ~35，广播 AABB 只有 50）。
- 流程：`DungeonKeys` → `shouldInstance`（**只对未首通**）→ `startFirstClear`（建场地→记返回/重生→改重生点→传送→
  `start` + `setDeferRewards`）；`finishRun` 挂 3 秒 `PENDING` → 收掉落 → 送人 → 原点投奖励。
- 存活判定每 200 tick；空场 `cleanupIfEmpty`（宽限 `EMPTY_GRACE=40`、`DEATH_GRACE=200` 别删）。
- `ArenaProtection` **只拦玩家**：`BreakSpeed=0` + `BreakEvent`；别整块取消 `RightClickBlock`（铁魔法/TACZ 会哑）；
  爆炸走 `ExplosionEvent.Detonate` 清 affectedBlocks。
- 场地只有 1 层地板 + 环形墙（wallHeight 6~9），**没有顶**；竖直 leash 上限是 175 格（等于没有）。

## 灾变 boss（3.31，fast43-46）
- 破坏方块全走 `Level#destroyBlock(pos, false, this)`（m_46953_），且都先判 `ForgeEventFactory.getMobGriefingEvent`
  → `EntityMobGriefingEvent` 可以 DENY。Ignis：blockbreak/ShieldSmashDamage/UltimateAttack；Netherite：
  ChargeBlockBreaking/berserkBlockBreaking（removeBlock + Cm_Falling_Block_Entity）；Harbinger：blockbreak/destroyBlock/
  destoryblock2；Remnant：tick/Charge。
- 四只都有 immune tag（ignis / harbinger / netherite_monstrosity / remnant），交集含 barrier、bedrock、reinforced_deepslate。
- 真飞行只有 **The Harbinger**（FlyingMoveControl + FlyingPathNavigation）；Ignis/Netherite 是跳跃冲撞，Remnant 钻地。
- 灾变 mixin 放 `fast.cataclysm.mixins.json`（required=false），编译期 classpath 无灾变属正常噪音。
- ⚠️ `BlockEvent.BreakEvent` 只在 `ServerPlayerGameMode` 触发（玩家），**mob 破坏不经过**；`event.getPlayer()` 对 mob 恒 null。

## Jade / 人形皮肤 / 刷怪顺序
- Jade 全在 Java `compat/jade/`：FastJadeInfo + FastJadeServerData(`IServerDataProvider<EntityAccessor>`，只发注视这只) + FastJadePlugin；旧 JS 整包同步已删。
- `HumanoidModel.renderToBuffer` 只画 `headParts()+bodyParts()`，玩家网格的 jacket/袖子/裤腿挂 root 不渲染 → 改挂 body/arm/leg
  （见 `entity/client/PhantomHeroLayers`）。
- 刷怪：Java `DifficultyHooks`(HIGHEST) 上等级 → `DungeonSpawner` 写 `DungeonMobData` → JS `entity/spawn.js`(HIGH)
  首进世界跑 `NewMobEntityEnderBonus` → `Rpg` → 回满血。

## KubeJS 纪律 / 商店 / 晴雪UI
- 全局作用域共享：顶层 `const`/`let` 重名 → redeclaration，**整个文件不加载**；引用 Java 类加后缀（`$FastDungeonApi`）或统一 `var`；
  写前用 **Grep 工具**查重名（别用 shell `grep "\s"`）。排错看 `logs/latest.log` 的「Loaded script」缺哪个。
- 商店顺序：`ShopManager.setCategoryOrder`(ShopRegisterEvent) → `PacketSyncShopData` → `ClientShopCache` →
  `FastShopUI.getDefaultOrder()`；玩家拖拽按 UUID 存 `config/fast/shop_order/<uuid>.txt`；优先级 玩家→作者→注册顺序。
  拖拽只认 `event.target`（AUI 无 `elementFromPoint`），class 按空格切词，虚影 `position:fixed` + `transform`。
- 晴雪UI：实例 `<实例>/apricity/fast/shop.*` 会盖掉 jar 内 `assets/apricityui/.../shop.*`，**两处都要改**；`autoReload` 全 false
  （重启或 **END** 重载）；页面 `mode=gui`（640x331），尺寸用 JS 内联钳制别用 `vh/vw`；`aui-mouse-events` 必须 `none`；
  改前先读 `UI-CONVENTIONS.md`。
- **页面 z 分层 vs 原版 tooltip**：`Base.resolveFlatDocumentBaseZ` = `1.0 + 第几份扁平文档 × 251.25`，`<item>` 再加
  150/200/250.25。商店前面还有 1 份扁平文档（HUD/toast）时 → 物品 z=402.25 > 原版 tooltip 的 400 → 深度 LEQUAL 把
  tooltip 在物品像素上判负（「tooltip 被物品压住 / 戳洞」，面板却没事，因为 252 < 400）。`RenderType.gui`/`text` 会
  自己重新打开 LEQUAL，`RenderSystem.disableDepthTest()` 对它俩无效。正解：页面画完 → `graphics.flush()` →
  `depthMask(true)` → `RenderSystem.clear(GL_DEPTH_BUFFER_BIT)`，再画 tooltip（`ShopTooltipState#clearPageDepth`），
  这样 Pin Tooltips 钉住那份（z≈401）也一起好了。

## 女仆系统（AI 在 Java，数据由 JS 推）
`com.main.fast.maid`：api/FastMaid（JS 唯一入口）/ MaidSkillRegistry / MaidCombatPlanner / brain/MaidCombatBehavior /
task/MaidTaskBase+MaidTasks / event/* / MaidExtension / MaidCommand。
- UID `fast:physical|magic|bow|shield|support`，`fast:physical2` 是兼容壳。
- **Java 不能读 KubeJS global**：`registerSpellType`/`addSkillTag`/`PushMaidCombatData` 单向 JSON 写进 Java。
- 基础攻击法术唯一判断在 `magic_common.js` 的 `getBasicAttackSpell`；连段段数读 `getRecastCount()`，结束必须
  `getPlayerRecasts().removeRecast(spellId)`。非玩家施法者起手前 `ComboSpell.resetCombo(caster)`。
- 女仆 `ENTITY_REACH=2`，统一走 `FastAttributeUtil.getAttackRange()`（女仆 +1）；战士走位 `MaidComboPositionTask`：
  有勇者之剑 2.6 格、<1.6 后退；没剑 1.2 贴脸；基础攻击是 `triple_slash` 时改 6~11 格。别挂
  `SetWalkTargetFromAttackTargetIfTargetOutOfReach`。
- 吟唱推进在 `PathfinderMobMixin#customServerAiStep`（行为先跑、吟唱后推进）；`MaidCombatBehavior.pumpCast` 是看门狗
  （剩余≤0 补 onCast、4 tick 不推进接管、120 tick 强收）。连段只在目标死亡/丢失或段数打完中断，距离不打断。
  ⚠️ 诊断日志不要加一次性守卫（会吃掉第 2/3 段输出）。
- 编译依赖 touhou-little-maid 1.5.3，`libs/maven` 本地镜像（cursemaven 会超时）；`isCasting()` 来自 mixin，编译期用
  `IMagicEntity`；仇恨表在 JS（`MobHateValue`）；`PhantomCantor` 主人放宽到 `LivingEntity`。

## 已删除
深蓝试炼（肉鸽）整包已删（`com.main.fast.rogue` 等），别再找。
