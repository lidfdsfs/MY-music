package com.example.my.util.livephoto

/**
 * Motion Photo 的 XMP 构建与解析。
 *
 * 安卓动态照片靠 JPEG 里的 XMP 说明「这是动态照片、尾部挂了多大的视频」。两套字段并存是常态：
 * - 新（Google Photos / Pixel）：Camera:MotionPhoto + Container:Directory（列出主图与视频两条）；
 * - 旧（早期 Pixel / 部分小米）：GCamera:MicroVideo + GCamera:MicroVideoOffset（从文件尾算的视频长度）。
 * 两个都写兼容性最好。
 */
object XmpMotion {

    class Item(val semantic: String, val mime: String, val length: Long?)

    class Info(
        val motion: Boolean,
        val items: List<Item>,
        val microVideoOffset: Long?,
    )

    fun build(
        primaryLen: Long,
        videoLen: Long,
        legacy: Boolean = true,
        extraAttrs: Map<String, String> = emptyMap(),
    ): ByteArray {
        val legacyAttr = if (legacy) {
            "\n    GCamera:MicroVideo=\"1\"\n" +
                "    GCamera:MicroVideoVersion=\"1\"\n" +
                "    GCamera:MicroVideoOffset=\"$videoLen\""
        } else ""
        val extra = extraAttrs.entries.joinToString("") { "\n    ${it.key}=\"${it.value}\"" }
        val text = buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"MY足迹\">\n")
            append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("  <rdf:Description rdf:about=\"\"\n")
            append("    xmlns:Camera=\"http://ns.google.com/photos/1.0/camera/\"\n")
            append("    xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"\n")
            append("    xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"\n")
            append("    xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"\n")
            append("    Camera:MotionPhoto=\"1\"\n")
            append("    Camera:MotionPhotoVersion=\"1\"\n")
            append("    Camera:MotionPhotoPresentationTimestampUs=\"0\"")
            append(legacyAttr)
            append(extra)
            append(">\n")
            append("   <Container:Directory>\n    <rdf:Seq>\n")
            append("     <rdf:li rdf:parseType=\"Resource\">\n")
            append("      <Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"$primaryLen\" Item:Padding=\"0\"/>\n")
            append("     </rdf:li>\n")
            append("     <rdf:li rdf:parseType=\"Resource\">\n")
            append("      <Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"$videoLen\" Item:Padding=\"0\"/>\n")
            append("     </rdf:li>\n    </rdf:Seq>\n   </Container:Directory>\n")
            append("  </rdf:Description>\n </rdf:RDF>\n</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>")
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    fun parse(packet: ByteArray): Info = parse(String(packet, Charsets.UTF_8))

    fun parse(text: String): Info {
        val motion = Regex("MotionPhoto\\s*=\\s*\"1\"").containsMatchIn(text) ||
            Regex("MicroVideo\\s*=\\s*\"1\"").containsMatchIn(text)
        val offset = Regex("MicroVideoOffset\\s*=\\s*\"([0-9]+)\"").find(text)
            ?.groupValues?.get(1)?.toLongOrNull()
        val items = mutableListOf<Item>()
        Regex("<Container:Item([^<>]*)>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { match ->
            val attrs = match.groupValues[1]
            fun attr(name: String): String? =
                Regex("$name\\s*=\\s*\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)

            val semantic = attr("Semantic").orEmpty()
            val mime = attr("Mime").orEmpty()
            val length = attr("Length")?.toLongOrNull()
            if (semantic.isNotEmpty() || mime.isNotEmpty()) items.add(Item(semantic, mime, length))
        }
        return Info(motion, items, offset)
    }

    /** 尾部视频长度：优先新版 MotionPhoto 条目，退回旧版偏移 */
    fun videoLength(info: Info): Long? {
        info.items.lastOrNull { it.semantic.equals("MotionPhoto", true) || it.semantic.equals("Video", true) }
            ?.length?.let { return it }
        info.items.lastOrNull { it.mime.startsWith("video/") }?.length?.let { return it }
        return info.microVideoOffset
    }
}
