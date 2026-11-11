# 留影 LiuYing

<img src="app/src/online/res/drawable-nodpi/liuying_icon.jpg" alt="留影 App 图标" width="144" height="144">

留影是 Android 本地摄影地图应用。可在地图上选点，从系统相册导入多张照片，记录拍摄日期、标题和心得，并按地点浏览、编辑及备份作品。项目使用 Kotlin、Compose、MapLibre Native 和 SQLite，支持 Android 8.0 及以上和 OpenGL ES 3.0。

## 维护状态

**离线版暂时停止维护，后续更新集中在 Online（联网版）。** 现有离线版仍可使用，也可通过「数据」导出备份，在 Online 版恢复作品。两版可以并装。

Online 版在手机桌面显示为「留影」（`cn.photomap.online`），支持简洁底图、全球街道图和卫星图。地球和地图保持正北朝上，国家、省州与区县边界随缩放逐级显示。最小缩放时可用单指拖动球体，快速横划可让地球持续自转，触摸地图或点「归位」即可停止。

地球视图先显示 APK 内置的街道或卫星概览。放大后先显示本地主要道路，再通过 OpenFreeMap 按需加载街道与景点；卫星底图在全球范围内均采用国内 CDN 节点免 VPN 秒级直连：中国境内（按内置中国版图多边形精准判定）直连高德卫星 CDN（最高 Z18），并在端侧实时完成 GCJ-02 到 WGS-84 的像素级重采样校正，与照片 EXIF GPS 及省市国界严丝合缝；中国境外（或高德高缩放无数据区域）自动切换至部署在腾讯云国内 EdgeOne 节点的必应全球卫星服务（`t0-t3.dynamic.tiles.ditu.live.com`，原生 WGS-84 坐标，国内直连延迟 ~40ms，最高 Z18），并以 Esri World Imagery 作为兜底；同时卫星模式下的叠加路网与地名完全使用内置离线矢量库渲染以消除跨境请求阻塞。手动选点时，输入地名后自动显示候选地点，也可输入经纬度。地图服务商会收到瓦片请求和连接信息，地点搜索服务会收到搜索框中输入的地名。照片、EXIF、作品、备注及备份保留在本机。地图数据署名与许可可在「底图」中查看。

界面采用全屏沉浸式状态栏与导航栏设计（Edge-to-Edge），结合 Android 12–17 硬件级 `RenderEffect` 实时背景模糊、高光折射描边与弹性按压动效（Liquid Glass 液态玻璃风格），配备一体式顶部导航栏、底部悬浮操作坞与全套统一描边的纯矢量图标体系，并完整适配浅色与深色松石绿/森林绿主题。

「忆往昔」打开时定位到去年同一天或最近的拍摄日期。较早的照片在上方，较晚的在下方。

## 下载与使用

在 GitHub Releases 下载最新 Online 调试版（当前最新为 `v0.6.3`）。点「导入照片」可批量读取拍摄日期和 GPS，缺少信息时可补填。点「地图选点」也可先确定位置再添加照片。点照片标记查看作品，点「底图」切换地图样式，点「数据」导出或恢复备份。 [下载页面](https://github.com/Nodobyyy/LiuYing-Personalized-Photography-Map/releases)

## 近期重点更新（v0.5.7 – v0.6.3）

- **全球卫星底图全量接入国内 CDN（免 VPN 秒开）+ 端侧 GCJ-02 实时纠偏**：
  - **中国境内**：直连高德卫星 CDN（`webst01-04.is.autonavi.com`，最高 Z18），通过端侧实时像素重采样消除火星坐标偏移，并结合内置中国国界多边形与 JPEG 魔数校验自动过滤“此区域无卫星图”占位块。
  - **海外全境及高缩放补充**：直连腾讯云国内 EdgeOne 节点承载的必应全球卫星源（`t0-t3.dynamic.tiles.ditu.live.com`，原生 WGS-84 QuadKey 瓦片，国内直连延迟 ~40ms），彻底解决手机不挂代理时海外高倍缩放卫星图（原来请求美国 Esri 节点超时）加载不出来的问题。
- **卫星叠加层零跨境阻塞**：卫星模式开启“叠加道路与地名”时剥离海外 `tiles.openfreemap.org` 请求，100% 由内置离线矢量库（`overview` / `context`）秒级渲染至 Z17.5，配合本地概览与低级瓦片全层级托底，缩放不黑屏。
- **沉浸式液态玻璃 UI 重构**：全屏 Edge-to-Edge 沉浸式状态栏、一体式顶部导航舱、底部悬浮操作坞、1.8dp 统一描边纯矢量图标库，以及深浅双色自适应的磨砂玻璃底部抽屉。

## 当前局限与后续方向

| 当前局限 | 后续方向 |
| --- | --- |
| 海外高倍缩放街道矢量瓦片（OpenFreeMap）受跨境网络质量影响，在弱网环境下首次加载仍有一定延迟（全球卫星底图已实现国内 CDN 直连秒开）。 | 持续优化矢量瓦片缓存策略与预加载管线，进一步提升海外街道图浏览体验。 |
| 卫星模式下的离线叠加路网在极高缩放层级（Z15+）主要保留主干道路与重要地名。 | 探索更轻量的高缩放级别本地路网与地名补充方案。 |
| 全球街道与景点的详细程度受上游 OpenStreetMap 数据覆盖影响。 | 持续优化地图加载与地点搜索体验。 |

## 历史离线版

离线版（`cn.photomap`）自带全国 Z10 概览、周边背景和 Z7 卫星概览。导入全国包后可使用 Z15 街道数据与 1,995 个城区的精细卫星影像。

历史 APK 和全国包的下载、分卷合并与校验步骤见 GitHub Releases。把合并后的 `LiuYing-China-Z15-offline.zip` 复制到手机，在「数据」中选择 ZIP 导入。请在 ZIP 之外另留至少 **18 GB** 空间。导入成功后可以删除原 ZIP。

## 构建 Online 调试版

需要 JDK 17、Android SDK Platform 35 / Build Tools 35.0.0、Node.js 24（数据工具）。Windows 使用 Gradle Wrapper 8.9。

从 [MapLibre Native 的 globe 预发行版](https://github.com/maplibre/maplibre-native/releases/tag/android-v2026.11.2-globe) 下载 `MapLibreAndroid-opengl-release-android-v2026.11.2-globe.aar`，放入 `.tooling/maplibre-globe/`。SHA-256 为 `75837162745f97f36623460c991678f4452311d6de9f1a90895a8e889775c74d`。

```powershell
./gradlew.bat :core:test :app:testOnlineDebugUnitTest :app:assembleOnlineDebug :app:lintOnlineDebug
node --test tools/map-bundle.test.mjs
```

Online APK 输出位于 `app/build/outputs/apk/online/debug`。需使用阿里云 Maven 镜像时加 `-PuseMirror=true`。原生测试使用 `assembleOnlineDebugAndroidTest`，测试源码在 `app/src/androidTest`，应在专用模拟器或测试机运行。

带内置概览的 Online APK 包含全球 Z8 矢量概览和 Z9 主要道路补充层，可在联网细节加载前显示道路及已有路名。小街巷、建筑和景点细节继续按视野联网获取。中国 Z10 街道概览和 Z7 卫星概览也随包提供。

准备下方历史离线版构建步骤所列的基础资源，并将 PMTiles CLI 放到 `.tooling/pmtiles/pmtiles.exe`。使用同一天的 Protomaps 数据制作全球概览：

```powershell
.tooling/pmtiles/pmtiles.exe extract https://build.protomaps.com/20261101.pmtiles .tooling/world-z9.pmtiles --maxzoom=9
node tools/compact-streets.mjs .tooling/world-z9.pmtiles .tooling/world-z8-compact.pmtiles 8
node tools/compact-streets.mjs .tooling/world-z9.pmtiles .tooling/world-roads-z9.pmtiles 9 --roads-only --major-top
node tools/fetch-world-satellite.mjs
node tools/stage-online-overview.mjs
```

准备完资源后再运行 Gradle 构建命令。未准备地图资源时也可构建按需联网的开发包。

地球概览图已随源码提供。更新内置地图数据后，可在 PowerShell 运行 `pwsh -File tools/build-globe-overviews.ps1` 重新生成。街道概览使用内置 Protomaps 地形和 Natural Earth 边界，卫星概览使用内置 EOX 影像。

<details>
<summary>历史离线版构建步骤</summary>

构建离线版前，从已交付的离线 APK 恢复三份地图资源。构建会按 `bundle.json` 核对资源。

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = [IO.Compression.ZipFile]::OpenRead((Resolve-Path 'deliverables/LiuYing-0.3.0-offline-debug.apk').Path)
try {
    foreach ($name in 'china.pmtiles', 'context.pmtiles', 'satellite-overview.pmtiles') {
        $entry = $apk.GetEntry("assets/maps/$name")
        if (-not $entry) { throw "Missing map: $name" }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $PWD "app/src/offline/assets/maps/$name"), $true)
    }
} finally {
    $apk.Dispose()
}
./gradlew.bat :app:testOfflineDebugUnitTest :app:assembleOfflineDebug :app:lintOfflineDebug
```

</details>

地图构建工具在 `tools/`。 [地图数据与许可](docs/MAP_DATA.md) · [架构与数据安全](docs/ARCHITECTURE.md)

## 代码许可

留影公开项目自有源代码，采用 PolyForm Noncommercial 1.0.0。可按条款进行非商业使用、修改和分发。商业使用须另行获得权利人授权。第三方依赖、地图数据和字体分别适用各自的许可。 [许可证全文](LICENSE) · [地图数据与许可](docs/MAP_DATA.md)
