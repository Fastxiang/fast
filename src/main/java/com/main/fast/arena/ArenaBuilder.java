package com.main.fast.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用代码搭 BOSS 战场地：先把整块区域清成空气，再铺地板、砌围墙、嵌灯。
 *
 * <p>全部使用 flag=2（只同步客户端、不触发邻块更新），一次性建好，避免每次开战重新计算。</p>
 *
 * <p>两种风格：</p>
 * <ul>
 *   <li>{@code plain}（默认）：一个光板圆形场地 + 一圈墙；</li>
 *   <li>{@code colosseum}：圆形斗兽场，战斗平台外面套一圈环形阶梯观众席，
 *       最外圈是带灯的高墙，+Z 方向留一个门洞。</li>
 * </ul>
 */
public final class ArenaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Arena");

    /** 门洞半宽（x 方向），总宽 5 格 */
    private static final int GATE_HALF_WIDTH = 2;
    /** 门洞高度（y 方向），从地板上面 1 格开始 */
    private static final int GATE_HEIGHT = 4;

    private ArenaBuilder() {
    }

    /**
     * @param level  竞技场世界
     * @param center 地板中心（Y = {@link ArenaDimensions#FLOOR_Y}）
     */
    public static void build(ServerLevel level, BlockPos center, ArenaConfig config) {
        // 先按「两种样式里最大的那个占地」整体清一遍，再重建。
        // ⚠️ 为什么不能在各自的 buildXxx 里只清自己的范围：
        //   上一场如果是斗兽场（外径 29）、这一场换成光板（半径 24），圆盘以外那几圈观众席/外墙
        //   就没人清；反过来光板的方角（对角 √2 倍）也会露出斗兽场的圆边。
        //   而且槽位是复用的，所以「上一场的残留」一定会被下一场看到。
        wipe(level, center, config);

        if ("colosseum".equalsIgnoreCase(config.getStyle()) && config.getSeats() > 0) {
            buildColosseum(level, center, config);
        } else {
            buildPlain(level, center, config);
        }
    }

    /** 清理余量：往外多清几格，兜住爆炸/怪物把方块推出去的那一圈 */
    private static final int WIPE_MARGIN = 4;
    /** 清理余量：往上多清几格，兜住被炸飞的碎块 */
    private static final int WIPE_TOP_MARGIN = 8;
    /** 清理余量：往下多清几格（门洞地板铺到 -Y 也不用担心） */
    private static final int WIPE_BOTTOM_MARGIN = 4;

    /**
     * 把这块场地**彻底**清成空气。
     *
     * <p>范围 = 两种样式占地的并集再各向外扩 {@link #WIPE_MARGIN} 格：
     * 半径取「光板方形的对角」和「斗兽场外径」里更大的那个，竖直范围盖住最高的墙。</p>
     */
    private static void wipe(ServerLevel level, BlockPos center, ArenaConfig config) {
        int plainRadius = config.getRadius();
        // 光板是方形，对角最远 √2·r；斗兽场是圆，最远 outer
        int squareReach = (int) Math.ceil(plainRadius * Math.sqrt(2.0D));
        int outer = config.getOuterRadius();
        int maxRadius = Math.max(squareReach, outer) + WIPE_MARGIN;

        int wallHeight = config.getWallHeight();
        int top = wallHeight + Math.max(0, config.getSeats()) + WIPE_TOP_MARGIN;
        int bottom = -WIPE_BOTTOM_MARGIN;

        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = bottom; y <= top; y++) {
            for (int x = -maxRadius; x <= maxRadius; x++) {
                for (int z = -maxRadius; z <= maxRadius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.getBlockState(pos).isAir()) continue;
                    level.setBlock(pos, air, 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 光板场地（原来的样式）
    // ------------------------------------------------------------------

    private static void buildPlain(ServerLevel level, BlockPos center, ArenaConfig config) {
        int r = config.getRadius();
        int wallHeight = config.getWallHeight();

        BlockState floor = stateOf(config.getFloor(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        BlockState wall = stateOf(config.getWall(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        BlockState light = stateOf(config.getLight(), Blocks.GLOWSTONE.defaultBlockState());
        BlockState air = Blocks.AIR.defaultBlockState();

        int baseY = center.getY();
        int clearTop = baseY + wallHeight + 3;

        // 1) 清空（上一次残留 / 其他结构）
        for (int y = baseY; y <= clearTop; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    level.setBlock(center.offset(x, y - baseY, z), air, 2);
                }
            }
        }

        // 2) 地板
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                level.setBlock(center.offset(x, 0, z), floor, 2);
            }
        }

        // 3) 围墙 + 墙上的灯
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean border = Math.abs(x) == r || Math.abs(z) == r;
                    if (!border) continue;
                    boolean lamp = y == 2 && (Math.floorMod(x + z, 6) == 0);
                    level.setBlock(center.offset(x, y, z), lamp ? light : wall, 2);
                }
            }
        }

        // 4) 场地中央留个地标（方便玩家辨认出生点）
        level.setBlock(center.offset(0, 0, 0), light, 2);
    }

    // ------------------------------------------------------------------
    // 斗兽场（圆形平台 + 环形阶梯观众席 + 外墙 + 门洞）
    // ------------------------------------------------------------------

    private static void buildColosseum(ServerLevel level, BlockPos center, ArenaConfig config) {
        int r = config.getRadius();
        int seats = config.getSeats();
        int wallHeight = config.getWallHeight();
        int outer = r + seats + 1;
        int topY = wallHeight + seats + 1;

        BlockState floor = stateOf(config.getFloor(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        BlockState wall = stateOf(config.getWall(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        BlockState light = stateOf(config.getLight(), Blocks.GLOWSTONE.defaultBlockState());
        BlockState air = Blocks.AIR.defaultBlockState();

        int outerSq = outer * outer;
        int clearTop = topY + 2;

        // 1) 清空整个圆（含观众席与外墙的位置）
        for (int y = 0; y <= clearTop; y++) {
            for (int x = -outer; x <= outer; x++) {
                for (int z = -outer; z <= outer; z++) {
                    if (x * x + z * z > outerSq) continue;
                    level.setBlock(center.offset(x, y, z), air, 2);
                }
            }
        }

        // 2) 战斗平台（内圈圆盘）
        int platformSq = r * r;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > platformSq) continue;
                level.setBlock(center.offset(x, 0, z), floor, 2);
            }
        }

        // 3) 环形阶梯观众席：第 i 层在环带 (r+i-1, r+i] 上，顶面高 i 格
        for (int i = 1; i <= seats; i++) {
            int innerSq = (r + i - 1) * (r + i - 1);
            int outerRingSq = (r + i) * (r + i);
            for (int x = -(r + i); x <= r + i; x++) {
                for (int z = -(r + i); z <= r + i; z++) {
                    int d2 = x * x + z * z;
                    if (d2 <= innerSq || d2 > outerRingSq) continue;
                    for (int y = 0; y <= i; y++) {
                        level.setBlock(center.offset(x, y, z), y == i ? floor : wall, 2);
                    }
                }
            }
        }

        // 4) 外墙（外圈环带，带灯）
        int wallInnerSq = (outer - 1) * (outer - 1);
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                int d2 = x * x + z * z;
                if (d2 <= wallInnerSq || d2 > outerSq) continue;
                for (int y = 0; y <= topY; y++) {
                    boolean lamp = (y == 2 || y == topY - 2) && Math.floorMod(x + z, 6) == 0;
                    level.setBlock(center.offset(x, y, z), lamp ? light : wall, 2);
                }
            }
        }

        // 5) +Z 方向的门洞：挖穿观众席与外墙，出口用铁栅栏封死（看得见外面，出不去）
        for (int x = -GATE_HALF_WIDTH; x <= GATE_HALF_WIDTH; x++) {
            for (int y = 1; y <= GATE_HEIGHT; y++) {
                for (int z = r - 1; z <= outer; z++) {
                    level.setBlock(center.offset(x, y, z), air, 2);
                }
            }
        }
        for (int x = -GATE_HALF_WIDTH; x <= GATE_HALF_WIDTH; x++) {
            for (int z = r; z <= outer; z++) {
                level.setBlock(center.offset(x, 0, z), floor, 2);
            }
        }
        // 栅栏用 flag=3 放，才能按相邻方块自动连成一片（只有二十来格，开销可以忽略）
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int x = -GATE_HALF_WIDTH; x <= GATE_HALF_WIDTH; x++) {
            for (int y = 1; y <= GATE_HEIGHT; y++) {
                level.setBlock(center.offset(x, y, outer), bars, 3);
            }
        }

        // 6) 中央地标
        level.setBlock(center.offset(0, 0, 0), light, 2);
    }

    /** 玩家出生点（地板上方一格，往 +Z 偏一点，别和 BOSS 刷在同一个点上） */
    public static BlockPos spawnOf(BlockPos center) {
        return center.offset(0, 1, 6);
    }

    private static BlockState stateOf(String blockId, BlockState fallback) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id != null) {
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block != null) return block.defaultBlockState();
        }
        LOGGER.warn("[Fast/Arena] 未知方块 {}，改用默认方块", blockId);
        return fallback;
    }
}
