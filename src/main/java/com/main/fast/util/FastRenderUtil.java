package com.main.fast.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font.DisplayMode;
import org.joml.Matrix4f;

public class FastRenderUtil {
    
    public static void FontdrawString(
            Font font,
            String text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f matrix,
            MultiBufferSource buffer,
            DisplayMode displayMode,
            int backgroundColor,
            int packedLight
    ) {
        font.drawInBatch(
                text,
                x,
                y,
                color,
                dropShadow,
                matrix,
                buffer,
                displayMode,
                backgroundColor,
                packedLight
        );
    }
    
}
