# 留影 LiuYing

<img src="app/src/online/res/drawable-nodpi/liuying_icon.jpg" alt="留影 App 图标" width="144" height="144">

留影是 Android 本地摄影地图应用。可在地图上选点，从系统相册导入多张照片，记录拍摄日期、标题和心得，并按地点浏览、编辑及备份作品。项目使用 Kotlin、Compose、MapLibre Native 和 SQLite，支持 Android 8.0 及以上和 OpenGL ES 3.0。

## 维护状态

**离线版暂时停止维护，后续更新集中在 Online（联网版）。** 现有离线版仍可使用，也可通过「数据」导出备份，在 Online 版恢复作品。两版可以并装。

Online 版（`cn.photomap.online`）支持简洁底图、全球街道图和卫星图。缩至最小可拖动完整地球，放大时按需通过 OpenFreeMap 加载道路与景点，并通过 EOX 加载卫星影像。手动选点时可搜索地名（优先使用 Android 系统服务，必要时使用 Photon），也可直接输入经纬度。地图服务商会收到瓦片请求和连接信息，地点搜索服务会收到用户主动提交的地名。照片、EXIF、作品、备注及备份保留在本机。

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

从 [MapLibre Native 的 globe 预发行版](https://github.com/maplibre/maplibre-native/releases/tag/android-v2026.10.19-globe) 下载 `MapLibreAndroid-opengl-release-android-v2026.10.19-globe.aar`，放入 `.tooling/maplibre-globe/`。SHA-256 为 `a05fedd9eb1eddf90792f0938bf573c79f15b8dd24d1a5ecd1289916d89e3531`。

```powershell
./gradlew.bat :core:test :app:testOnlineDebugUnitTest :app:assembleOnlineDebug :app:lintOnlineDebug
node --test tools/map-bundle.test.mjs
```

Online APK 输出位于 `app/build/outputs/apk/online/debug`。需使用阿里云 Maven 镜像时加 `-PuseMirror=true`。原生测试使用 `assembleOnlineDebugAndroidTest`，测试源码在 `app/src/androidTest`，应在专用模拟器或测试机运行。

要构建带内置概览的 Online APK，先准备下方所列三份 PMTiles，再运行 `node tools/fetch-world-satellite.mjs .tooling/world-satellite.pmtiles <pmtiles 可执行文件路径>` 和 `node tools/stage-online-overview.mjs app/src/offline/assets/maps <pmtiles 可执行文件路径>`。此步骤校验并打包全球 Z6 矢量概览、全球 Z2 卫星概览、中国 Z10 街道和中国 Z7 卫星概览。未准备内置资源时，源码仍可构建按需联网的开发包。

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
