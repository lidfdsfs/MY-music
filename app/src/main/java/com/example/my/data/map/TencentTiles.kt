package com.example.my.data.map

/**
 * 腾讯地图瓦片（免 Key，GCJ-02，与 App 内所有坐标同一坐标系，不需要再做纠偏）。
 *
 * 坑：腾讯瓦片用的是 TMS 的 y 轴，而 osmdroid 传进来的是 XYZ 的 y，
 * 必须翻转成 `2^z - 1 - y`，否则整块底图会上下错位/直接空白。
 * 另外 19 级腾讯只返回占位空图，最高用 18 级。
 */
object TencentTiles {
    const val MIN_ZOOM = 3
    const val MAX_ZOOM = 18

    fun url(style: String, z: Int, x: Int, y: Int): String {
        val ty = (1 shl z) - 1 - y
        return if (style == "satellite") {
            val server = (x + y) % 3
            "https://p$server.map.gtimg.com/sateTiles/$z/${x shr 4}/${ty shr 4}/${x}_$ty.jpg"
        } else {
            val server = (x + y) % 4
            "https://rt$server.map.gtimg.com/tile?z=$z&x=$x&y=$ty&type=vector&styleid=3"
        }
    }
}
