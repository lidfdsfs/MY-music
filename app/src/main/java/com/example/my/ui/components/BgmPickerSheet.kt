package com.example.my.ui.components

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.my.data.music.BgmPick
import com.example.my.data.music.WebMusicCache
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 足迹配乐选择器。
 *
 * 曲源用的是 App 自己的音乐库（「音乐」页搜歌后下载到 `files/web_music/` 的歌），
 * 好处是全部已落地成真实文件：选中即复制进足迹目录，离线也能放，直链过期也不会变成哑巴。
 * 另外给一个「从文件选择」，选手机里任何一段音频。
 *
 * 关于 Tunetank：它的 MCP 端点现在要求 OAuth Bearer（无 Authorization 直接 401），
 * 已不是「无需认证」，所以没把它接成默认曲源；等授权流程确认后可以再加一条线上曲源。
 */
@Composable
fun BgmPickerSheet(
    onDismiss: () -> Unit,
    onPick: (BgmPick) -> Unit
    ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val songs = remember {
        runCatching { WebMusicCache.list(context) }.getOrDefault(emptyList())
    }
    val filtered = remember(songs, query) {
        val q = query.trim()
        if (q.isEmpty()) songs
        else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
    }

    fun displayNameOf(uri: android.net.Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull().orEmpty().ifBlank { tr("本地音频", "Local audio") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val name = displayNameOf(uri)
            val pick = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "bgm_in").apply { mkdirs() }
                    val ext = name.substringAfterLast('.', "mp3").lowercase()
                    val dst = File(dir, "bgm_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { out -> input.copyTo(out) }
                    }
                    if (!dst.exists() || dst.length() <= 0L) null
                    else BgmPick(
                        path = dst.absolutePath,
                        title = name.substringBeforeLast('.').ifBlank { name },
                        artist = null,
                        source = tr("本地音频", "Local audio")
                    )
                }.getOrNull()
            }
            busy = false
            if (pick == null) {
                AppBanner.show(tr("这段音频读不出来，换一个试试", "Could not read this audio file"))
            } else {
                onPick(pick)
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(Bg.Surface)
                .padding(18.dp)
        ) {
            Text(tr("选择配乐", "Pick a soundtrack"), color = Text.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                tr("从音乐库里已经下载好的歌里挑一首，或直接从手机选一段音频。",
                    "Pick a downloaded track from the music library, or choose an audio file."),
                color = Text.Muted, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            // 曲库可能还没下载过任何歌，此时给出明确引导，而不是给一个空白列表
            if (songs.isEmpty()) {
                Text(
                    tr("音乐库里还没有已下载的歌：先去「音乐」页搜歌并下载，或用下面的「从文件选择」。",
                        "No downloaded tracks yet: download a song on the Music tab, or use “Choose a file”."),
                    color = Text.Secondary, fontSize = 12.sp
                )
            } else {
                OutlinedSearchField(query = query, onQuery = { query = it })
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .clickable(enabled = !busy && song.path.isNotBlank()) {
                                    onPick(
                                        BgmPick(
                                            path = song.path,
                                            title = song.title,
                                            artist = song.artist.ifBlank { null },
                                            source = tr("音乐库", "Music library")
                                        )
                                    )
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                                    color = Text.Muted, fontSize = 11.sp, maxLines = 1
                                )
                            }
                            Text(
                                com.example.my.ui.i18n.tr("选择", "Use"),
                                color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(FixedInk.ChipSurface)
                        .clickable(enabled = !busy) { filePicker.launch(arrayOf("audio/*")) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        if (busy) tr("正在读取…", "Reading…") else tr("从文件选择音频", "Choose an audio file"),
                        color = FixedInk.OnChip, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    tr("取消", "Cancel"),
                    color = Text.Secondary, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = !busy) { onDismiss() }.padding(8.dp)
                )
            }
        }
    }
}

/** 轻量搜索框：只在本页过滤，不打网络请求 */
@Composable
private fun OutlinedSearchField(query: String, onQuery: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(tr("搜索歌名或歌手", "Search title or artist"), color = Text.Muted, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 13.sp)
    )
}
