package com.example.my.model

/**
 * 微足迹（抖音式公开足迹流）数据模型。
 *
 * 这一套是「公开池」专用：与个人足迹 [Footprint] 完全分离。
 * Footprint 是用户自己的私密权威数据（Room），微足迹是展示层快照 —— 发布时从
 * Footprint 复制媒体路径生成一条 MicroPost，之后点赞/评论/收藏都只改快照，
 * 绝不会反向污染本机足迹。离线模式下示例内容来自 assets，本人发布的内容落 SP。
 */

/** 微足迹作者：示例作者用负数 id，避免和后端真实用户 id 撞车 */
data class MicroAuthor(
    val id: Int,
    val name: String,
    val avatarText: String,
    /** assets 里的头像路径（file:///android_asset/...）或本机绝对路径 */
    val avatarPath: String? = null,
    val bio: String = "",
    val followers: Int = 0,
    val verified: Boolean = false
)

/** 微足迹评论：本地新增的评论与示例评论共用一种结构 */
data class MicroComment(
    val id: String,
    val authorName: String,
    val text: String,
    val time: String,
    val authorId: Int = 0,
    val replyTo: String? = null,
    val likes: Int = 0
)

/** 一条公开足迹 */
data class MicroPost(
    val id: String,
    val author: MicroAuthor,
    /** 地点标签（如「梅里雪山-日照金山」） */
    val place: String,
    /** 同城筛选用的城市 */
    val city: String,
    val caption: String,
    val tags: List<String> = emptyList(),
    /** 图片路径：assets 用 file:///android_asset 前缀，本地发布用绝对路径 */
    val imagePaths: List<String> = emptyList(),
    val videoPath: String? = null,
    val bgmTitle: String? = null,
    /**
     * 配乐的本地缓存路径（应用私有目录）。
     * 本人足迹来自 [Footprint.bgmPath]；示例帖没有音频文件，只有标题，字段为 null。
     * feed 播放时只把它交给 BgmPlayback.attach()，绝不自己 new MediaPlayer。
     */
    val bgmPath: String? = null,
    val bgmArtist: String? = null,
    val baseLikes: Int = 0,
    val baseComments: Int = 0,
    val baseShares: Int = 0,
    val favorites: Int = 0,
    val liked: Boolean = false,
    val favored: Boolean = false,
    val followed: Boolean = false,
    val comments: List<MicroComment> = emptyList(),
    /**
     * 评论列表是否已经从服务端完整拉过。
     * 本地示例池的 baseComments 是「历史评论数」，和 comments（示例评论）相加；
     * 远端帖的 baseComments 本身就是总评论数，拉回列表后不能再加一遍。
     */
    val commentsLoaded: Boolean = false,
    /** 本人从足迹发布的内容，可撤回 */
    val isMine: Boolean = false,
    val footprintId: Int? = null,
    val publishedAt: Long = 0L
) {
    val isVideo: Boolean get() = videoPath != null
    val likeCount: Int get() = baseLikes + if (liked) 1 else 0
    val commentCount: Int get() = if (commentsLoaded) maxOf(baseComments, comments.size) else baseComments + comments.size
    val shareCount: Int get() = baseShares
    val mediaCount: Int get() = if (isVideo) 1 else imagePaths.size
}
