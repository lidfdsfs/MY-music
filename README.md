# MY音乐

面向音乐爱好者的**无广告、轻量、本地优先**跨端音乐播放器：Android 原生版 + iOS 可用的 PWA 版，统一 QQ 音乐绿（#31C27C）视觉风格。核心是播放本身——搜索、播放、后台与锁屏控制、歌单导入、歌词，不做社交与推荐流。

## 特性

- 搜索、在线播放、后台播放与 MediaStyle 锁屏/通知卡片（封面 + 进度 + 上/下一首）
- 歌单导入：粘贴文本、网易云、QQ 音乐歌单
- 歌词、听歌统计、分享卡片、播放器皮肤、极简播放页
- 桌面小组件显示当前歌曲
- 双端可用：Android 原生 App，加 PWA 版（Safari 添加到主屏幕后全屏使用）
- 不内置音源与 Key：曲库接口由使用者自备

## 目录结构

```
app/                     Android 原生版（Kotlin + Jetpack Compose）
 ├─ src/main/java/com/example/my/
 │   ├─ music/            独立音乐 App 入口（MusicAppRoot）
 │   ├─ ui/music/         播放器、导入、统计、皮肤、分享卡片等页面
 │   ├─ ui/screens/       设置、登录、驾驶模式等外围页面
 │   ├─ data/             网络、播放器、本地存储、AI 等数据层
 │   ├─ state/            AppState（全局状态与偏好）
 │   └─ widget/           桌面小组件
 ├─ src/main/jniLibs/     MNN / libllm 等原生库（arm64-v8a）
 └─ src/main/res/         图标、图片等资源
pwa/                     iPhone 可用的 PWA 版（见 pwa/README.md）
gradle/                  版本目录（libs.versions.toml）与 wrapper
```

## 构建 Android 版

环境：JDK 17+、Android Studio（AGP 9.4、Kotlin 2.2.10、compileSdk 37、minSdk 24）。

```bat
gradlew.bat assembleDebug
```

- 只打包 `arm64-v8a`：2017 年后的设备基本是 64 位，砍掉 v7a 可省约 55MB 原生库。要支持老设备，把 `armeabi-v7a` 加回 `abiFilters`。
- release 用本机 `~/.android/debug.keystore` 签名，只为方便直接 `adb install`，不是上架用途。
- `jniLibs` 里的 `libMNN*.so` / `libllm.so` 是播放器与端侧推理依赖，删掉会在运行期 JNI 找不到符号。

## PWA 版（iPhone）

`pwa/` 是独立目录，不影响 Android 工程。部署两步：`pwa/worker.js` 贴到 Cloudflare Worker 当接口代理，再把 `pwa/` 下的静态文件托管到任意静态服务，最后在 iPhone Safari 里「添加到主屏幕」。细节见 [pwa/README.md](pwa/README.md)。

## 需要自己配的东西

| 配置项 | 位置 | 说明 |
|---|---|---|
| 高德 Web 服务 Key | 本机 `local.properties` 的 `AMAP_KEY=xxx` | 不进仓库，由 Gradle 注入 `BuildConfig.AMAP_KEY`；也可在 App 设置页填 |
| 曲库接口 | App 设置页 / `pwa/worker.js` | 仓库不含任何音源或第三方接口凭据 |
| 云端同步、微足迹后端 | `app/src/main/java/com/example/my/data/CloudConfig.kt` | 默认指向作者自建实例；可改成自己的地址，或关掉「云端同步」开关纯本地使用 |
| AI Key | App 设置页 | 不内置，自行填写 |

## 说明与限制

- 本项目是「MY足迹」工程中音乐模块拆出的独立 App，复用同一套 `AppState`、主题与播放器单例，代码结构里因此保留了部分上游模块。
- 曲库、歌词数据来自使用者自行配置的接口，请遵守所在地区的版权规定，仅用于个人学习与自用。

## 许可证

[MIT](LICENSE)
