package com.example.my.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.my.state.AppState
import com.example.my.ui.components.Avatar
import com.example.my.ui.components.GradientCover
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.AppBackground
import com.example.my.ui.theme.AppBackgroundConfig
import com.example.my.ui.theme.AppFont
import com.example.my.ui.theme.bgBlurDp
import com.example.my.ui.theme.customInk
import com.example.my.ui.theme.customScrimAlpha
import com.example.my.ui.theme.customScrimColor
import com.example.my.ui.theme.MAX_BACKGROUND_IMAGES
import com.example.my.ui.theme.backgroundBrush
import com.example.my.ui.theme.gradientColorList
import com.example.my.ui.theme.BgColorMode
import com.example.my.ui.theme.BgFill
import com.example.my.ui.theme.BgLayout
import com.example.my.ui.theme.BgMint
import com.example.my.ui.theme.BgPreset
import com.example.my.ui.theme.BgStyle
import com.example.my.ui.theme.BgTransition
import com.example.my.ui.theme.BackgroundPresets
import com.example.my.ui.theme.ColorSync
import com.example.my.ui.theme.GlassLevel
import com.example.my.ui.theme.ThemePreset
import com.example.my.ui.theme.ThemePresets
import com.example.my.ui.theme.colorsForBackground
import com.example.my.ui.theme.extractPalette
import com.example.my.ui.theme.fontFamilyFor
import com.example.my.ui.theme.hueOf
import com.example.my.ui.theme.rememberBackgroundBitmap
import com.example.my.ui.theme.rememberThumbBitmap
import com.example.my.ui.theme.surfaceForCustomText
import com.example.my.ui.theme.saturationOf

private val PageBg = Color(0xFF0C0C0D)
private val PanelBg = Color(0xFF232326)
private val PageInk = Color(0xFFFFFFFF)
private val PageSub = Color(0xFFA5A5AA)
private val PageLine = Color(0x14FFFFFF)
private val ActionGreen = Color(0xFF2BE06B)

private val HighlightOptions = listOf(
    "黑色" to "Black" to Color(0xFF171717),
    "白色" to "White" to Color(0xFFFFFFFF),
    "赤红" to "Red" to Color(0xFFFF3B30),
    "橙" to "Orange" to Color(0xFFFF7A1A),
    "赤陶" to "Terracotta" to Color(0xFFE07A5F),
    "琥珀" to "Amber" to Color(0xFFFFC400),
    "海蓝" to "Blue" to Color(0xFF2F80ED),
    "松绿" to "Green" to Color(0xFF27C46B),
    "樱粉" to "Pink" to Color(0xFFFF6F9C)
)

private val TextOptions = listOf(
    // 大厂标准的正文墨色档：每一档都跟「玻璃卡强度」配对过——
    // 明度 > 0.55 走深色玻璃、≤ 0.55 走浅色玻璃，任何一档在自己的卡片上对比度都够。
    "墨黑" to "Ink" to Color(0xFF1F2328),
    "深灰" to "Charcoal" to Color(0xFF43474D),
    "纯白" to "White" to Color(0xFFFFFFFF),
    "米白" to "Ivory" to Color(0xFFF2EDE4),
    "藏蓝" to "Navy" to Color(0xFF1B3B6F),
    "墨绿" to "Forest" to Color(0xFF1E4A38),
    "酒红" to "Wine" to Color(0xFF6C2537),
    "青灰" to "Slate" to Color(0xFF2F6F7E)
)

/**
 * 字体选项直接从 [AppFont] 生成，不再手写一份列表：
 * 增删档位只改枚举，界面与「当前：xxx」文案自动跟上，避免两处漂移。
 */
private val FontOptions = AppFont.entries.map { it to (it.zh to it.en) }

/** 设置 > 通用 > 自定义背景：全 App 生效的背景编辑页。 */
@Composable
fun CustomBackgroundScreen(
    state: AppState,
    onBack: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember { AppBackground.config }
    var draft by remember { mutableStateOf(initial) }
    var showColorSheet by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(hueOf(draft.activeColors.first())) }
    var saturation by remember { mutableFloatStateOf(saturationOf(draft.activeColors.first())) }

    // 多选：一次能把剩下的名额都选满（上限 MAX_BACKGROUND_IMAGES 张），
    // 换成 OpenMultipleDocuments 而不是 GetContent —— 单选一次只能加一张，20 张要来回点 20 次
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val room = MAX_BACKGROUND_IMAGES - draft.imagePaths.size
        if (room <= 0) {
            state.toast(
                tr(
                    "最多 $MAX_BACKGROUND_IMAGES 张，先删掉几张再加",
                    "Up to $MAX_BACKGROUND_IMAGES photos — remove one first"
                )
            )
            return@rememberLauncherForActivityResult
        }
        val added = uris.take(room).mapNotNull { AppBackground.importImage(context, it) }
        if (added.isEmpty()) {
            state.toast(tr("图片读取失败，请换几张试试", "Could not read these images"))
            return@rememberLauncherForActivityResult
        }
        // 没选中图片就保持原来的图片模式；只加图，不动已有的
        draft = draft.copy(fill = BgFill.IMAGE, imagePaths = draft.imagePaths + added)
        if (uris.size > room) {
            state.toast(
                tr(
                    "最多 $MAX_BACKGROUND_IMAGES 张，这次先加了 $room 张",
                    "Up to $MAX_BACKGROUND_IMAGES photos — added $room this time"
                )
            )
        }
    }

    // 本页固定深色，系统状态栏/导航栏图标切到浅色，退出时还原
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (controller != null) {
                controller.isAppearanceLightStatusBars = oldStatus ?: false
                controller.isAppearanceLightNavigationBars = oldNav ?: false
            }
        }
    }

    BackHandler {
        AppBackground.discardImages(context, draft.imagePaths)
        onBack()
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶栏：返回 · 标题 · 使用
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = tr("返回", "Back"),
                    tint = PageInk,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { AppBackground.discardImages(context, draft.imagePaths); onBack() }
                )
                Text(
                    tr("自定义背景", "Custom background"),
                    color = PageInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(50))
                        .background(ActionGreen)
                        .clickable {
                            if (draft.fill == BgFill.IMAGE && draft.imagePaths.isEmpty()) {
                                state.toast(tr("先选一张相片作为背景", "Pick a photo first"))
                            } else {
                                AppBackground.apply(context, draft)
                                onApplied()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        tr("使用", "Use"),
                        color = Color(0xFF0B0D0B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    OwnAppPreview(
                        state = state,
                        draft = draft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 14.dp)
                    )
                }
                item {
                    // 主题中心：成套配色一次点完，这是整个页面最重要的一块
                    ThemePresetSection(
                        current = draft,
                        onPick = { preset ->
                            // 只换配色，不动已选的图：用户切回「相册」时图还在，
                            // 顺手清掉反而像"点一下主题把照片弄丢了"
                            draft = preset.draftFrom(draft)
                            hue = hueOf(draft.activeColors.first())
                            saturation = saturationOf(draft.activeColors.first())
                        }
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppBackground.discardImages(context, draft.imagePaths)
                                AppBackground.reset(context)
                                draft = AppBackgroundConfig.Default
                                hue = hueOf(BgMint)
                                saturation = saturationOf(BgMint)
                                state.toast(tr("已恢复默认背景", "Default background restored"))
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Restore,
                            null,
                            tint = PageSub,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(tr("恢复默认", "Restore default"), color = PageSub, fontSize = 13.sp)
                    }
                }
                item { DarkDivider() }
                item {
                    BackgroundSection(
                        draft = draft,
                        onAddImages = { imagePicker.launch(arrayOf("image/*")) },
                        onRemoveImage = { index ->
                            val removed = draft.imagePaths.getOrNull(index) ?: return@BackgroundSection
                            draft = draft.copy(
                                imagePaths = draft.imagePaths.filterIndexed { i, _ -> i != index }
                            )
                            AppBackground.discardImages(context, listOf(removed))
                        },
                        onOpenColor = {
                            hue = hueOf(draft.activeColors.first())
                            saturation = saturationOf(draft.activeColors.first())
                            showColorSheet = true
                        },
                        onPickPreset = { preset ->
                            AppBackground.discardImages(context, draft.imagePaths)
                            draft = draft.copy(
                                fill = BgFill.COLOR,
                                mode = preset.mode,
                                colors = preset.colors,
                                imagePaths = emptyList()
                            )
                            hue = hueOf(preset.colors.first())
                            saturation = saturationOf(preset.colors.first())
                        }
                    )
                }
                if (draft.fill == BgFill.COLOR) {
                    item { DarkDivider() }
                    item {
                        StyleSection(
                            current = draft.style,
                            onPick = { draft = draft.copy(style = it) }
                        )
                    }
                }
                if (draft.fill == BgFill.IMAGE) {
                    item {
                        LayoutSection(
                            current = draft.layout,
                            onPick = { draft = draft.copy(layout = it) }
                        )
                    }
                }
                if (draft.fill == BgFill.IMAGE && draft.imagePaths.size >= 2) {
                    item {
                        IntervalSection(
                            intervalSec = draft.intervalSec,
                            onChange = { draft = draft.copy(intervalSec = it) }
                        )
                    }
                    item {
                        TransitionSection(
                            current = draft.transition,
                            onPick = { draft = draft.copy(transition = it) }
                        )
                    }
                }
                if (draft.fill == BgFill.IMAGE) {
                    item {
                        QqSliderLine(
                            label = tr("透明度", "Transparency"),
                            value = draft.imageAlpha,
                            range = 0f..1f,
                            onChange = { draft = draft.copy(imageAlpha = it) }
                        )
                    }
                    item {
                        QqSliderLine(
                            label = tr("模糊值", "Blur"),
                            value = draft.blur,
                            range = 0f..50f,
                            onChange = { draft = draft.copy(blur = it) }
                        )
                    }
                }
                item { DarkDivider() }
                item {
                    TextureSection(
                        vignette = draft.vignette,
                        grain = draft.grain,
                        animate = draft.animate,
                        canAnimate = draft.fill == BgFill.COLOR &&
                            (draft.style == BgStyle.MESH || draft.style == BgStyle.AURORA),
                        onVignette = { draft = draft.copy(vignette = it) },
                        onGrain = { draft = draft.copy(grain = it) },
                        onAnimate = { draft = draft.copy(animate = it) }
                    )
                }
                item { DarkDivider() }
                item {
                    ColorChoiceSection(
                        title = tr("高亮色", "Highlight"),
                        options = HighlightOptions,
                        current = draft.highlight,
                        onPick = { draft = draft.copy(highlight = it) }
                    )
                }
                item { DarkDivider() }
                item {
                    ColorChoiceSection(
                        title = tr("文字色", "Text color"),
                        options = TextOptions,
                        current = draft.textColor,
                        onPick = { draft = draft.copy(textColor = it) }
                    )
                }
                item { DarkDivider() }
                item {
                    FontChoiceSection(
                        current = draft.font,
                        onPick = { draft = draft.copy(font = it) }
                    )
                }
                item { DarkDivider() }
                item {
                    ColorSyncSection(
                        available = draft.fill == BgFill.IMAGE,
                        current = draft.colorSync,
                        onPick = { draft = draft.copy(colorSync = it) }
                    )
                }
                item { DarkDivider() }
                item {
                    GlassSection(
                        current = draft.glass,
                        onPick = { draft = draft.copy(glass = it) }
                    )
                }
                item { DarkDivider() }
                item {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        tr(
                            "背景会铺在整个 App 的最底层，液态玻璃卡片保留自身边界；文字色会自动匹配卡片深浅。" +
                                "留默认「墨黑」时会跟随系统深浅色自动切换，选其他颜色则以你选的为准。",
                            "The background sits behind every screen; liquid-glass cards keep their own edge; text adapts. " +
                                "Leaving the default Ink follows the system light/dark theme."
                        ),
                        color = PageSub.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }

    if (showColorSheet) {
        ColorModeSheet(
            draft = draft,
            hue = hue,
            saturation = saturation,
            onDraft = { draft = it },
            onHue = { h ->
                hue = h
                draft = draft.copy(colors = colorsForBackground(hue, saturation, draft.mode))
            },
            onSaturation = { s ->
                saturation = s
                draft = draft.copy(colors = colorsForBackground(hue, saturation, draft.mode))
            },
            onMode = { mode ->
                draft = draft.copy(mode = mode, colors = colorsForBackground(hue, saturation, mode))
            },
            onDismiss = { showColorSheet = false }
        )
    }
}

/** 预览框里画的是 MY足迹 自己的「我的」页，而不是参考图里的其他 App。 */
@Composable
private fun OwnAppPreview(state: AppState, draft: AppBackgroundConfig, modifier: Modifier = Modifier) {
    val user = state.user
    val footprints = state.footprints
    val plans = state.tripPlans
    val image = rememberBackgroundBitmap(draft.imagePath.takeIf { draft.fill == BgFill.IMAGE })
    // 预览里的取色：64×64 采样，一次几毫秒，按图缓存不重复算
    val previewPalette = remember(image) { extractPalette(image) }
    val usePhotoColors = draft.fill == BgFill.IMAGE
    // 与真实页面同一套规则：默认墨色在深色模式下自动转浅
    val ink = customInk(draft, isSystemInDarkTheme())
    // 预览里所有次要文字共用这一档：0.78 叠在半透明玻璃卡上会发灰，提到 0.88
    val sub = ink.copy(alpha = 0.88f)
    // 预览与真实页面共用同一个真源：液态玻璃底色（高不透明，文字不被背景穿透）
    val card = surfaceForCustomText(ink, draft.glass)
    val glassBorder = if (ink.luminance() > 0.55f) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.42f)
    // 高亮色同样走「取色同步」：预览里选「跟随」，这里立刻变色，用户才敢用
    val accent = draft.resolvedHighlight(if (usePhotoColors) previewPalette else null)
    val colors = draft.resolvedColors(if (usePhotoColors) previewPalette else null, isSystemInDarkTheme())
    val shape = RoundedCornerShape(28.dp)

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(238.dp)
                .height(496.dp)
                .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.20f), spotColor = Color.Black.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                .clip(shape)
                .background(BgMint)
        ) {
            if (draft.fill == BgFill.IMAGE && image != null) {
                // 三种排版都要如实预览，否则用户选了「模糊铺底」却看不到效果
                when (draft.layout) {
                    BgLayout.FILL -> Image(
                        image,
                        null,
                        Modifier.fillMaxSize().blurPreview(draft.blur),
                        contentScale = ContentScale.Crop
                    )
                    BgLayout.BLUR_BACKDROP -> {
                        Image(
                            image,
                            null,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
                                .blur(16.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.40f))
                        )
                        val ratio = image.width.toFloat() / image.height.coerceAtLeast(1)
                        Image(
                            image,
                            null,
                            Modifier
                                .width(178.dp)
                                .height((178f / ratio.coerceIn(0.55f, 2.2f)).dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(14.dp))
                                .blurPreview(draft.blur * 0.35f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    BgLayout.CENTER -> {
                        Box(Modifier.fillMaxSize().background(backgroundBrush(colors)))
                        Image(
                            image,
                            null,
                            Modifier.fillMaxSize().blurPreview(draft.blur),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            } else {
                PreviewBackdrop(colors, draft.style, Modifier.fillMaxSize())
            }
            // 暗角与颗粒：让预览和实际背景看起来是同一个东西
            if (draft.vignette > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = (draft.vignette / 100f) * 0.62f)
                                ),
                                radius = 420f
                            )
                        )
                )
            }
            // 遮罩（和全 App 一致）：越透明越接近遮罩色，深色模式黑灰、浅色模式中性灰
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        customScrimColor(isSystemInDarkTheme()).copy(alpha = customScrimAlpha(draft))
                    )
            )

            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamilyFor(draft.font))
            ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("我的", "Me"), color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.Notifications, null, tint = sub, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Outlined.Tune, null, tint = sub, modifier = Modifier.size(10.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(19.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(card.copy(alpha = card.alpha * 0.94f))
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = sub, modifier = Modifier.size(8.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("搜索城市、攻略、足迹", "Search places, guides"), color = sub, fontSize = 7.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(card)
                        .border(1.dp, glassBorder, RoundedCornerShape(10.dp))
                        .padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        text = user.nickname.ifBlank { "我" },
                        imageUrl = user.avatarUrl,
                        size = 23.dp,
                        background = accent
                    )
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                user.nickname.ifBlank { tr("旅行者", "Traveler") },
                                color = ink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 84.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accent.copy(alpha = 0.22f))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text("Lv.6", color = accent, fontSize = 5.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            user.signature.ifBlank { tr("山高路远，看世界", "Roads and horizons") },
                            color = sub,
                            fontSize = 6.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        tr("${user.following} 关注", "${user.following} following"),
                        color = sub,
                        fontSize = 6.5.sp
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(card.copy(alpha = card.alpha * 0.94f))
                        .padding(vertical = 6.dp)
                ) {
                    MiniStat(Icons.Outlined.Place, footprints.size, tr("足迹", "Spots"), ink, sub, Modifier.weight(1f))
                    MiniStat(Icons.Outlined.FavoriteBorder, state.guides.count { it.favored }, tr("收藏", "Saved"), ink, sub, Modifier.weight(1f))
                    MiniStat(Icons.Outlined.Article, state.myGuides.size, tr("攻略", "Guides"), ink, sub, Modifier.weight(1f))
                    MiniStat(Icons.Outlined.Route, plans.size, tr("计划", "Trips"), ink, sub, Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("最近足迹", "Recent"), color = ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.KeyboardArrowRight, null, tint = sub, modifier = Modifier.size(10.dp))
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (footprints.isEmpty()) {
                        repeat(3) { MiniEmptyTile(ink, sub, Modifier.weight(1f)) }
                    } else {
                        footprints.take(3).forEach { fp ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(7.dp))
                            ) {
                                GradientCover(seed = fp.seed, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(7.dp))
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                                )
                                Text(
                                    fp.city,
                                    color = Color.White,
                                    fontSize = 6.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                )
                            }
                        }
                        repeat((3 - footprints.size).coerceAtLeast(0)) { MiniEmptyTile(ink, sub, Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("我的计划", "My plans"), color = ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(5.dp))
                    Box(
                        Modifier.size(12.dp).clip(CircleShape).background(card.copy(alpha = card.alpha * 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = sub, fontSize = 7.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        tr("${plans.size} 个", "${plans.size}"),
                        color = sub,
                        fontSize = 6.5.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (plans.isEmpty()) {
                    MiniPlanRow(tr("还没有出行计划", "No trips yet"), tr("去添加一个", "Add one"), ink, sub, card)
                } else {
                    plans.take(2).forEach { plan ->
                        MiniPlanRow(plan.name, plan.destination, ink, sub, card)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(card)
                        .border(1.dp, glassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniTab(Icons.Outlined.Home, tr("首页", "Home"), false, ink, sub, Modifier.weight(1f))
                    MiniTab(Icons.Outlined.Map, tr("地图", "Map"), false, ink, sub, Modifier.weight(1f))
                    MiniTab(Icons.Outlined.Route, tr("行程", "Trips"), false, ink, sub, Modifier.weight(1f))
                    MiniTab(Icons.Outlined.Person, tr("我的", "Me"), true, ink, accent, Modifier.weight(1f))
                }
            }
            }
        }
    }
}

@Composable
private fun MiniStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    label: String,
    ink: Color,
    sub: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = sub, modifier = Modifier.size(9.dp))
        Text("$value", color = ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
        Text(label, color = sub, fontSize = 6.sp, lineHeight = 7.sp)
    }
}

@Composable
private fun MiniEmptyTile(ink: Color, sub: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, sub.copy(alpha = 0.22f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(tr("待记录", "Empty"), color = sub, fontSize = 6.5.sp)
    }
}

@Composable
private fun MiniPlanRow(title: String, subtitle: String, ink: Color, sub: Color, card: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(card.copy(alpha = card.alpha * 0.72f))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(19.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE07A5F).copy(alpha = 0.28f))
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 7.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = sub, fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = sub, fontSize = 8.sp)
    }
}

@Composable
private fun MiniTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    ink: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) accent else ink.copy(alpha = 0.76f), modifier = Modifier.size(10.dp))
        Text(
            label,
            color = if (selected) accent else ink.copy(alpha = 0.76f),
            fontSize = 5.5.sp,
            lineHeight = 6.sp
        )
    }
}

@Composable
private fun BackgroundSection(
    draft: AppBackgroundConfig,
    onAddImages: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onOpenColor: () -> Unit,
    onPickPreset: (BgPreset) -> Unit
) {
    val isPreset = draft.fill == BgFill.COLOR && BackgroundPresets.any {
        it.mode == draft.mode && it.colors == draft.activeColors
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("背景", "Background"), color = PageInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    draft.fill != BgFill.IMAGE -> tr("浅色", "Light")
                    draft.imagePaths.size >= 2 -> tr("轮播 · ${draft.imagePaths.size} 张", "Slideshow · ${draft.imagePaths.size}")
                    else -> tr("相册", "Photo")
                },
                color = PageSub,
                fontSize = 14.sp
            )
        }

        // 已选的图片：一张一张列出来，带序号与删除，最多 MAX_BACKGROUND_IMAGES 张
        if (draft.fill == BgFill.IMAGE) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                draft.imagePaths.forEachIndexed { index, path ->
                    ImageThumb(
                        path = path,
                        index = index,
                        onRemove = { onRemoveImage(index) }
                    )
                }
                if (draft.imagePaths.size < MAX_BACKGROUND_IMAGES) {
                    ActionTile(
                        label = tr("加图", "Add"),
                        icon = Icons.Outlined.Add,
                        selected = false,
                        onClick = onAddImages
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "已选 ${draft.imagePaths.size} 张，最多 $MAX_BACKGROUND_IMAGES 张；加满后可设间隔与切换动画。",
                    "${draft.imagePaths.size} of $MAX_BACKGROUND_IMAGES selected — set interval and transition below."
                ),
                color = PageSub.copy(alpha = 0.9f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                label = if (draft.fill == BgFill.IMAGE) tr("加图", "Add") else tr("相册", "Album"),
                icon = Icons.Outlined.Add,
                selected = draft.fill == BgFill.IMAGE,
                onClick = onAddImages
            )
            ActionTile(
                label = tr("调色", "Color"),
                icon = Icons.Outlined.Tune,
                selected = !isPreset && draft.fill == BgFill.COLOR,
                onClick = onOpenColor
            )
            BackgroundPresets.forEach { preset ->
                PresetTile(
                    preset = preset,
                    selected = isPreset && preset.mode == draft.mode && preset.colors == draft.activeColors,
                    onClick = { onPickPreset(preset) }
                )
            }
        }
    }
}

/** 单张已选背景：缩略图 + 序号 + 右上角删除 */
@Composable
private fun ImageThumb(path: String, index: Int, onRemove: () -> Unit) {
    val thumb = rememberThumbBitmap(path)
    Box(
        Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PanelBg)
            .border(1.dp, ActionGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        if (thumb != null) {
            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // 序号：让用户知道切换顺序
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text("${index + 1}", color = Color.White, fontSize = 9.sp)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = tr("删除这张", "Remove"),
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/** 自动切换间隔：几档快选 + 秒数滑条（自定义任意 3–60 秒） */
@Composable
private fun IntervalSection(intervalSec: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("自动切换", "Auto switch"), color = PageInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (intervalSec <= 0) tr("不切换", "Off") else tr("每 $intervalSec 秒", "every ${intervalSec}s"),
                color = PageSub, fontSize = 12.sp
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 5, 10, 20, 30, 60).forEach { sec ->
                DarkChip(
                    label = if (sec == 0) tr("不切换", "Off") else "$sec s",
                    selected = intervalSec == sec,
                    onClick = { onChange(sec) }
                )
            }
        }
        SliderLine(
            label = tr("间隔", "Interval"),
            valueText = "${intervalSec.coerceIn(3, 60)}s",
            value = intervalSec.coerceIn(3, 60).toFloat(),
            range = 3f..60f,
            onChange = { onChange(it.toInt()) }
        )
    }
}

/** 切换动画：三档，同一个 App 背景层实时生效 */
@Composable
private fun TransitionSection(current: BgTransition, onPick: (BgTransition) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(tr("切换动画", "Transition"), color = PageInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BgTransition.entries.forEach { t ->
                DarkChip(
                    label = tr(t.zh, t.en),
                    selected = current == t,
                    onClick = { onPick(t) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "约 0.9 秒完成；「智能轮播」每次随机挑一种，前后两次不会重样。",
                "Each runs ~0.9s; “Smart mix” picks a random one per switch, never twice in a row."
            ),
            color = PageSub.copy(alpha = 0.9f),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

/** 深色页面用的小胶囊选项 */
@Composable
private fun DarkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ActionGreen.copy(alpha = 0.22f) else PanelBg)
            .border(
                1.dp,
                if (selected) ActionGreen else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) ActionGreen else PageInk,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(62.dp)
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PanelBg)
            .border(if (selected) 2.dp else 1.dp, if (selected) ActionGreen else Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = PageInk, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = PageInk, fontSize = 10.sp)
    }
}

@Composable
private fun PresetTile(preset: BgPreset, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (preset.colors.size > 1) Brush.linearGradient(preset.colors) else Brush.linearGradient(listOf(preset.colors.first(), preset.colors.first())))
            .border(if (selected) 2.dp else 1.dp, if (selected) ActionGreen else Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SliderLine(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PageInk, fontSize = 14.sp, modifier = Modifier.width(58.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.88f),
                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            )
        )
        Text(valueText, color = PageSub, fontSize = 12.sp, modifier = Modifier.width(44.dp), maxLines = 1)
    }
}

/**
 * QQ 音乐风格滑块：整条轨道是「亮白 → 深灰」的水平渐变细胶囊，
 * 拇指为白色外环 + 浅灰内芯，外圈带一圈柔和白光晕；右侧不显示数值。
 * 仅用于背景图片的透明度 / 模糊值，其余滑块仍走 [SliderLine]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QqSliderLine(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PageInk, fontSize = 14.sp, modifier = Modifier.width(58.dp))
        Box(Modifier.weight(1f).height(30.dp)) {
            // 整条固定渐变轨道（不随拇指位置分段）
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.92f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                    )
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.fillMaxSize(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(22.dp)
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                ambientColor = Color.White.copy(alpha = 0.45f),
                                spotColor = Color.White.copy(alpha = 0.45f)
                            )
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC7C7CC))
                    )
                }
            )
        }
    }
}

@Composable
private fun ColorChoiceSection(
    title: String,
    options: List<Pair<Pair<String, String>, Color>>,
    current: Color,
    onPick: (Color) -> Unit
) {
    val currentLabel = options.firstOrNull { it.second.toArgb() == current.toArgb() }
        ?.first?.let { tr(it.first, it.second) } ?: tr("自定义", "Custom")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = PageInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(currentLabel, color = PageSub, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { (names, color) ->
                Box(
                    Modifier
                        .size(49.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(color)
                        .border(
                            width = if (current.toArgb() == color.toArgb()) 2.dp else 1.dp,
                            color = if (current.toArgb() == color.toArgb()) ActionGreen else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable { onPick(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (current.toArgb() == color.toArgb()) {
                        Icon(
                            Icons.Outlined.Check,
                            null,
                            tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontChoiceSection(current: AppFont, onPick: (AppFont) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("字体", "Font"), color = PageInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                FontOptions.firstOrNull { it.first == current }?.second?.let { tr(it.first, it.second) }
                    ?: tr("系统默认", "System"),
                color = PageSub,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 一行 4 个：8 档全挤一行会把每格压到 40dp 以内，字形差异根本看不出来
            FontOptions.chunked(4).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { (font, names) ->
                        val selected = current == font
                        // 本机没带这个字体族时如实变灰并禁用，避免「点了没反应」被当成 bug
                        val usable = font.isAvailable
                        Column(
                            Modifier
                                .weight(1f)
                                .height(58.dp)
                                .alpha(if (usable) 1f else 0.42f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color(0xFF303034) else PanelBg)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) ActionGreen else Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable(enabled = usable) { onPick(font) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Aa",
                                color = PageInk,
                                fontSize = 18.sp,
                                fontFamily = fontFamilyFor(font)
                            )
                            Text(
                                if (usable) tr(names.first, names.second) else tr("本机不支持", "N/A"),
                                color = if (selected) ActionGreen else PageSub,
                                fontSize = 9.sp,
                                fontFamily = fontFamilyFor(font),
                                maxLines = 1
                            )
                        }
                    }
                    // 补齐空位，避免最后一行不足 4 个时被拉成两倍宽
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorModeSheet(
    draft: AppBackgroundConfig,
    hue: Float,
    saturation: Float,
    onDraft: (AppBackgroundConfig) -> Unit,
    onHue: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onMode: (BgColorMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1D),
        contentColor = PageInk,
        scrimColor = Color.Black.copy(alpha = 0.52f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.22f)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("色彩模式", "Color mode"), color = PageInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    tr("完成", "Done"),
                    color = ActionGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BgColorMode.entries.forEach { mode ->
                    val selected = draft.mode == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF2D2D31))
                            .border(if (selected) 2.dp else 1.dp, if (selected) ActionGreen else Color.White.copy(alpha = 0.05f), RoundedCornerShape(9.dp))
                            .clickable { onMode(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (mode) {
                                BgColorMode.SINGLE -> tr("单色", "Single")
                                BgColorMode.DUAL -> tr("双色", "Duo")
                                BgColorMode.TRI -> tr("三色", "Tri")
                            },
                            color = PageInk,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                draft.activeColors.forEach { color ->
                    Box(
                        Modifier
                            .size(55.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(tr("色相值", "Hue"), color = PageInk, fontSize = 14.sp)
            Box(Modifier.fillMaxWidth().height(34.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            )
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = onHue,
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
            Text(tr("鲜艳度", "Vividness"), color = PageInk, fontSize = 14.sp)
            Box(Modifier.fillMaxWidth().height(34.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White, Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f))
                            )
                        )
                )
                Slider(
                    value = saturation,
                    onValueChange = onSaturation,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
        }
    }
}

// ====================================================================
// 主题中心 / 背景样式 / 质感 / 取色 / 玻璃
//
// 这一段是「参考 QQ 音乐重做」的主体：把原本散落在滑条里的十几种参数
// 收成「先挑成套主题，再按需微调」的两层结构。大厂的主题中心都是这个逻辑——
// 绝大多数用户只想点一下就好，愿意逐项调的人自然会往下滚。
// ====================================================================

/** 分区标题：统一 19sp Bold，与上面「背景」那一节完全对齐 */
@Composable
private fun SectionHeading(title: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = PageInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (hint != null) {
            Text(hint, color = PageSub, fontSize = 14.sp)
        }
    }
}

/** 分区说明：11sp，行高 16，任何一句都不能超过两行 */
@Composable
private fun SectionNote(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = PageSub.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp)
}

/** 主题中心：横向卡流，每张卡是一个完整「外观」 */
@Composable
private fun ThemePresetSection(current: AppBackgroundConfig, onPick: (ThemePreset) -> Unit) {
    Column(Modifier.padding(top = 2.dp, bottom = 14.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeading(
                tr("主题中心", "Theme studio"),
                tr("${ThemePresets.size} 套", "${ThemePresets.size} looks")
            )
            Spacer(Modifier.height(5.dp))
            Text(
                tr(
                    "成套的配色、玻璃与质感，一次点完；想细调再往下走。",
                    "Complete looks — colors, glass and texture in one tap."
                ),
                color = PageSub,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemePresets.forEach { preset ->
                ThemePresetCard(
                    preset = preset,
                    selected = current.sceneId == preset.id,
                    onClick = { onPick(preset) }
                )
            }
        }
    }
}

@Composable
private fun ThemePresetCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .width(128.dp)
            .height(158.dp)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ActionGreen else Color.White.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        PresetArt(preset, Modifier.fillMaxSize())
        // 底部压暗再放文字：不加这层，浅色主题（晨雾 / 蜜桃）上的白字完全读不出来
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.84f)
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(
                preset.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                preset.tagline,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ActionGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color(0xFF0B0D0B),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** 主题卡的迷你底纹：按 [BgStyle] 画，跟真实背景同一套逻辑（只是没有动效） */
@Composable
private fun PresetArt(preset: ThemePreset, modifier: Modifier = Modifier) {
    Box(modifier.drawBehind { drawPresetArt(preset) })
}

private fun DrawScope.drawPresetArt(preset: ThemePreset) {
    val cs = preset.colors.ifEmpty { listOf(BgMint) }
    drawRect(cs.first())
    when (preset.style) {
        BgStyle.SOLID -> Unit
        BgStyle.LINEAR -> drawRect(Brush.verticalGradient(gradientColorList(cs)))
        BgStyle.MESH -> cs.forEachIndexed { i, c ->
            val cx = size.width * (0.26f + 0.46f * (i % 2) + 0.05f * i)
            val cy = size.height * (0.20f + 0.40f * (i / 2) + 0.08f * i)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = size.maxDimension * 0.78f
                )
            )
        }
        BgStyle.AURORA -> {
            drawRect(Brush.linearGradient(gradientColorList(cs), start = Offset(0f, 0f), end = Offset(size.width, size.height)))
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, cs.last().copy(alpha = 0.75f), Color.Transparent),
                    start = Offset(0f, -size.height * 0.3f),
                    end = Offset(size.width * 1.25f, size.height)
                )
            )
        }
    }
    if (preset.vignette > 0f) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = (preset.vignette / 100f) * 0.62f)
                ),
                center = center,
                radius = size.maxDimension * 0.78f
            )
        )
    }
}

/** 背景样式：纯色 / 渐变 / 网格光晕 / 极光 */
@Composable
private fun StyleSection(current: BgStyle, onPick: (BgStyle) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        SectionHeading(tr("背景样式", "Style"))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BgStyle.entries.forEach { style ->
                OptionCard(
                    title = tr(style.zh, style.en),
                    desc = tr(styleHintZh(style), styleHintEn(style)),
                    selected = current == style,
                    onClick = { onPick(style) }
                )
            }
        }
        SectionNote(
            tr(
                "「网格光晕」与「极光」会缓慢流动，一个周期 34 秒；关掉下面的动效开关即静止。",
                "Mesh glow and Aurora drift slowly (34s per cycle); turn off motion below to keep them still."
            )
        )
    }
}

private fun styleHintZh(style: BgStyle): String = when (style) {
    BgStyle.SOLID -> "最克制，列表页好看"
    BgStyle.LINEAR -> "上下过渡，最稳妥"
    BgStyle.MESH -> "多色光斑交叠"
    BgStyle.AURORA -> "斜向流动，安静"
}

private fun styleHintEn(style: BgStyle): String = when (style) {
    BgStyle.SOLID -> "Quietest, good with lists"
    BgStyle.LINEAR -> "Simple top-to-bottom"
    BgStyle.MESH -> "Overlapping color glows"
    BgStyle.AURORA -> "Soft diagonal drift"
}

/** 图片排版：铺满 / 模糊铺底 / 完整居中 */
@Composable
private fun LayoutSection(current: BgLayout, onPick: (BgLayout) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        SectionHeading(tr("图片排版", "Photo layout"))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BgLayout.entries.forEach { layout ->
                OptionCard(
                    title = tr(layout.zh, layout.en),
                    desc = tr(layoutHintZh(layout), layoutHintEn(layout)),
                    selected = current == layout,
                    onClick = { onPick(layout) }
                )
            }
        }
        SectionNote(
            tr(
                "「模糊铺底」是 QQ 音乐播放页那套：同一张图先糊成氛围，再把清晰主体摆中间。",
                "Blurred backdrop is the pattern used on music player pages: a heavily blurred copy for mood, a sharp copy in the middle."
            )
        )
    }
}

private fun layoutHintZh(layout: BgLayout): String = when (layout) {
    BgLayout.FILL -> "铺满裁剪，无边框"
    BgLayout.BLUR_BACKDROP -> "模糊铺底 + 清晰主体"
    BgLayout.CENTER -> "不裁剪，四边补色"
}

private fun layoutHintEn(layout: BgLayout): String = when (layout) {
    BgLayout.FILL -> "Crop to fill"
    BgLayout.BLUR_BACKDROP -> "Blur behind, sharp subject"
    BgLayout.CENTER -> "Fit whole, color-filled edges"
}

/** 取色同步：手动 / 跟随高亮 / 整套跟随 */
@Composable
private fun ColorSyncSection(available: Boolean, current: ColorSync, onPick: (ColorSync) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        SectionHeading(
            tr("取色同步", "Color sync"),
            if (available) null else tr("需相册背景", "Needs a photo")
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ColorSync.entries.forEach { sync ->
                OptionCard(
                    title = tr(sync.zh, sync.en),
                    desc = tr(syncHintZh(sync), syncHintEn(sync)),
                    selected = available && current == sync,
                    enabled = available,
                    onClick = { if (available) onPick(sync) }
                )
            }
        }
        SectionNote(
            if (available) {
                tr(
                    "从照片里提取主色，换一张图整页色相跟着变 —— 这是最像「大厂」的一项。",
                    "Pulls the accent straight out of your photo, so the whole page follows the image."
                )
            } else {
                tr(
                    "选一张相片作背景后才能用；纯色/渐变模式本来就是你亲手挑的色，不需要覆盖。",
                    "Available once a photo is used; solid and gradient modes already use colors you picked."
                )
            }
        )
    }
}

private fun syncHintZh(sync: ColorSync): String = when (sync) {
    ColorSync.OFF -> "用手选的高亮色"
    ColorSync.ACCENT -> "高亮色取自照片"
    ColorSync.FULL -> "高亮 + 背景一起跟随"
}

private fun syncHintEn(sync: ColorSync): String = when (sync) {
    ColorSync.OFF -> "Your picked accent"
    ColorSync.ACCENT -> "Accent from the photo"
    ColorSync.FULL -> "Accent and backdrop both follow"
}

/** 玻璃强度：通透 / 标准 / 磨砂 */
@Composable
private fun GlassSection(current: GlassLevel, onPick: (GlassLevel) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        SectionHeading(tr("玻璃强度", "Glass strength"))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassLevel.entries.forEach { level ->
                OptionCard(
                    title = tr(level.zh, level.en),
                    desc = "${(level.alpha * 100).toInt()}% " + tr("不透明", "opaque"),
                    selected = current == level,
                    onClick = { onPick(level) }
                )
            }
        }
        SectionNote(
            tr(
                "三档都在 86% 以上：再透就会透出底层文字，卡片上的字会跟背景叠在一起。",
                "All three stay above 86% — any more see-through and the text behind bleeds into the card."
            )
        )
    }
}

/** 质感：暗角 + 颗粒 + 动效开关 */
@Composable
private fun TextureSection(
    vignette: Float,
    grain: Float,
    animate: Boolean,
    canAnimate: Boolean,
    onVignette: (Float) -> Unit,
    onGrain: (Float) -> Unit,
    onAnimate: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            SectionHeading(tr("质感", "Texture"))
            SectionNote(
                tr(
                    "暗角把视线收到画面中间；颗粒能消掉大面积渐变上的色带，照片背景尤其明显。",
                    "Vignette pulls the eye to the centre; grain kills banding on large gradients."
                )
            )
        }
        SliderLine(
            label = tr("暗角", "Vignette"),
            valueText = "${vignette.toInt()}",
            value = vignette,
            range = 0f..100f,
            onChange = onVignette
        )
        SliderLine(
            label = tr("颗粒", "Grain"),
            valueText = "${grain.toInt()}",
            value = grain,
            range = 0f..100f,
            onChange = onGrain
        )
        SwitchRow(
            label = tr("缓慢流动", "Slow motion"),
            hint = if (canAnimate) {
                tr("网格光晕 / 极光会以 34 秒一个周期漂移", "Mesh and aurora drift, 34s per cycle")
            } else {
                tr("仅「网格光晕」「极光」两种样式有动效", "Only Mesh glow and Aurora animate")
            },
            checked = animate && canAnimate,
            enabled = canAnimate,
            onChange = onAnimate
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** 通用的选项卡：标题 + 一行说明，选中时绿描边 */
@Composable
private fun OptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(108.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> PanelBg.copy(alpha = 0.5f)
                    selected -> ActionGreen.copy(alpha = 0.14f)
                    else -> PanelBg
                }
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.05f)
                    selected -> ActionGreen
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(
            title,
            color = if (selected) ActionGreen else if (enabled) PageInk else PageSub,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            desc,
            color = PageSub.copy(alpha = if (enabled) 0.95f else 0.7f),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 开关行：整行可点，右侧胶囊滑块 */
@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) PageInk else PageSub,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(hint, color = PageSub.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(46.dp, 26.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked && enabled) ActionGreen else Color.White.copy(alpha = 0.14f))
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .align(if (checked && enabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.55f))
            )
        }
    }
}

/** 预览用的背景底纹：与 AppBackgroundLayer 同一套形态，只是不做动效 */
@Composable
private fun PreviewBackdrop(colors: List<Color>, style: BgStyle, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val cs = colors.ifEmpty { listOf(BgMint) }
            drawRect(cs.first())
            when (style) {
                BgStyle.SOLID -> Unit
                BgStyle.LINEAR -> drawRect(Brush.verticalGradient(gradientColorList(cs)))
                BgStyle.MESH -> cs.forEachIndexed { i, c ->
                    val cx = size.width * (0.30f + 0.34f * (i % 2) + 0.06f * i)
                    val cy = size.height * (0.24f + 0.36f * (i / 2) + 0.10f * i)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(c.copy(alpha = 0.94f), c.copy(alpha = 0f)),
                            center = Offset(cx, cy),
                            radius = size.maxDimension * 0.78f
                        )
                    )
                }
                BgStyle.AURORA -> {
                    drawRect(
                        Brush.linearGradient(gradientColorList(cs), start = Offset(0f, 0f), end = Offset(size.width, size.height))
                    )
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, cs.last().copy(alpha = 0.62f), Color.Transparent),
                            start = Offset(0f, -size.height * 0.3f),
                            end = Offset(size.width * 1.2f, size.height)
                        )
                    )
                }
            }
        }
    )
}

@Composable
private fun DarkDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(PageLine)
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 预览专用的小工具。 */
private fun Modifier.graphicsAlpha(alpha: Float): Modifier =
    this.graphicsLayer { this.alpha = alpha }

private fun Modifier.blurPreview(radius: Float): Modifier =
    // 与全 App 共用同一档换算：滑条值 2 → 0.6dp，只做轻微柔化
    this.blur(bgBlurDp(radius), edgeTreatment = BlurredEdgeTreatment.Unbounded)
