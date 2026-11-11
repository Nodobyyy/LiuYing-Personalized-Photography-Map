# 留影 LiuYing

<img src="app/src/online/res/drawable-nodpi/liuying_icon.jpg" alt="留影 App 图标" width="144" height="144">

留影是 Android 本地摄影地图应用。可在地图上选点，从系统相册导入多张照片，记录拍摄日期、标题和心得，并按地点浏览、编辑及备份作品。项目使用 Kotlin、Compose、MapLibre Native 和 SQLite，支持 Android 8.0 及以上和 OpenGL ES 3.0。

## 维护状态

**离线版暂时停止维护，后续更新集中在 Online（联网版）。** 现有离线版仍可使用，也可通过「数据」导出备份，在 Online 版恢复作品。两版可以并装。

Online 版（`cn.photomap.online`）支持简洁底图、全球街道图和卫星图。地球和地图保持正北朝上，国家、省州与区县边界随缩放逐级显示。最小缩放时快速横划可让地球持续自转，触摸地图或点「归位」即可停止。

地球视图先显示 APK 内置的街道或卫星概览。放大后先显示本地主要道路，再通过 OpenFreeMap 按需加载街道与景点，通过 EOX 加载卫星影像。手动选点时，输入地名后自动显示候选地点，也可输入经纬度。地图服务商会收到瓦片请求和连接信息，地点搜索服务会收到搜索框中输入的地名。照片、EXIF、作品、备注及备份保留在本机。地图数据署名与许可可在「底图」中查看。

「忆往昔」打开时定位到去年同一天或最近的拍摄日期。较早的照片在上方，较晚的在下方。

## 下载与使用

在 GitHub Releases 下载 Online 调试版。点「导入照片」可批量读取拍摄日期和 GPS，缺少信息时可补填。点「地图选点」也可先确定位置再添加照片。点照片标记查看作品，点「底图」切换地图样式，点「数据」导出或恢复备份。 [下载页面](https://github.com/Nodobyyy/LiuYing-Personalized-Photography-Map/releases)

## 当前局限与后续方向

| 当前局限 | 后续方向 |
| --- | --- |
| 界面视觉和布局仍需打磨，地图控件与信息面板的排布还有优化空间。 | 统一视觉样式，调整控件层级和布局，优化地图浏览、选点及照片整理的交互。 |
| 卫星底图使用 2016 年 Sentinel-2 影像，部分区域与现状有差异。 | 评估更新的卫星影像来源及其覆盖范围。 |
| 全球街道与景点的详细程度受上游数据覆盖影响。 | 持续优化地图加载与地点搜索。 |

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
