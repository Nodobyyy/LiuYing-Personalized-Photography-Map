# 留影 (PhotoMap) v0.6.3 更新报告

- **版本信息**：`0.6.3` (`versionCode = 19`)
- **安装包路径**：`deliverables/LiuYing-0.6.3-online-debug.apk`
- **安装包体积**：`502.10 MB`（`526,487,770` 字节，已通过 Android 15/17 `16 KB` 页面对齐校验）
- **SHA-256**：`8b00f830fc968976cbb19c01f32f2f0fa6762cc15882703d4889bf70cf55520d`

---

## 1. 彻底解决手机不挂代理时“海外/非中国地区高倍卫星图加载不出来或极慢”问题
- **问题根因**：
  - 电脑端因开启了代理虚拟网卡（`198.18.0.x`），访问美国 Esri 卫星服务器（`services.arcgisonline.com`）走的是代理通道；
  - 而在中国用户的**手机端直连（无 VPN/代理）**环境下，当视野移出中国或放大查看海外国家/城市时，此前回退的美国 Esri 节点（`162.125.32.x`）跨境链路丢包严重，高倍缩放并发请求 12~20 张瓦片时极易出现 TCP/TLS 握手超时，导致长时间空白或无法加载。
- **v0.6.3 根治方案**（[`GcjSatellite.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/GcjSatellite.kt)、[`MapNetwork.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/MapNetwork.kt)、[`OnlineMapAssets.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/OnlineMapAssets.kt)）：
  1. **海外全境及高缩放补充接入国内腾讯云 EdgeOne 必应全球卫星 CDN（`t0-t3.dynamic.tiles.ditu.live.com`）**：
     - 经绕过电脑代理、直接绑定物理网卡与国内阿里 DNS（`223.5.5.5`）实测，该服务解析至国内 **腾讯云 EdgeOne 节点（`43.141.x.x`）**，国内手机直连延迟仅 **~40ms**！
     - 覆盖全球所有国家与城市（最高支持至 **Z18+**），且海外区域原生采用国际标准 **WGS-84 坐标系**（无火星坐标偏移），与中国境内经过端侧 GCJ-02 → WGS-84 重采样校正的高德卫星图无缝拼接。
  2. **实现 Web-Mercator `(z, x, y)` 到 QuadKey 的端侧零开销转换（`GcjSatellite.toQuadKey`）**：
     - 在 `MapNetwork` 拦截器中，中国境内继续使用高德卫星 CDN + 端侧实时 WGS-84 像素重采样；
     - 中国境外（或高德在偏远边境 Z17–Z18 返回无数据占位块时），自动转换为 QuadKey 并发往国内 `t0~t3.dynamic.tiles.ditu.live.com` 4 节点并行拉取，同時校验 `X-VE-Tile-Info != no-tile`，若极端情况下无瓦片再以 Esri 作为最终兜底。
  3. **区域级在线卫星图层全层级托底（`OnlineMapAssets.kt`）**：
     - 移除 `satellite-online-world`（Z4–Z7 区域级在线卫星层）的 `maxzoom` 隐藏限制，使得在海外任何地区快速放大至 Z14–Z18 时，已加载的低级别卫星瓦片始终保留在底层托底，过渡丝滑不黑屏。
  4. **启动预热双国内 CDN**：
     - 启动时同时对高德国内节点（`webst01.is.autonavi.com`）与必应国内 EdgeOne 节点（`t0.dynamic.tiles.ditu.live.com`）执行异步 `HEAD` 预握手，国内外卫星图首次切换均能秒开。
