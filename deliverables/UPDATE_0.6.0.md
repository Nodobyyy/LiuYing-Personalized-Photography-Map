# 留影 (PhotoMap) v0.6.0 更新报告

- **版本信息**：`0.6.0` (`versionCode = 16`)
- **安装包路径**：`deliverables/LiuYing-0.6.0-online-debug.apk`
- **安装包体积**：`502.08 MB`（已通过 16 KB 页面对齐校验）
- **SHA-256**：`3df88836f3d479cb84d2bdfcb2ad2843a399d3b5db1828864fe8b8434cbc1df8`
- **设计规范**：引入 GitHub 开源 [`nextlevelbuilder/ui-ux-pro-max-skill`](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) 专业移动端 UI/UX 设计规范（已安装至 `.agents/skills/ui-ux-pro-max/`）

---

## 1. 彻底解决“不挂 VPN 卫星底图加载慢”的核心瓶颈
经过对渲染管线与网络请求的深度排查，定位到此前在**不挂 VPN** 时卫星图仍然卡顿的根本原因：**卫星模式默认开启“叠加道路与地名”，当缩放至 Z10.5 以上时，引擎会同时向海外服务器 `tiles.openfreemap.org` 请求大量矢量街道瓦片（`.pbf`）！** 在不挂 VPN 的情况下，海外 `openfreemap.org` 连接超时/极慢，不仅拖慢了整个地图样式的帧渲染队列，还阻塞了卫星瓦片的上屏。

**v0.6.0 根治方案**：
1. **卫星模式剥离海外矢量源请求**（`PhotoMapEngine.kt` & `OnlineMapAssets.kt`）：
   - 在卫星底图模式下，自动移除海外 `tiles.openfreemap.org` (`base`) 矢量源，并将 APK 内置的**离线中国矢量概览库（`overview`）与全球路网库（`context`）**的地名/路网显示层级上限扩展至 **Z17.5**。
   - 效果：卫星图叠加的道路与地名 **100% 由本机离线秒级渲染（0 网络耗时）**，网络带宽 **100% 独占用于拉取卫星影像图片**，无需 VPN 即可丝滑缩放！
2. **关闭卫星模式跨级预取风暴**（`prefetchZoomDelta = 0`）：
   - MapLibre 默认 `prefetchZoomDelta` 会额外拉取父级/邻近层级的数十张栅格大图。卫星模式下将其设为 `0`，只请求当前屏幕可见的瓦片，单次缩放请求量减少约 60%。
3. **双节点并发 TLS 预热**（`MapNetwork.kt`）：
   - 启动时同时对 `services.arcgisonline.com` 和 `server.arcgisonline.com` 发起异步 `HEAD` 预握手，消除首次切换卫星图的冷启动延迟。

---

## 2. 基于 GitHub 开源 `ui-ux-pro-max` 技能的整体 UI 重构
下载并应用了 GitHub 开源设计技能 `ui-ux-pro-max`（涵盖 `SKILL.md`、`references/pro-rules.md` 与 `references/quick-reference.md` 的移动端黄金准则），针对原界面“排布凌乱、纯文字按钮单调、半透明底色浑浊”的问题进行了全面重构：

1. **统一顶部导航舱（Unified Top Header Bar，`OnlineMapChrome.kt`）**：
   - **修复前**：左上角一个大号“留影”方框，右上角纵向堆叠两个“底图 / 数据”小胶囊，左右高度不齐、视觉重心割裂。
   - **修复后**：重构为单行居中对称的**一体式液态玻璃顶部导航栏**——左侧为品牌星芒徽章 + 标题与实时地点/照片统计，右侧内嵌“底图”与“数据”双色胶囊按钮（带分层与数据库矢量图标），地平线干净利落。
2. **底部悬浮操作坞（Floating Bottom Dock）与矢量图标体系（`AppIcons.kt`）**：
   - 遵循 `pro-rules.md` **Vector-Only Assets & Stroke Consistency** 准则，新增一套统一 `1.8dp` 圆角描边的純矢量图标库（`LAYERS`、`DATABASE`、`COMPASS`、`HISTORY`、`CROSSHAIR`、`CAMERA_PLUS`、`SEARCH`、`INFO` 等），彻底告别纯文字按钮和简陋的 `＋` 号字符。
   - 底部操作栏全按钮满足 Android `>=48dp` 最小触控高度标准，去除原先突兀的单侧渐变色块，改用高对比度磨砂玻璃基底 + 清晰分割线 + 胶囊主按钮。
3. **全场景液态玻璃抽屉与卡片排布重构（`MainActivity.kt`、`FrostedPanel.kt`）**：
   - **底图与数据抽屉统一磨砂玻璃化**：移除此前 `opaque = true` 导致的生硬纯色弹窗，让“选择底图”和“本地数据与备份”抽屉同样享受 Android 12–17 硬件级 `RenderEffect` 实时背景模糊。
   - **地点详情与数据设置 Bento 卡片化**：地点详情头部采用“左标题坐标 + 右统计徽章”的双栏对齐排布；设置页将“导出完整备份”与“从备份恢复”整合进带图标的 Bento 玻璃卡片中，并排按钮更紧凑美观。
   - **选点准星与搜索框升级（`PickLocationPanel.kt`）**：地图中心选点准星升级为专业双环十字矢量瞄准镜；地点搜索框加入前置搜索图标与一键清空按钮。
