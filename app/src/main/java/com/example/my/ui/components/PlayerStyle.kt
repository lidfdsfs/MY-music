package com.example.my.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.TypeScale

/**
 * 足迹配乐播放器的配色，一共六套。
 *
 * 这里**只有播放器外观**：旧的「素材整理状态条」连同它的卡片、百分比算法、
 * 预览图都已整体删除，不要再往这个模块里加任何状态条。
 */
enum class PlayerStyle(
    val id: String,
    val label: String,
    val zh: String,
    val left: Color,
    val mid: Color,
    val right: Color,
    val glow: Color
) {
    CLEAN("clean", "CLEAN", "暖红", Color(0xFF2B0D0D), Color(0xFF3D1111), Color(0xFF150606), Color(0xFFFF5A36)),
    BLUE_PURPLE("blue_purple", "BLUE PURPLE", "蓝紫", Color(0xFF111B3F), Color(0xFF241549), Color(0xFF0A0E22), Color(0xFF8B6BFF)),
    GREEN_YELLOW("green_yellow", "GREEN YELLOW", "黄绿", Color(0xFF16210B), Color(0xFF1E2C0D), Color(0xFF0A0F06), Color(0xFFB7E23E)),
    BLUE_WHITE_CYAN("blue_white_cyan", "BLUE WHITE CYAN", "青白", Color(0xFF0C2531), Color(0xFF123243), Color(0xFF07131A), Color(0xFF7FE6FF)),
    PURPLE_NAVY("purple_navy", "PURPLE NAVY", "紫蓝", Color(0xFF1B1228), Color(0xFF2C1844), Color(0xFF0B0912), Color(0xFFC27BFF)),
    ORANGE_YELLOW_GREEN("orange_yellow_green", "ORANGE YELLOW-GREEN", "橙黄", Color(0xFF2B1C0B), Color(0xFF35210C), Color(0xFF120C05), Color(0xFFFFB03A));

    companion object {
        fun byId(id: String?): PlayerStyle = entries.firstOrNull { it.id == id } ?: PURPLE_NAVY
    }
}

/** 选中的配色存 SharedPreferences：重启 App、换一条足迹都还是上次那套 */
object PlayerStyleStore {
    private const val PREFS = "player_style"
    private const val KEY = "style"
    private const val LEGACY_PREFS = "sync_strip"

    private val state = mutableStateOf(PlayerStyle.PURPLE_NAVY)
    private var loaded = false

    val current: PlayerStyle get() = state.value

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 老版本把同一套配色存在别的文件里，读一次迁移，别把用户挑好的样式弄丢
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY, null) ?: legacy.getString(KEY, null)
        state.value = PlayerStyle.byId(id)
    }

    fun select(context: Context, style: PlayerStyle) {
        state.value = style
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, style.id)
            .apply()
    }
}

/**
 * 播放器的公共底图：渐变底 → 已播区域柔光 → 电流光缝。
 * 真实播放器和样式面板的预览都走这一处，避免两处样式画歪。
 *
 * 底部那条「细轨道 + 圆点把手」已按用户要求**整体删除**：进度只靠柔光暗示，
 * 炫光与粒子交给 [PlayerMotionLayer]，拖动落点用一团柔光而不是一个点。
 */
internal fun DrawScope.drawPlayerChrome(
    style: PlayerStyle,
    fraction: Float
) {
    val f = fraction.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height

    drawRect(
        Brush.horizontalGradient(
            0f to style.left,
            0.55f to style.mid,
            0.78f to style.right,
            1f to style.right
        )
    )
    if (f > 0f) {
        val fw = w * f
        drawRect(
            brush = Brush.horizontalGradient(
                0f to style.glow.copy(alpha = 0.28f),
                0.72f to style.glow.copy(alpha = 0.13f),
                1f to style.glow.copy(alpha = 0.40f),
                startX = 0f,
                endX = fw.coerceAtLeast(1f)
            ),
            size = Size(fw, h)
        )
    }

    if (f > 0.01f) {
        drawPlayerSeam((w * f).coerceAtMost(w * 0.985f), style.glow)
    }
}

/** 手绘折线光缝：宽描边叠出泛光，再压一层白芯 */
private fun DrawScope.drawPlayerSeam(centerX: Float, glow: Color) {
    val h = size.height
    val w = size.width
    val bolt = Path().apply {
        val pts = listOf(
            0.02f to 0.008f, 0.16f to -0.014f, 0.30f to 0.013f, 0.44f to -0.016f,
            0.58f to 0.012f, 0.72f to -0.013f, 0.86f to 0.010f, 1.02f to -0.006f
        )
        pts.forEachIndexed { i, (fy, fx) ->
            val x = centerX + fx * w
            val y = fy * h
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glow.copy(alpha = 0.26f),
                glow.copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = Offset(centerX, h * 0.5f),
            radius = h * 1.8f
        ),
        radius = h * 1.8f,
        center = Offset(centerX, h * 0.5f)
    )
    listOf(10.dp to 0.10f, 6.dp to 0.22f, 3.dp to 0.45f).forEach { (stroke, alpha) ->
        drawPath(
            bolt,
            color = glow.copy(alpha = alpha),
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
        )
    }
    drawPath(
        bolt,
        color = Ink.OnCover.copy(alpha = 0.55f),
        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * 样式面板里的预览：**画的是播放器本身**（歌名 / 当前状态 / 播放进度），
 * 不再是任何状态条。和详情里那条播放器共用 [drawPlayerChrome]。
 */
@Composable
fun PlayerStylePreview(
    style: PlayerStyle,
    title: String,
    subtitle: String,
    percent: Int,
    modifier: Modifier = Modifier,
    dense: Boolean = true,
    motion: PlayerMotion = PlayerMotionStore.current
) {
    val p = percent.coerceIn(0, 100)
    val shape = RoundedCornerShape(if (dense) 12.dp else 16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(if (dense) 52.dp else 66.dp)
            .clip(shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPlayerChrome(style, p / 100f)
        }
        // 预览和真机是同一层特效，只是粒子密度减半（面板里六格同时动也不卡）
        PlayerMotionLayer(
            style = style,
            motion = motion,
            playing = true,
            progress = p / 100f,
            focus = null,
            particleScale = 0.45f,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = if (dense) 9.dp else 14.dp, end = if (dense) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { tr("配乐", "Soundtrack") },
                    color = Ink.OnCover,
                    fontSize = if (dense) TypeScale.tiny else TypeScale.subtitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Ink.OnCoverSub,
                    fontSize = TypeScale.micro,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$p",
                    color = Ink.OnCover,
                    fontSize = if (dense) TypeScale.stat else TypeScale.hero,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    "%",
                    color = Ink.OnCoverSub,
                    fontSize = TypeScale.micro,
                    modifier = Modifier.padding(start = 1.dp, bottom = if (dense) 3.dp else 6.dp)
                )
            }
            Spacer(Modifier.width(3.dp))
        }
    }
}

/**
 * 长按播放器弹出的样式选择：六套流光特效横排 + 六套配色两列排开，每格都是真实播放器预览。
 * 选特效即时写回并让六格预览一起变，选配色即保存并关面板。
 * 面板是盖在页面上的层，底色用 [Layer.Opaque]，遮罩用 [Layer.Scrim]，
 * 避免自定义背景态下底层文字穿透。
 */
@Composable
fun PlayerStylePickerSheet(
    percent: Int,
    title: String,
    subtitle: String,
    current: PlayerStyle,
    onPick: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit,
    currentMotion: PlayerMotion = PlayerMotionStore.current,
    onPickMotion: (PlayerMotion) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Layer.Scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Layer.Opaque)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg.Outline)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("播放器样式", "Player style"),
                    color = com.example.my.ui.theme.Text.Primary,
                    fontSize = TypeScale.section,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tr("挑一套配色，之后所有足迹的播放器都用它", "Pick one palette for every soundtrack player"),
                    color = com.example.my.ui.theme.Text.Muted,
                    fontSize = TypeScale.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("左右滑动跳到播放时间 · 点一下暂停/继续", "Drag to seek · tap to pause/resume"),
                    color = com.example.my.ui.theme.Text.Muted,
                    fontSize = TypeScale.micro,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("流光特效", "Light effects"),
                    color = com.example.my.ui.theme.Text.Primary,
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PlayerMotion.entries) { motion ->
                        val picked = motion == currentMotion
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (picked) Brand.Terracotta else Bg.SurfaceVariant)
                                .border(
                                    1.dp,
                                    if (picked) Brand.Terracotta else Bg.Outline,
                                    RoundedCornerShape(50)
                                )
                                .clickable { onPickMotion(motion) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                tr(motion.zh, motion.en),
                                color = if (picked) Ink.OnAccent else com.example.my.ui.theme.Text.Secondary,
                                fontSize = TypeScale.caption,
                                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(PlayerStyle.entries.chunked(2)) { pair ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { style ->
                                Column(
                                    Modifier.weight(1f).clickable { onPick(style) }
                                ) {
                                    Text(
                                        style.label,
                                        color = if (style == current) Brand.Terracotta else com.example.my.ui.theme.Text.Muted,
                                        fontSize = TypeScale.micro,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (style == current) {
                                                    Modifier.border(
                                                        1.5.dp,
                                                        Brand.Terracotta,
                                                        RoundedCornerShape(16.dp)
                                                    )
                                                } else Modifier
                                            )
                                            .padding(if (style == current) 2.dp else 0.dp)
                                    ) {
                                        PlayerStylePreview(
                                            style = style,
                                            title = title,
                                            subtitle = subtitle,
                                            percent = percent,
                                            motion = currentMotion
                                        )
                                    }
                                    if (style == current) {
                                        Text(
                                            tr("当前", "Current"),
                                            color = Brand.Terracotta,
                                            fontSize = TypeScale.micro,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
