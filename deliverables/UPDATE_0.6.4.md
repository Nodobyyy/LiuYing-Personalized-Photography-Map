# 留影 (PhotoMap) v0.6.4 更新报告

- **版本信息**：`0.6.4` (`versionCode = 20`)
- **安装包路径**：`deliverables/LiuYing-0.6.4-online-debug.apk`
- **安装包体积**：`502.10 MB`（`526,487,770` 字节，已通过 Android 15/17 `16 KB` 页面对齐校验）
- **SHA-256**：`c041f020a1b8aa8123893d8dc60e89813d8f1df780c33e95d5e82680c0dc990c`

---

## 1. 解决手机裸连时沿海区域（如三亚海棠湾）瓦片缺失及海外高倍卫星图加载问题
- **真机实测诊断（通过 ADB 在连接手机 `24129PN74C` 上直接裸连测试）**：
  - 手机此前安装运行的是 `19:11` 安装的 `0.6.2` 版本，当时境外及海岸外瓦片回退至美国 Esri 节点（`services.arcgisonline.com`），手机裸连实测 5 秒超时（`HTTP 000`）。
  - 而在三亚海棠区/英州镇海岸带，由于 `0.6.2` 的 `GcjSatellite.covers()` 仅检测瓦片正中心一个点 `(x+0.5, y+0.5)`，当海岸瓦片的左上角是陆地而中心点恰好落在离岸 2 公里的海面上时，被误判为“中国境外”并发往了超时的美国 Esri 节点，导致海岸线瓦片出现直角黑块/模糊切边。
- **修复方案**（[`GcjSatellite.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/GcjSatellite.kt)、[`MapNetwork.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/MapNetwork.kt)）：
  1. **海岸线多点 + 18km 缓冲带判定（`GcjSatellite.covers`）**：将瓦片归属判定升级为同时检查瓦片**正中心、四角顶点、四边中点及向外扩展 `0.18°`（约 18 公里）近海缓冲带**。任何包含中国海岸线、海湾、岛屿的瓦片全部完整走国内高德卫星 CDN（`webst01-04.is.autonavi.com`），彻底消除三亚等沿海区域的直角切边。
  2. **海外全境直连国内腾讯云 EdgeOne 必应全球卫星 CDN（`t0-t3.dynamic.tiles.ditu.live.com`）**：针对纯海外区域或高德无数据区域，全部请求国内腾讯云 EdgeOne 节点（手机裸连实测 `HTTP 200`，无需 VPN 即可秒级加载全球任意国家最高 Z18 卫星图）。

---

## 2. 修复底部操作坞「地图选点」的“点”字与右侧按钮过近/遮挡问题
- **修复方案**（[`OnlineMapChrome.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/OnlineMapChrome.kt)）：
  - 优化底部悬浮操作坞三枚按钮（`忆往昔` / `地图选点` / `导入照片`）的水平间距与权重配比（调整为 `0.92f : 1.08f : 1.12f`），增大按钮间 `spacedBy(8.dp)` 与内边距，并适度收敛前置图标尺寸为 `16.dp`，确保「地图选点」四个字完整居中显示，与右侧深绿色「导入照片」胶囊按钮之间留有清晰间距，不再遮挡。
