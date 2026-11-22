package com.example.my.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.state.AppState
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Text as ThemeText
import com.example.my.ui.theme.TextMuted

/**
 * 全 App 唯一的提示条状态。
 *
 * 以前有两套：全局 ToastHost（黑胶囊贴在屏幕底部）+ 播放页 stageToast（系统白 Toast），
 * 一次操作会同时弹两条、位置还不一样。现在只留这一条：黑胶囊挂在页面顶部，
 * 从顶栏下方展开、2.6 秒后收起，谁调用都进这里。
 */
object AppBanner {
    private var textState by mutableStateOf("")
    private var visibleState by mutableStateOf(false)
    private var seqState by mutableIntStateOf(0)

    val text: String get() = textState
    val visible: Boolean get() = visibleState
    val seq: Int get() = seqState

    fun show(message: String) {
        if (message.isBlank()) return
        textState = message
        visibleState = true
        seqState += 1
    }

    fun hide() {
        visibleState = false
    }
}

/** 全 App 提示条：页面顶部居中黑胶囊，展开 / 收起都带动画 */
@Composable
fun ToastHost(state: AppState) {
    // 旧的 state.toast(...) 只是"发起"，渲染统一交给 AppBanner
    LaunchedEffect(state.toastSeq) {
        state.toastText?.let {
            AppBanner.show(it)
            state.clearToast()
        }
    }
    LaunchedEffect(AppBanner.seq) {
        if (AppBanner.visible) {
            kotlinx.coroutines.delay(2600)
            AppBanner.hide()
        }
    }
    AnimatedVisibility(
        visible = AppBanner.visible,
        enter = expandVertically(animationSpec = tween(300), expandFrom = Alignment.Top) +
            slideInVertically(animationSpec = tween(300)) { -it / 3 } +
            fadeIn(animationSpec = tween(180)),
        exit = shrinkVertically(animationSpec = tween(240), shrinkTowards = Alignment.Top) +
            slideOutVertically(animationSpec = tween(240)) { -it / 3 } +
            fadeOut(animationSpec = tween(140))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 54.dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xF2151920))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = AppBanner.text,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 空状态：图标 + 主文案，可选副标题与一个引导动作按钮 */
@Composable
fun EmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(text = text, color = ThemeText.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        val sub = subtitle?.takeIf { it.isNotBlank() }
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = sub,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 错误 / 失败状态：图标 + 标题 + 说明，可选重试按钮；网络失败、空结果、超时统一走这里 */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Func.Danger, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(text = title, color = ThemeText.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val msg = message?.takeIf { it.isNotBlank() }
        if (msg != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = msg,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }
        if (!actionLabel.isNullOrBlank() && onRetry != null) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onRetry) {
                Text(actionLabel, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 通用加载态：居中转圈 + 可选文案；列表页优先用骨架屏，整页等待用这个 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = Brand.Teal
        )
        if (!text.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = text, color = TextMuted, fontSize = 12.sp)
        }
    }
}
