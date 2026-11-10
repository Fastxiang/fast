package com.main.fast.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.main.fast.maid.task.MaidTasks;

/**
 * 女仆模组扩展入口：注册职业任务。
 *
 * <p>和 {@code shop.maid.LittleMaidCompat}（女仆饰品绑定）分开，各自只干一件事。
 * 注册时机在 {@code TaskManager.init()} 里，晚于原版任务、早于 KubeJS 注册，
 * 所以旧的 {@code MaidRegister.TASK} 脚本必须一起删掉，否则同名 UID 会重复。</p>
 */
@LittleMaidExtension
public class MaidExtension implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager manager) {
        MaidTasks.register(manager);
    }
}
