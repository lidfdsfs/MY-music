package com.example.my.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import com.example.my.util.ImageExporter
import kotlinx.coroutines.delay
import com.example.my.ui.i18n.tr

/**
 * 长图导出结果面板：年度报告与旅行纪念卡共用。
 * 先生成再让用户选「保存」还是「分享」，不静默写进相册。
 */
@Composable
fun ExportSheet(
    bitmap: Bitmap,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hint by remember { mutableStateOf<String?>(null) }
    val fileName = "$title-${System.currentTimeMillis()}"
    // 面板盖在页面之上：返回键先关面板，而不是把整个页面（播放器/年报）退掉
    BackHandler { onDismiss() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Bg.Surface)
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .navigationBarsPadding()
    ) {
        Text(tr("长图已生成"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${bitmap.width} × ${bitmap.height}", color = Text.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Bg.SurfaceVariant)
                    .clickable {
                        hint = if (ImageExporter.saveToGallery(context, bitmap, fileName))
                    tr("已保存到相册", "Saved to gallery")
                        else tr("保存失败，请重试")
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("保存到相册"), color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brand.Terracotta)
                    .clickable {
                        val uri = ImageExporter.shareUri(context, bitmap, fileName)
                        hint = if (uri != null) {
                            ImageExporter.share(context, uri)
                            ""
                        } else tr("分享失败，请重试")
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.IosShare, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("分享"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (hint != null && hint!!.isNotEmpty()) {
            LaunchedEffect(hint) {
                delay(1800)
                hint = null
            }
            Text(
                hint!!,
                color = Brand.Terracotta,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            tr("关闭"),
            color = Text.Muted,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onDismiss)
                .padding(8.dp)
        )
    }
}
