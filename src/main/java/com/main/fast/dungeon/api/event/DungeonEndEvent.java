package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraftforge.eventbus.api.Event;

/** 副本彻底结束时触发（数据已清理）。 */
public class DungeonEndEvent extends Event {

    public enum Reason {
        /** 全部波次完成 */
        COMPLETE,
        /** 超时 */
        TIMEOUT,
        /** 生命耗尽（阵亡次数达到上限） */
        FAILED,
        /** 脚本 / API 主动关闭 */
        STOPPED,
        /** 服务器关闭 */
        SHUTDOWN
    }

    private final DungeonInstance instance;
    private final Reason reason;

    public DungeonEndEvent(DungeonInstance instance, Reason reason) {
        this.instance = instance;
        this.reason = reason;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }

    public Reason getReason() {
        return reason;
    }
}
