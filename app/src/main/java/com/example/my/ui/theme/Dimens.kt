package com.example.my.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 形状与间距 token（4dp 网格）
 *
 * QQ 音乐式的大圆角：卡片 16/20，封面 12，弹层 20/28，只有按钮允许胶囊。
 */
object Radius {
    val xs = 8.dp      // 角标 / 小标签
    val sm = 12.dp     // 封面缩略图 / 输入框
    val md = 16.dp     // 通用卡片
    val lg = 20.dp     // 大卡片 / 弹层
    val xl = 28.dp     // 特大容器
    val pill = 50.dp   // 胶囊按钮
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object IconSize {
    val xs = 14.dp
    val sm = 18.dp
    val md = 22.dp
    val lg = 28.dp
    val xl = 36.dp
}

/** 页面水平留白（统一对齐） */
val PagePadding = Spacing.lg
