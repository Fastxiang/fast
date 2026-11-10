package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonDefinition;
import com.main.fast.dungeon.DungeonManager;
import net.minecraftforge.eventbus.api.Event;

/**
 * 副本类型注册事件（和商店的 ShopRegisterEvent 同一个用法）。
 *
 * <pre>{@code
 * NativeEvents.onEvent($DungeonRegisterEvent, event => {
 *     event.registerJson("my_dungeon", JSON.stringify(data))
 * })
 * }</pre>
 *
 * 注意：事件注册的条目属于「动态注册」，/reload 时会被清空并重新触发；
 * 启动脚本里用 FastDungeon.registerJson 注册的条目属于「静态注册」，不受 reload 影响。
 */
public class DungeonRegisterEvent extends Event {

    /** 用旧版 JS 数据结构（{mobs, list, config}）注册 */
    public void registerJson(String id, String json) {
        DungeonManager.registerDynamicJson(id, json);
    }

    /** 用现成的定义对象注册 */
    public void register(DungeonDefinition definition) {
        DungeonManager.registerDynamic(definition);
    }
}
