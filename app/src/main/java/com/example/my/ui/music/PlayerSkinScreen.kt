package com.example.my.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.i18n.tr
import com.example.my.ui.icons.Lucide
import com.example.my.ui.theme.PlayerAnim
import com.example.my.ui.theme.PlayerSkin
import com.example.my.ui.theme.PlayerSkinLayout
import com.example.my.ui.theme.PlayerSkinStore
import com.example.my.ui.theme.SkinGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 装扮中心的中性底：深灰，让每张皮肤卡自己的配色跳出来 */
private val SkinSheetBg = Color(0xFF0E1015)
private val SkinSheetChip = Color(0xFF23262E)
private val SkinSheetInk = Color(0xFFF2F5F9)
private val SkinSheetSub = Color(0xFF8C96A5)

/**
 * 播放器样式 / 装扮中心。
 *
 * 参考稿只是个长相参考：这里的皮肤是自建的，每张卡的预览用的就是那套皮肤真实的
 * 背景（纯色渐变或自绘底图）、唱片渐变、真版式（HUD / 海报 / 环绕 / 双栏 / 原版）
 * 和强调色，点一下直接把 [PlayerSkin] 写进播放器，返回播放页立刻是新的样子。
 */
@Composable
fun PlayerSkinScreen(
    current: PlayerSkin,
    onClose: () -> Unit,
    onPick: (PlayerSkin) -> Unit
) {
    // 这是一整页覆盖层：返回键只应该关掉本页回到播放器，
    // 不能漏给下层（原来没挂 BackHandler，按返回会直接退出整个播放页）
    BackHandler { onClose() }
    var filter by remember { mutableStateOf<SkinGroup?>(null) }
    val shown = PlayerSkin.entries.filter { filter == null || it.group == filter }
    val accent = current.accent

    Column(
        Modifier
            .fillMaxSize()
            .background(SkinSheetBg)
            .statusBarsPadding()
    ) {
        Box(
            Modifier.fillMaxWidth().height(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = tr("返回", "Back"),
                    tint = SkinSheetInk,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SkinSheetChip)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Text(
                    tr("播放器样式", "Player styles"),
                    color = SkinSheetInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Text(
            tr(
                "挑一套皮肤，点一下立即换上 · 当前「${current.zh}」",
                "Pick a skin — tap to apply · now \"${current.en}\""
            ),
            color = SkinSheetSub,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkinFilterChip(tr("全部", "All"), filter == null, accent) { filter = null }
            SkinGroup.entries.forEach { group ->
                SkinFilterChip(tr(group.zh, group.en), filter == group, accent) {
                    filter = if (filter == group) null else group
                }
            }
        }

        // 动态皮肤的动画总开关：默认开，关掉后动画层停在静止帧，播放页更省电
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SkinSheetChip)
                .clickable {
                    PlayerSkinStore.setAnimationsEnabled(ctx, !PlayerSkinStore.animationsEnabled)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr("动态皮肤动画", "Dynamic skin animation"),
                    color = SkinSheetInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    tr("关闭后画面静止，更省电", "Off keeps it still to save power"),
                    color = SkinSheetSub,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Switch(
                checked = PlayerSkinStore.animationsEnabled,
                onCheckedChange = { PlayerSkinStore.setAnimationsEnabled(ctx, it) }
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(shown, key = { it.id }) { skin ->
                PlayerSkinCard(
                    skin = skin,
                    selected = skin == current,
                    onClick = { onPick(skin) }
                )
            }
        }
    }
}

@Composable
private fun SkinFilterChip(
    label: String,
    on: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) accent else SkinSheetChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (on) Color.White else Color(0xFFB6BFCC),
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1
        )
    }
}

private fun layoutZh(layout: PlayerSkinLayout): String = when (layout) {
    PlayerSkinLayout.STAGE -> "原版"
    PlayerSkinLayout.HUD -> "HUD"
    PlayerSkinLayout.POSTER -> "海报"
    PlayerSkinLayout.ORBIT -> "环绕"
    PlayerSkinLayout.DUO -> "双栏"
}

private fun layoutEn(layout: PlayerSkinLayout): String = when (layout) {
    PlayerSkinLayout.STAGE -> "Classic"
    PlayerSkinLayout.HUD -> "HUD"
    PlayerSkinLayout.POSTER -> "Poster"
    PlayerSkinLayout.ORBIT -> "Orbit"
    PlayerSkinLayout.DUO -> "Duo"
}

/**
 * 皮肤卡：一张卡就是一台迷你播放器 —— 底、版式、唱片、控制条，
 * 全部用这套皮肤真实的底图和配色渲染，所以卡里看到的和换上之后是同一台机器。
 */
@Composable
private fun PlayerSkinCard(
    skin: PlayerSkin,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(skin.bgMid)
            .then(
                if (selected) Modifier.border(2.dp, skin.accent, RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(214.dp)
        ) {
            SkinCardBackdrop(skin, animate = selected && skin.anim != PlayerAnim.NONE)

            when (skin.layout) {
                PlayerSkinLayout.STAGE -> StageCardBody(skin)
                PlayerSkinLayout.HUD -> HudCardBody(skin)
                PlayerSkinLayout.POSTER -> PosterCardBody(skin)
                PlayerSkinLayout.ORBIT -> OrbitCardBody(skin)
                PlayerSkinLayout.DUO -> DuoCardBody(skin)
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(skin.ink.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    tr(layoutZh(skin.layout), layoutEn(skin.layout)),
                    fontSize = 7.sp,
                    color = skin.ink.copy(alpha = 0.85f),
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )
            }

            if (selected) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(skin.accentDeep)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        tr("使用中", "In use"),
                        fontSize = 9.sp,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr(skin.tagZh, skin.tagEn),
                fontSize = 10.sp,
                color = if (skin.dark) skin.accent else skin.accentDeep,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                if (selected) tr("已换上", "Applied") else tr("换上", "Apply"),
                fontSize = 10.sp,
                color = if (selected) skin.sub else skin.ink,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BoxScope.SkinCardBackdrop(skin: PlayerSkin, animate: Boolean = false) {
    val img = skin.bgImage
    if (img != null) {
        Image(
            bitmap = ImageBitmap.imageResource(img),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val scrim = if (skin.ink.luminance() > 0.5f) Color.Black else Color.White
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, scrim.copy(alpha = 0.24f), scrim.copy(alpha = 0.80f))
                )
            )
        )
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(skin.bgTop, skin.bgMid, skin.bgBottom))
            )
        )
    }
    // 动态皮肤：选中的那张卡直接播放动画预览（只有当前选中的一张在跑，不叠加多套帧循环）
    if (animate) {
        DynamicSkinOverlay(skin = skin, playing = true)
    }
}

/** 卡里的迷你唱片：和播放页同一套渐变，幻彩皮肤连光晕一起画 */
@Composable
private fun MiniDisc(skin: PlayerSkin, size: Dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (skin.glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        skin.discLight.copy(alpha = 0.55f),
                        skin.discMid.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
        }
        val discScale = if (skin.glow) 0.78f else 0.93f
        drawCircle(
            color = skin.discRim.copy(alpha = 0.5f),
            radius = r * (discScale + 0.07f),
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(skin.discLight, skin.discMid, skin.discDark),
                center = center,
                radius = r
            ),
            radius = r * discScale,
            center = center
        )
        repeat(7) { i ->
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = r * discScale * (0.94f - i * 0.075f),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        drawArc(
            color = Color.White.copy(alpha = 0.16f),
            startAngle = -65f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - r * discScale * 0.88f, center.y - r * discScale * 0.88f),
            size = androidx.compose.ui.geometry.Size(
                r * discScale * 1.76f,
                r * discScale * 1.76f
            ),
            style = Stroke(width = 5.dp.toPx())
        )
        drawCircle(color = skin.bgMid, radius = r * discScale * 0.26f, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = r * discScale * 0.26f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/** 卡里的分段读数条 */
@Composable
private fun MiniSegBar(skin: PlayerSkin, segments: Int = 16, progress: Float = 0.42f) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(segments) { i ->
            val on = (i + 1) / segments.toFloat() <= progress
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(if (on) skin.accent else skin.track.copy(alpha = 0.6f))
            )
        }
    }
}

/** 卡里的控制条：心 / 上一首 / 播放 / 下一首 / 列表 */
@Composable
private fun MiniControlRow(skin: PlayerSkin, playSize: Dp = 27.dp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Lucide.Heart, contentDescription = null,
            tint = skin.ink.copy(alpha = 0.52f), modifier = Modifier.size(12.dp)
        )
        Icon(
            Lucide.SkipBack, contentDescription = null,
            tint = skin.ink, modifier = Modifier.size(13.dp)
        )
        Box(
            Modifier.size(playSize).clip(CircleShape).background(skin.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(playSize / 2)
            )
        }
        Icon(
            Lucide.SkipForward, contentDescription = null,
            tint = skin.ink, modifier = Modifier.size(13.dp)
        )
        Icon(
            Lucide.ListMusic, contentDescription = null,
            tint = skin.ink.copy(alpha = 0.52f), modifier = Modifier.size(12.dp)
        )
    }
}

/** 原版卡：唱片居中，标题 + 控制条在下 */
@Composable
private fun StageCardBody(skin: PlayerSkin) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.26f))
        MiniDisc(skin, if (skin.glow) 84.dp else 92.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            skin.zh, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = skin.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.weight(0.20f))
        MiniControlRow(skin)
    }
}

/** HUD 卡：刻度环 + 分段读数 */
@Composable
private fun HudCardBody(skin: PlayerSkin) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            "SYS · LINK", fontSize = 7.sp, color = skin.accent,
            letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.weight(0.10f))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                repeat(40) { i ->
                    val a = (i * 9f) * PI / 180f
                    val major = i % 5 == 0
                    val inner = r * (if (major) 0.90f else 0.945f)
                    drawLine(
                        color = skin.sub.copy(alpha = if (major) 0.8f else 0.35f),
                        start = Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                        end = Offset(
                            c.x + cos(a).toFloat() * r * 0.99f,
                            c.y + sin(a).toFloat() * r * 0.99f
                        ),
                        strokeWidth = if (major) 1.4.dp.toPx() else 0.8.dp.toPx()
                    )
                }
            }
            MiniDisc(skin, 62.dp)
        }
        Spacer(Modifier.weight(0.14f))
        MiniSegBar(skin, segments = 12)
        Spacer(Modifier.height(5.dp))
        Text(
            skin.zh, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = skin.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(0.16f))
        MiniControlRow(skin, playSize = 22.dp)
    }
}

/** 海报卡：大字标题压场，封面缩成右下小圆 */
@Composable
private fun PosterCardBody(skin: PlayerSkin) {
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        Text(
            tr("单曲档案", "FILE"), fontSize = 7.sp, color = skin.sub, letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            skin.zh, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = skin.ink,
            lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr(skin.tagZh, skin.tagEn), fontSize = 8.sp, color = skin.sub,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                MiniSegBar(skin, segments = 10)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .border(1.dp, skin.ink.copy(alpha = 0.5f), CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(skin.discLight, skin.discMid, skin.discDark))
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        MiniControlRow(skin, playSize = 24.dp)
    }
}

/** 环绕卡：控件散在唱片四周 */
@Composable
private fun OrbitCardBody(skin: PlayerSkin) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = skin.sub.copy(alpha = 0.2f), radius = r * 0.99f, center = c,
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = skin.accent, startAngle = -90f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(c.x - r * 0.97f, c.y - r * 0.97f),
                size = androidx.compose.ui.geometry.Size(r * 1.94f, r * 1.94f),
                style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        MiniDisc(skin, 68.dp)
        Icon(
            Lucide.Heart, contentDescription = null,
            tint = skin.ink.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopCenter).size(11.dp)
        )
        Icon(
            Lucide.SkipBack, contentDescription = null,
            tint = skin.ink, modifier = Modifier.align(Alignment.CenterStart).size(13.dp)
        )
        Icon(
            Lucide.SkipForward, contentDescription = null,
            tint = skin.ink, modifier = Modifier.align(Alignment.CenterEnd).size(13.dp)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(24.dp)
                .clip(CircleShape)
                .background(skin.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 30.dp)
        ) {
            Text(
                skin.zh, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = skin.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp
            )
        }
    }
}

/** 双栏卡：左玻璃封面卡 + 右信息列 */
@Composable
private fun DuoCardBody(skin: PlayerSkin) {
    Row(
        Modifier.fillMaxSize().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(skin.chipBg)
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(skin.discLight, skin.discMid, skin.discDark)
                        )
                    )
            )
        }
        Column(Modifier.weight(0.60f).fillMaxHeight()) {
            Text(
                tr("正在播放", "NOW PLAYING"), fontSize = 7.sp, color = skin.sub,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                skin.zh, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = skin.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp
            )
            Text(
                tr(skin.tagZh, skin.tagEn), fontSize = 7.sp, color = skin.sub, maxLines = 1
            )
            Spacer(Modifier.weight(0.4f))
            MiniSegBar(skin, segments = 10)
            Spacer(Modifier.weight(0.3f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    Lucide.Heart, null, tint = skin.ink.copy(alpha = 0.52f),
                    modifier = Modifier.size(10.dp)
                )
                Icon(Lucide.SkipBack, null, tint = skin.ink, modifier = Modifier.size(11.dp))
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(skin.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
                Icon(Lucide.SkipForward, null, tint = skin.ink, modifier = Modifier.size(11.dp))
                Icon(
                    Lucide.ListMusic, null, tint = skin.ink.copy(alpha = 0.52f),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}
