package com.example.my.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.VoskRecognizer
import com.example.my.data.ai.VoiceInfo
import com.example.my.data.ai.VoiceIo
import com.example.my.data.ai.VoiceLibrary
import com.example.my.state.AppState
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text

/**
 * 语音库：浏览 / 下载 / 启用 / 试听声音包。
 *
 * 音色由系统 TTS 引擎提供，App 负责目录、下载引导、自动识别新装语音、选择与微调。
 * 底部把「离线语音识别包（Vosk）」也纳入同一处管理，形成统一的语音资源入口。
 */
@Composable
fun VoiceLibraryDialog(state: AppState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var voices by remember { mutableStateOf(emptyList<VoiceInfo>()) }
    var refresh by remember { mutableStateOf(0) }
    var activeId by remember { mutableStateOf(VoiceLibrary.activeId(ctx)) }
    var rate by remember { mutableStateOf(VoiceLibrary.rate(ctx)) }
    var pitch by remember { mutableStateOf(VoiceLibrary.pitch(ctx)) }
    val vosk = remember { VoskRecognizer(ctx) }
    var voskPct by remember { mutableStateOf(-1) }
    var voskStatus by remember {
        mutableStateOf(
            if (vosk.hasModel()) tr("已下载 · 离线识别可用", "Downloaded — ready")
            else tr("未下载 · 约 42MB", "Not downloaded · ~42MB")
        )
    }
    val installed = remember(refresh) { VoiceLibrary.installed(ctx) }
    val pending = remember(refresh) { VoiceLibrary.pendingId(ctx) }

    fun reloadVoices(force: Boolean = false) {
        val onScan: (List<VoiceInfo>) -> Unit = { list ->
            voices = list
            val settled = VoiceLibrary.settlePending(ctx, list)
            if (settled != null) {
                activeId = VoiceLibrary.activeId(ctx)
                refresh++
                state.toast(tr("声音包已安装并启用", "Voice pack installed and activated"))
            }
        }
        if (force) VoiceIo.refreshVoices(ctx, onScan) else VoiceIo.listVoices(ctx, onScan)
    }

    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { reloadVoices(force = true) }

    LaunchedEffect(Unit) { reloadVoices() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg.Surface,
        title = {
            Text(
                tr("语音库 / 声音包", "Voice library"),
                fontWeight = FontWeight.Bold, color = Text.Primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    tr(
                        "当前声音：${VoiceLibrary.activePack(ctx).zh}",
                        "Active: ${VoiceLibrary.activePack(ctx).en}"
                    ),
                    color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "内置音色免下载，直接「启用」；四种内置音色会落到不同的系统音色上，再叠加各自的语速 / 音调，试听时能听出区别。方言 / 外语声音包按需下载：点「下载」打开系统语音数据页，装完返回这里自动识别并启用。音色由系统 TTS 引擎提供，不占 App 安装包体积。",
                        "Built-in voices need no download. Each built-in variant maps to a different system voice plus its own speed / pitch, so previews sound different. Dialect / language packs are downloaded on demand: tap Download to open the system voice-data page; they are detected automatically when you return. Voices come from the system TTS engine, not the APK."
                    ),
                    color = Text.Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))

                VoiceLibrary.CATALOG.forEachIndexed { index, pack ->
                    if (index > 0) VoiceDivider()
                    val ready = VoiceLibrary.systemHas(pack, voices)
                    val isActive = activeId == pack.id
                    val isPending = pending == pack.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr(pack.zh, pack.en),
                                color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                tr(pack.descZh, pack.descEn),
                                color = Text.Secondary, fontSize = 11.sp, lineHeight = 15.sp
                            )
                            Text(
                                tr(pack.sizeZh, pack.sizeEn),
                                color = Text.Muted, fontSize = 11.sp
                            )
                            if (!pack.builtIn && !ready) {
                                Text(
                                    tr("未安装 · 按需下载", "Not installed · download on demand"),
                                    color = Text.Muted, fontSize = 10.sp
                                )
                            }
                            if (!pack.builtIn && installed.contains(pack.id)) {
                                Text(
                                    tr("已下载", "Downloaded"),
                                    color = Brand.Terracotta, fontSize = 11.sp
                                )
                            }
                            if (isPending && !ready) {
                                Text(
                                    tr("等待系统下载完成…返回本页自动识别", "Waiting for system download…"),
                                    color = Brand.Terracotta, fontSize = 11.sp
                                )
                            }
                        }
                        TextButton(onClick = {
                            if (!VoiceIo.preview(ctx, pack)) {
                                state.toast(tr("这台手机没有可用的语音引擎，试听失败", "No TTS engine — preview failed"))
                            }
                        }) {
                            Text(tr("试听", "Preview"), color = Text.Secondary, fontSize = 12.sp)
                        }
                        when {
                            isActive -> Text(
                                tr("使用中", "Active"),
                                color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                            pack.builtIn || ready -> TextButton(onClick = {
                                VoiceLibrary.setActive(ctx, pack.id)
                                activeId = pack.id
                                refresh++
                                if (!VoiceIo.preview(ctx, pack)) {
                                    state.toast(tr("这台手机没有可用的语音引擎，试听失败", "No TTS engine — preview failed"))
                                }
                            }) {
                                Text(tr("启用", "Use"), color = Brand.Terracotta, fontSize = 12.sp)
                            }
                            else -> TextButton(onClick = {
                                VoiceLibrary.setPending(ctx, pack.id)
                                refresh++
                                val ok = runCatching {
                                    installLauncher.launch(VoiceLibrary.installIntent())
                                }.isSuccess
                                val fallback = if (ok) true else runCatching {
                                    installLauncher.launch(VoiceLibrary.settingsIntent())
                                }.isSuccess
                                if (!fallback) {
                                    VoiceLibrary.setPending(ctx, null)
                                    refresh++
                                    state.toast(
                                        tr("当前设备没有语音数据下载入口，请到系统「文字转语音」设置里安装", "No system voice installer found")
                                    )
                                } else if (!ok) {
                                    state.toast(
                                        tr("请在系统设置里下载语音数据，返回本页会自动识别", "Install voice data in system settings, then come back")
                                    )
                                }
                            }) {
                                Text(
                                    tr(if (isPending) "重试" else "下载", if (isPending) "Retry" else "Download"),
                                    color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (!pack.builtIn && installed.contains(pack.id) && !isActive) {
                        TextButton(onClick = {
                            VoiceLibrary.markInstalled(ctx, pack.id, false)
                            refresh++
                        }) {
                            Text(tr("移除记录", "Remove record"), color = Text.Muted, fontSize = 11.sp)
                        }
                    }
                }

                VoiceDivider()
                Text(
                    tr("语音微调（对所有声音生效）", "Voice tuning (applies to all)"),
                    color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    tr("语速 ${"%.1f".format(rate)}x", "Speed ${"%.1f".format(rate)}x"),
                    color = Text.Secondary, fontSize = 11.sp
                )
                Slider(
                    value = rate,
                    onValueChange = { rate = it },
                    onValueChangeFinished = { VoiceLibrary.setRate(ctx, rate) },
                    valueRange = 0.5f..2.0f
                )
                Text(
                    tr("音调 ${"%.1f".format(pitch)}x", "Pitch ${"%.1f".format(pitch)}x"),
                    color = Text.Secondary, fontSize = 11.sp
                )
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    onValueChangeFinished = { VoiceLibrary.setPitch(ctx, pitch) },
                    valueRange = 0.5f..2.0f
                )

                VoiceDivider()
                Text(
                    tr("系统已安装声音（${voices.size}）", "Installed system voices (${voices.size})"),
                    color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                if (voices.isEmpty()) {
                    Text(
                        tr("还没读到系统声音，安装语音数据后回来会自动刷新", "No voices yet — they refresh automatically after install"),
                        color = Text.Muted, fontSize = 11.sp
                    )
                } else {
                    voices.take(12).forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.name, color = Text.Secondary, fontSize = 11.sp, maxLines = 1)
                                Text(
                                    v.locale + if (v.needsNetwork) tr(" · 需联网", " · online") else "",
                                    color = Text.Muted, fontSize = 10.sp
                                )
                            }
                            TextButton(onClick = {
                                VoiceLibrary.setSystemVoice(ctx, v)
                                activeId = VoiceLibrary.activeId(ctx)
                                refresh++
                                if (!VoiceIo.preview(ctx, VoiceLibrary.activePack(ctx))) {
                                    state.toast(tr("这台手机没有可用的语音引擎，试听失败", "No TTS engine — preview failed"))
                                }
                            }) {
                                Text(tr("使用", "Use"), color = Brand.Terracotta, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                // 直接试听这一个系统音色，不经声音包——用户才好比较哪个才真的不同
                                val ok = VoiceIo.preview(
                                    ctx,
                                    com.example.my.data.ai.VoicePack(
                                        id = "sys:" + v.id, zh = v.name, en = v.name,
                                        descZh = "", descEn = "", locale = v.locale,
                                        builtIn = true, sizeZh = "", sizeEn = ""
                                    )
                                )
                                if (!ok) {
                                    state.toast(tr("这台手机没有可用的语音引擎，试听失败", "No TTS engine — preview failed"))
                                }
                            }) {
                                Text(tr("试听", "Preview"), color = Text.Secondary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                VoiceDivider()
                Text(
                    tr("语音识别（输入）", "Speech recognition (input)"),
                    color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("离线语音识别包（Vosk）", "Offline speech model (Vosk)"),
                            color = Text.Secondary, fontSize = 12.sp
                        )
                        Text(voskStatus, color = Text.Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        if (vosk.hasModel()) {
                            state.toast(tr("离线语音模型已就绪", "Offline model ready"))
                        } else if (voskPct >= 0) {
                            state.toast(tr("正在下载 ${voskPct}%，请稍等", "Downloading ${voskPct}%…"))
                        } else {
                            voskPct = 0
                            voskStatus = tr("准备下载…", "Preparing…")
                            vosk.downloadModel(
                                onProgress = { pct ->
                                    voskPct = pct
                                    voskStatus = tr("下载中 $pct%（约 42MB）", "Downloading $pct% (~42MB)")
                                },
                                onDone = { ok ->
                                    voskPct = -1
                                    voskStatus = if (ok) tr("已下载 · 离线识别可用", "Downloaded — ready")
                                    else tr(
                                        "下载失败：${vosk.lastError ?: "未知原因"} · 点击重试",
                                        "Failed: ${vosk.lastError ?: "unknown"} — tap to retry"
                                    )
                                }
                            )
                        }
                    }) {
                        Text(
                            tr(
                                if (vosk.hasModel()) "已下载" else if (voskPct >= 0) "$voskPct%" else "下载",
                                if (vosk.hasModel()) "Downloaded" else if (voskPct >= 0) "$voskPct%" else "Download"
                            ),
                            color = Brand.Terracotta, fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { reloadVoices(); state.toast(tr("已刷新", "Refreshed")) }) {
                Text(tr("刷新声音列表", "Refresh"), color = Brand.Terracotta, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("关闭", "Close"), color = Text.Muted, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun VoiceDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .height(0.5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Text.Muted.copy(alpha = 0.25f))
    )
    Spacer(Modifier.width(1.dp))
}
