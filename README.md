# 留影 LiuYing

留影是 Android 本地摄影地图应用。可在地图上选点，从系统相册导入多张照片，记录拍摄日期、标题和心得，并按地点浏览、编辑及备份作品。项目使用 Kotlin、Compose、MapLibre Native 和 SQLite，支持 Android 8.0 及以上和 OpenGL ES 3.0。

## 维护状态

**离线版暂时停止维护，后续更新集中在 Online（联网版）。** 现有离线版仍可使用，也可通过「数据」导出备份，在 Online 版恢复作品。两版可以并装。

Online 版（`cn.photomap.online`）支持切换简洁底图、街道图和卫星图。它通过 OpenFreeMap 加载道路与景点，通过 EOX 加载卫星影像。联网请求仅用于地图资源，地图服务商会收到瓦片请求和连接信息。照片、EXIF、作品、备注及备份保留在本机。

## 下载与使用

在 [GitHub Releases](https://github.com/Nodobyyy/LiuYing-Personalized-Photography-Map-App/releases) 下载 Online 调试版。安装后，点地图上的「＋」选择拍摄地点，再从系统相册导入照片。点照片标记可查看该地点的作品，点「底图」可切换地图样式，点「数据」可导出或恢复备份。

## 当前局限与后续方向

| 当前局限 | 后续方向 |
| --- | --- |
| 界面视觉和布局仍需打磨，地图控件与信息面板的排布还有优化空间。 | 统一视觉样式，调整控件层级和布局，优化地图浏览、选点及照片整理的交互。 |
| 卫星底图使用 2016 年 Sentinel-2 影像，部分区域与现状有差异。 | 评估更新的卫星影像来源及其覆盖范围。 |
| 当前支持在中国地区选点和记录作品。 | 评估扩展至全球的可行性，包括底图覆盖、地名与选点范围。 |

后续也会继续完善作品管理和地图功能，全球覆盖仍处于评估阶段。

## 历史离线版

离线版（`cn.photomap`）自带全国 Z10 概览、周边背景和 Z7 卫星概览。导入全国包后可使用 Z15 街道数据与 1,995 个城区的精细卫星影像。

历史 APK 和全国包的下载、分卷合并与校验步骤见 GitHub Releases。把合并后的 `LiuYing-China-Z15-offline.zip` 复制到手机，在「数据」中选择 ZIP 导入。请在 ZIP 之外另留至少 **18 GB** 空间。导入成功后可以删除原 ZIP。

## 构建 Online 调试版

需要 JDK 17、Android SDK Platform 35 / Build Tools 35.0.0、Node.js 24（数据工具）。Windows 使用 Gradle Wrapper 8.9。

```powershell
./gradlew.bat :core:test :app:testOnlineDebugUnitTest :app:assembleOnlineDebug :app:lintOnlineDebug
node --test tools/map-bundle.test.mjs
```

Online APK 输出位于 `app/build/outputs/apk/online/debug`。需使用阿里云 Maven 镜像时加 `-PuseMirror=true`。原生测试使用 `assembleOnlineDebugAndroidTest`，测试源码在 `app/src/androidTest`，应在专用模拟器或测试机运行。

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
