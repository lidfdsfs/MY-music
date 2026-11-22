package com.example.my.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.my.data.AiApi
import com.example.my.data.AiPresets
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 端侧视觉分析结果。
 * [translation] 只有开启「图文翻译」且 OCR 识别到了文字时才有值。
 */
data class VisionResult(
    val labels: List<String> = emptyList(),
    val ocrText: String = "",
    val faceCount: Int = 0,
    val barcodes: List<String> = emptyList(),
    val translation: String? = null,
    val error: String? = null
) {
    val hasAnything: Boolean
        get() = labels.isNotEmpty() || ocrText.isNotBlank() || faceCount > 0 || barcodes.isNotEmpty()

    /**
     * 拼成可直接追加到足迹备注末尾的紧凑文案。
     * 逐项拼接、全部为空时返回 null，调用方据此决定要不要写备注。
     */
    fun noteSuffix(): String? {
        if (!hasAnything) return null
        val parts = mutableListOf<String>()
        if (labels.isNotEmpty()) parts += "（${labels.joinToString(" · ")}）"
        if (faceCount > 0) parts += "（$faceCount 人合影）"
        if (barcodes.isNotEmpty()) parts += "（票码 ${barcodes.first().take(24)}）"
        val text = buildString {
            if (parts.isNotEmpty()) append(parts.joinToString(""))
            if (ocrText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("识别文字：")
                append(ocrText.replace('\n', ' ').trim().take(60))
            }
            translation?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append('\n')
                append("译文：")
                append(it.replace('\n', ' ').trim().take(60))
            }
        }
        return text.ifBlank { null }
    }
}

/**
 * 全部端侧视觉能力（ML Kit 本地模型，不联网）：
 *  - 图像标签：理解画面内容（海边、日落、建筑…）
 *  - OCR：识别中英文（菜单 / 车票 / 路牌）
 *  - 人脸：统计合影人数
 *  - 条码：识别门票、商品的二维码 / 条形码
 *  - 翻译：中↔英，模型首次使用需下载（一次性，之后离线可用）
 *
 * 每项都受 [AiCapabilities] 控制，用户可以在设置里单独关掉；
 * 任何一项失败都不影响其它项，[VisionResult.error] 只在全部失败时填。
 */
object VisionAnalyzer {

    /**
     * 分析一张已经解码好的位图（调用方用 PhotoStore 解码，自动处理 EXIF 方向）。
     * [localizeLabels] 用来把英文标签转成中文，沿用发布页已有的词表。
     */
    suspend fun analyze(
        bitmap: Bitmap,
        caps: AiCapabilities,
        localizeLabels: (List<String>) -> List<String> = { it },
        ocrEndpoint: AiPresets.OcrEndpoint = AiPresets.OcrEndpoint()
    ): VisionResult = withContext(Dispatchers.IO) {
        if (!caps.anyVision) return@withContext VisionResult()
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels: List<String> = if (caps.visionLabels) runCatching {
            val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            try {
                val res = Tasks.await(client.process(image), 20, TimeUnit.SECONDS)
                localizeLabels(res.filter { it.confidence > 0.6f }.take(3).map { it.text })
            } finally {
                client.close()
            }
        }.getOrDefault(emptyList()) else emptyList()

        // OCR：优先用用户在「模型管理 - OCR」接入的云端识别端点；未接入时回退端侧 ML Kit
        val ocrLines: List<String> = if (caps.visionOcr) {
            val remote = if (ocrEndpoint.ready) remoteOcr(bitmap, ocrEndpoint) else null
            when {
                remote != null -> remote.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                else -> runCatching {
                    // 中文识别器同时覆盖拉丁字符，中英混排的票根/菜单都能读
                    val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    try {
                        Tasks.await(client.process(image), 25, TimeUnit.SECONDS)
                            .textBlocks.map { it.text.trim() }.filter { it.isNotBlank() }
                    } finally {
                        client.close()
                    }
                }.getOrDefault(emptyList())
            }
        } else emptyList()

        val faces: Int = if (caps.visionFace) runCatching {
            val client = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )
            try {
                Tasks.await(client.process(image), 20, TimeUnit.SECONDS).size
            } finally {
                client.close()
            }
        }.getOrDefault(0) else 0

        val codes: List<String> = if (caps.visionBarcode) runCatching {
            val client = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            )
            try {
                Tasks.await(client.process(image), 20, TimeUnit.SECONDS)
                    .mapNotNull { it.rawValue?.trim()?.takeIf { v -> v.isNotBlank() } }
            } finally {
                client.close()
            }
        }.getOrDefault(emptyList()) else emptyList()

        val ocrText = ocrLines.joinToString(" ")
        val translation = if (caps.visionTranslate && ocrText.isNotBlank()) {
            translate(ocrText)
        } else null

        VisionResult(labels, ocrText, faces, codes, translation)
    }

    /** 调用用户自接入的云端 OCR 端点；失败返回 null，交由调用方回退端侧识别 */
    private suspend fun remoteOcr(bitmap: Bitmap, endpoint: AiPresets.OcrEndpoint): String? {
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return AiApi.ocrRemote(endpoint, base64, "image/jpeg")
    }

    /**
     * 端侧翻译：自动判断中/英，翻到另一种语言。
     * 首次使用会下载约 30MB 的翻译模型；失败（无网 / 模型下载失败）返回 null，不阻断主流程。
     */
    suspend fun translate(text: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val identified = Tasks.await(
                LanguageIdentification.getClient().identifyLanguage(text.take(200)),
                10, TimeUnit.SECONDS
            )
            val source = when (identified) {
                TranslateLanguage.CHINESE -> TranslateLanguage.CHINESE
                TranslateLanguage.ENGLISH -> TranslateLanguage.ENGLISH
                else -> TranslateLanguage.CHINESE
            }
            val target = if (source == TranslateLanguage.CHINESE) TranslateLanguage.ENGLISH
            else TranslateLanguage.CHINESE
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            val translator = Translation.getClient(options)
            try {
                val ok = runCatching {
                    Tasks.await(translator.downloadModelIfNeeded(), 30, TimeUnit.SECONDS)
                }.isSuccess
                if (!ok) return@runCatching null
                Tasks.await(translator.translate(text.take(800)), 20, TimeUnit.SECONDS)
            } finally {
                translator.close()
            }
        }.getOrNull()
    }
}
