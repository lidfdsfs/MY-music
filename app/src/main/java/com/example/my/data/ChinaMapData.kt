package com.example.my.data

import android.content.Context
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/**
 * 中国地图轮廓数据。
 * 数据来源：@svg-maps/china (CC-BY-4.0，基于 MapSVG 中国地图)，
 * 已提取为 assets/china_paths.txt（每行 id|pathData，坐标系 0-774 / 0-569）。
 */
object ChinaMapData {

    /** 地图数据坐标系范围（与提取时一致） */
    const val MAP_MIN_X = -40.15f
    const val MAP_MAX_X = 630.81f
    const val MAP_MIN_Y = -27.31f
    const val MAP_MAX_Y = 542.33f

    const val MAP_W = MAP_MAX_X - MAP_MIN_X   // ~670.96
    const val MAP_H = MAP_MAX_Y - MAP_MIN_Y   // ~569.64

    private var loaded = false

    /** 省级路径（按文件顺序） */
    val provincePaths: MutableList<Path> = mutableListOf()
    val provinceIds: MutableList<String> = mutableListOf()

    /** 加载路径数据（进程内仅一次） */
    fun load(context: Context) {
        if (loaded) return
        try {
            context.assets.open("china_paths.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val idx = line.indexOf('|')
                    if (idx <= 0) continue
                    val id = line.substring(0, idx)
                    val d = line.substring(idx + 1)
                    val path = try {
                        PathParser().parsePathString(d).toPath()
                    } catch (e: Exception) {
                        null
                    }
                    if (path != null) {
                        provinceIds.add(id)
                        provincePaths.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            // 资产缺失时不崩溃，地图显示为空
        }
        loaded = true
    }

    /** 经纬度 → 地图坐标系（近似等距圆柱投影） */
    fun lonLatToMap(lon: Double, lat: Double): Pair<Float, Float> {
        val x = (lon - 73.5) / (135.1 - 73.5) * MAP_W + MAP_MIN_X
        val y = (53.6 - lat) / (53.6 - 18.2) * MAP_H + MAP_MIN_Y
        return x.toFloat() to y.toFloat()
    }

    /** 地图坐标系 → 经纬度（lonLatToMap 的逆运算，用于真实地图打点） */
    fun mapToLonLat(x: Float, y: Float): Pair<Double, Double> {
        val lon = (x - MAP_MIN_X) / MAP_W * (135.1 - 73.5) + 73.5
        val lat = 53.6 - (y - MAP_MIN_Y) / MAP_H * (53.6 - 18.2)
        return lon.toDouble() to lat.toDouble()
    }
}
