package com.example.my.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.state.AppState
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.cardShadow
import com.example.my.ui.theme.Text as TextColor
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

private val usernameRegex = Regex("^[A-Za-z0-9_\\u4e00-\\u9fa5]{3,20}$")

/** 登录页的两个模式：登录 / 注册 */
private enum class LoginMode { LOGIN, REGISTER }

/** base64 PNG → Bitmap，失败返回 null */
private fun decodeCaptcha(base64: String): android.graphics.Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/**
 * 登录 / 注册页。
 *
 * - 账号体系：用户名 + 密码，不使用任何邮件验证码；
 * - 注册三步：填用户名 → 获取「注册验证地址」（地址里带一次性验证码）→ 浏览器打开验证
 *   → 回 App 填图形验证码和密码完成注册；注册成功后服务端立即删除该地址；
 * - 忘记密码：联系管理员在后台重置。
 */
@Composable
fun LoginScreen(state: AppState) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LoginMode.LOGIN) }

    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // 图形验证码
    var captchaId by remember { mutableStateOf<String?>(null) }
    var captchaBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var captchaText by remember { mutableStateOf("") }
    var captchaLoading by remember { mutableStateOf(false) }
    var showLoginCaptcha by remember { mutableStateOf(false) }

    // 注册验证地址
    var verifyUrl by remember { mutableStateOf<String?>(null) }
    var verifyLoading by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshCaptcha() {
        captchaLoading = true
        captchaText = ""
        state.fetchCaptcha { c ->
            captchaLoading = false
            if (c == null) {
                captchaId = null
                captchaBmp = null
            } else {
                captchaId = c.id
                captchaBmp = decodeCaptcha(c.imageBase64)
            }
        }
    }

    LaunchedEffect(mode) {
        if (mode == LoginMode.REGISTER) refreshCaptcha()
    }

    fun switchMode(next: LoginMode) {
        mode = next
        error = null
        captchaText = ""
        if (next == LoginMode.REGISTER) verifyUrl = null
    }

    fun openVerifyUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error = tr("没有可打开网页的浏览器，请复制链接到浏览器打开") }
    }

    fun copyVerifyUrl(url: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MY足迹注册验证地址", url))
        state.toast(tr("验证地址已复制"))
    }

    /** 第 1 步：用户名 + 图形验证码 → 生成一次性验证地址 */
    fun requestLink() {
        val name = account.trim()
        if (!usernameRegex.matches(name)) {
            error = tr("用户名需 3–20 位，只能包含字母、数字、下划线或中文")
            return
        }
        if (captchaText.trim().length < 3) {
            error = tr("请输入图形验证码")
            return
        }
        error = null
        verifyLoading = true
        state.requestVerifyLink(name, captchaId ?: "", captchaText.trim()) { link, msg ->
            verifyLoading = false
            if (link == null) {
                if (msg?.contains("图形验证码") == true) refreshCaptcha()
                error = msg
            } else {
                verifyUrl = link.url
                // 生成地址已经消耗了一张验证码，注册还需要新的一张
                refreshCaptcha()
                state.toast(tr("验证地址已生成，请先打开完成验证"))
            }
        }
    }

    fun submit() {
        when (mode) {
            LoginMode.LOGIN -> {
                if (account.trim().isEmpty()) {
                    error = tr("请输入用户名")
                    return
                }
                if (password.isEmpty()) {
                    error = tr("请输入密码")
                    return
                }
                if (showLoginCaptcha && captchaText.trim().length < 3) {
                    error = tr("请输入图形验证码")
                    return
                }
                error = null
                loading = true
                state.login(
                    account.trim(), password,
                    if (showLoginCaptcha) captchaId else null,
                    if (showLoginCaptcha) captchaText.trim() else null
                ) { r ->
                    loading = false
                    if (r.ok) return@login
                    if (r.needCaptcha) {
                        showLoginCaptcha = true
                        refreshCaptcha()
                        state.toast(tr("连续登录失败，请先填图形验证码再登"))
                    }
                    error = r.message
                }
            }

            LoginMode.REGISTER -> {
                val name = account.trim()
                if (!usernameRegex.matches(name)) {
                    error = tr("用户名需 3–20 位，只能包含字母、数字、下划线或中文")
                    return
                }
                if (password.length < 6) {
                    error = tr("密码至少 6 位")
                    return
                }
                if (verifyUrl.isNullOrBlank()) {
                    error = tr("请先获取并打开注册验证地址")
                    return
                }
                if (captchaText.trim().length < 3) {
                    error = tr("请输入图形验证码")
                    return
                }
                error = null
                loading = true
                state.register(
                    name, password, nickname.trim(), captchaId ?: "", captchaText.trim()
                ) { r ->
                    loading = false
                    if (r.ok) return@register
                    if (r.needCaptcha || (r.message?.contains("图形验证码") == true)) refreshCaptcha()
                    error = r.message
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Bg.Surface)
                    .clickable { state.updateLanguage(if (Lang.isEn) "zh" else "en") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (Lang.isEn) tr("中文") else "English",
                    color = TextColor.Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta))),
            contentAlignment = Alignment.Center
        ) {
            Text(tr("迹"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(tr("MY足迹"), color = TextColor.Primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (mode) {
                LoginMode.LOGIN -> tr("记录每一段旅程", "Capture every journey")
                LoginMode.REGISTER -> tr("开启你的旅行记录", "Start your travel log")
            },
            color = TextColor.Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(36.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Bg.Surface)
                .cardShadow()
                .padding(20.dp)
        ) {
            Text(
                text = when (mode) {
                    LoginMode.REGISTER -> tr("用户名注册")
                    else -> tr("用户名登录")
                },
                color = TextColor.Primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            LoginField(
                value = account,
                onValueChange = { account = it; error = null },
                hint = if (mode == LoginMode.LOGIN) tr("用户名") else tr("用户名（3–20 位）"),
                icon = Icons.Outlined.Person,
                keyboardType = KeyboardType.Text
            )

            when (mode) {
                LoginMode.LOGIN -> {
                    Spacer(Modifier.height(12.dp))
                    LoginField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        hint = tr("密码"),
                        icon = Icons.Outlined.Lock,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = TextColor.Muted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showPassword = !showPassword }
                            )
                        }
                    )
                    if (showLoginCaptcha) {
                        Spacer(Modifier.height(12.dp))
                        CaptchaField(
                            bmp = captchaBmp,
                            value = captchaText,
                            onValueChange = { captchaText = it.take(6); error = null },
                            loading = captchaLoading,
                            onRefresh = { refreshCaptcha() }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tr("忘记密码？"),
                            color = Brand.Terracotta,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { state.toast(state.passwordResetHint()) }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = tr("没有账号？去注册"),
                            color = TextColor.Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { switchMode(LoginMode.REGISTER) }
                        )
                    }
                }

                LoginMode.REGISTER -> {
                    Spacer(Modifier.height(12.dp))
                    LoginField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        hint = tr("密码（至少 6 位）"),
                        icon = Icons.Outlined.Lock,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = TextColor.Muted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showPassword = !showPassword }
                            )
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    LoginField(
                        value = nickname,
                        onValueChange = { nickname = it; error = null },
                        hint = tr("昵称（可选）"),
                        icon = Icons.Outlined.Person
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = tr("第 1 步：获取注册验证地址（内含一次性验证码）"),
                        color = TextColor.Secondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    CaptchaField(
                        bmp = captchaBmp,
                        value = captchaText,
                        onValueChange = { captchaText = it.take(6); error = null },
                        loading = captchaLoading,
                        onRefresh = { refreshCaptcha() }
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable(enabled = !verifyLoading) { requestLink() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (verifyLoading) {
                            CircularProgressIndicator(color = Brand.Terracotta, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                tr("获取验证地址"),
                                color = Brand.Terracotta,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    verifyUrl?.let { url ->
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Bg.SurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                tr("第 2 步：在浏览器打开该地址完成验证"),
                                color = TextColor.Secondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(url, color = TextColor.Muted, fontSize = 11.sp)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                Text(
                                    tr("打开地址"),
                                    color = Brand.Terracotta,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { openVerifyUrl(url) }
                                )
                                Spacer(Modifier.width(18.dp))
                                Text(
                                    tr("复制链接"),
                                    color = TextColor.Secondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { copyVerifyUrl(url) }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("第 3 步：验证成功后回 App，填新的图形验证码并注册；注册成功地址立即作废。"),
                                color = TextColor.Muted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = tr("已有账号？去登录"),
                        color = Brand.Terracotta,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { switchMode(LoginMode.LOGIN) }
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFD05353), fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))

            val btnInteraction = remember { MutableInteractionSource() }
            val btnRipple = rememberTapRipple()
            val btnScale = pressBounceScale(btnInteraction, 0.97f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .scale(btnScale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta)))
                    .tapRipple(btnRipple) { if (!loading) submit() },
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (mode == LoginMode.REGISTER) tr("注册并登录") else tr("登 录"),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 后端没启动 / 暂时登录不上时，也允许进入 App 使用本地功能
        Spacer(Modifier.height(14.dp))
        Text(
            text = tr("暂不登录，离线使用（足迹、地图、拍摄照常）", "Use offline without signing in"),
            color = TextColor.Secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { state.goOffline() }
                .padding(vertical = 6.dp)
        )
        Text(
            text = tr("离线期间的数据先存本机，登录后自动上传同步", "Offline data uploads after sign-in"),
            color = TextColor.Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // ---------- 历史账号（切换账号用） ----------
        if (mode == LoginMode.LOGIN && state.savedAccounts.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Bg.Outline))
                Text(tr("  切换账号  "), color = TextColor.Muted, fontSize = 11.sp)
                Box(Modifier.weight(1f).height(1.dp).background(Bg.Outline))
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Bg.Surface)
                    .cardShadow()
                    .padding(vertical = 6.dp)
            ) {
                state.savedAccounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                account = acc.account
                                error = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (acc.nickname.take(1).ifBlank { acc.account.take(1) }).uppercase(),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                acc.nickname.ifBlank { "旅行者" },
                                color = TextColor.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text("@" + acc.account, color = TextColor.Muted, fontSize = 11.sp)
                        }
                        Text(
                            tr("移除"),
                            color = TextColor.Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { state.removeSavedAccount(acc.account) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Bg.Outline))
            Text(tr("  其他登录方式  "), color = TextColor.Muted, fontSize = 11.sp)
            Box(Modifier.weight(1f).height(1.dp).background(Bg.Outline))
        }
        Spacer(Modifier.height(18.dp))
        Row {
            ThirdPartyButton("QQ") { state.toast(tr("QQ 登录暂未开放")) }
            Spacer(Modifier.width(28.dp))
            ThirdPartyButton(tr("微信")) { state.toast(tr("微信登录暂未开放")) }
            Spacer(Modifier.width(28.dp))
            ThirdPartyButton(tr("手机")) { state.toast(tr("手机号登录暂未开放")) }
        }

        Spacer(Modifier.height(36.dp))
        Text(
            text = tr("登录即代表同意《用户协议》与《隐私政策》"),
            color = TextColor.Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = tr("登录后自动恢复你的足迹、行程与照片"),
            color = TextColor.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        (state.restoreProgress)?.let { progress ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = progress,
                color = Brand.Terracotta,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 通用输入框：图标 + 输入 + 可选尾部组件 */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(14.dp))
        Icon(icon, contentDescription = null, tint = TextColor.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(hint, color = TextColor.Muted, fontSize = 14.sp) },
                singleLine = true,
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Brand.Terracotta
                ),
                textStyle = TextStyle(color = TextColor.Primary, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** 图形验证码输入行：左侧输入 + 右侧图片（点击图片即换一张） */
@Composable
private fun CaptchaField(
    bmp: android.graphics.Bitmap?,
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(tr("图形验证码"), color = TextColor.Muted, fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Brand.Terracotta
                ),
                textStyle = TextStyle(color = TextColor.Primary, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Bg.Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = tr("图形验证码，点击换一张"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(width = 88.dp, height = 34.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    if (loading) tr("加载中…") else tr("点击加载"),
                    color = TextColor.Muted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun ThirdPartyButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val scale = pressBounceScale(interaction, 0.9f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Bg.SurfaceVariant)
                .border(1.dp, Bg.Outline, CircleShape)
                .tapRipple(ripple, Brand.Terracotta.copy(alpha = 0.12f), onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(label.take(1), color = TextColor.Secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextColor.Muted, fontSize = 11.sp)
    }
}
