# 留影 LiuYing

留影是 Android 本地摄影地图应用。可在地图上选点，从系统相册导入多张照片，记录拍摄日期、标题和心得，并按地点浏览、编辑及备份作品。项目使用 Kotlin、Compose、MapLibre Native 和 SQLite，支持 Android 8.0 及以上和 OpenGL ES 3.0。

## 两个调试版本

| 版本 | 应用 ID | 底图 |
| --- | --- | --- |
| 留影·离线 | `cn.photomap` | 自带全国 Z10 概览、周边背景和 Z7 卫星概览。导入全国包后使用 Z15 街道与城区卫星影像。 |
| 留影·联网 | `cn.photomap.online` | 通过 OpenFreeMap 加载道路和景点，通过 EOX 加载真实卫星影像。 |

- 两版均可切换简洁底图、街道和卫星图。离线版不会联网。联网版地图客户端只向限定的地图地址请求资源，地图供应商会收到所请求的地图区域与连接信息。照片、EXIF、作品、备注和备份保留在本机。
- 离线版使用原应用 ID，可在同签名的旧版上覆盖安装并保留作品。联网版可与离线版共存。通过“数据”导出作品备份，并在另一版恢复，可迁移作品。
- 把 `LiuYing-China-Z15-offline.zip` 复制到手机，然后在离线版的“数据”里导入。导入时请在 ZIP 之外预留至少 **18 GB** 可用空间，完成后可删除原 ZIP。请直接导入 ZIP。
- 全国街道源为 Protomaps / OpenStreetMap。Z15 是数据层级，实际街道名称与景点完整度取决于源数据。离线精细影像覆盖 GHSL 所列的 1,995 个城区，其他区域使用低分辨率概览。EOX 影像为 2016 年 Sentinel-2 数据。
- 国家层级不单独显示台湾标签，省级层级显示“台湾省”。地图保留南海相关要素及周边背景。公开发布地图的规范性审查仍未完成，Release 构建保持禁用。

## 构建

需要 JDK 17、Android SDK Platform 35 / Build Tools 35.0.0、Node.js 24（数据工具）。Windows 使用 Gradle Wrapper 8.9。

```powershell
./gradlew.bat :core:test :app:testOnlineDebugUnitTest :app:assembleOnlineDebug :app:lintOnlineDebug
node --test tools/map-bundle.test.mjs
```

构建离线版前，把随交付 APK 提供的三份原始地图资源恢复到 `app/src/offline/assets/maps`。以下命令从已验证的离线 APK 提取它们。构建会按 `bundle.json` 核对资源。

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

两版输出位于 `app/build/outputs/apk/{offline,online}/debug`。需使用阿里云 Maven 镜像时加 `-PuseMirror=true`。原生测试使用对应的 `assembleOfflineDebugAndroidTest` 和 `assembleOnlineDebugAndroidTest`，测试源码在 `app/src/androidTest`，应在专用模拟器或测试机运行。

地图构建工具在 `tools/`。全国 ZIP 与 APK 单独交付。 [地图数据与许可](docs/MAP_DATA.md) · [架构与数据安全](docs/ARCHITECTURE.md) · [开发说明](开发说明.md)
