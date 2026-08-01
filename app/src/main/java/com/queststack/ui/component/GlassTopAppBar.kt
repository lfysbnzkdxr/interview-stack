package com.queststack.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 带模糊效果的顶部栏（参考 MIUI 风格）。
 *
 * 支持运行时着色器时：以半透明 surface 铺底并对背后的页面内容做模糊；
 * 不支持时降级为纯色 surface 的 [SmallTopAppBar]，不崩溃。
 *
 * @param title 标题文本。
 * @param modifier 应用于顶栏根节点的修饰符。
 * @param navigationIcon 标题左侧的操作区内容（如返回按钮）。
 * @param actions 标题右侧的操作区内容。
 */
@Composable
fun GlassTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backdrop = LocalGlassBackdrop.current
    if (isRuntimeShaderSupported() && backdrop != null) {
        val surface = MiuixTheme.colorScheme.surface
        val colors = BlurDefaults.blurColors(
            blendColors = listOf(
                BlendColorEntry(color = surface.copy(alpha = 0.6f)),
            ),
        )
        Box(
            modifier = modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 20f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = colors,
            ),
        ) {
            // 模糊层已提供背景色，内部 SmallTopAppBar 只负责布局与标题
            SmallTopAppBar(
                title = title,
                color = Color.Transparent,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        }
    } else {
        SmallTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}
