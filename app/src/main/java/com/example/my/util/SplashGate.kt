package com.example.my.util

/**
 * 启动页的进程级闸门。
 *
 * Android 的 Application.onCreate() 每个进程只执行一次：
 * - 进程被杀掉后重新启动：Application 重建，这里重新置为待放行，冷启动显示一次启动页；
 * - 进程还活着：无论从桌面图标、通知、桌面小组件、最近任务还是应用内入口进来，
 *   都只是复用同一个进程，闸门已经消费过，不再显示启动页。
 *
 * 不能用 SharedPreferences/文件记录“是否启动过”，因为那会把“进程已死但任务还在最近任务里”
 * 也误判成热启动；这里要判断的是进程生命周期，不是任务栈是否存在。
 */
internal object SplashGate {
    private var pending = false

    /** 只在 Application.onCreate() 调用：标记这是一个新进程。 */
    @Synchronized
    fun onProcessCreated() {
        pending = true
    }

    /**
     * 由首个 Activity 消费。返回 true 表示这是本进程的第一次界面启动，应显示启动页；
     * 返回 false 表示进程早已存在（Activity 重建、通知/小组件回到已有进程等）。
     */
    @Synchronized
    fun consumeLaunch(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }

    /**
     * 后台组件（桌面小组件刷新、轨迹/歌词常驻服务、WorkManager）在用户没有打开界面的情况下
     * 把进程拉起来了。此时进程已经存活，再点图标/通知/小组件都属于热启动，不应显示启动页。
     */
    @Synchronized
    fun onBackgroundUse() {
        pending = false
    }

    /** 单元测试用，正式代码不调用。 */
    @Synchronized
    internal fun resetForTest() {
        pending = false
    }
}
