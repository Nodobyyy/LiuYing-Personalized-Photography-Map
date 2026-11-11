# 留影 (PhotoMap) v0.6.2 更新报告

- **版本信息**：`0.6.2` (`versionCode = 18`)
- **安装包路径**：`deliverables/LiuYing-0.6.2-online-debug.apk`
- **安装包体积**：`502.10 MB`（已通过 16 KB 页面对齐校验）
- **SHA-256**：`c55d45cfe88210615dafd83c6ba3689add39242415d1172c8dccfe7653350657`

---

## 1. 修复图 1：卫星底图显示“此区域无卫星图”灰底水印问题
- **问题原因**：
  1. 此前 `GcjSatellite` 使用了覆盖整个东亚的粗略矩形框（`72°E~137.8°E, 0.8°N~55.8°N`）判断是否请求高德卫星图，导致印度（如图 1 中的 Jamui / Gidhaur / Jhajha）、东南亚、西伯利亚等紧邻中国的境外区域也被发往高德服务器。
  2. 高德服务器对于中国境外或超高缩放层级无数据的区域，不会返回 HTTP 404，而是返回一张 **HTTP 200、`4235 字节` 的 PNG 灰色水印图（写有“此区域无卫星图”）**，导致拦截器误以为拉取成功并将其渲染上屏，没有触发 Esri 全球卫星源回退。
- **修复方案**（[`GcjSatellite.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/GcjSatellite.kt)、[`MapNetwork.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/MapNetwork.kt)）：
  1. **精准中国国界多边形判定（`ChinaView`）**：改用内置高精度中国国界多边形（含大陆、港澳台及藏南、阿克赛钦、钓鱼岛、南海诸岛）判断瓦片归属；国界外的区域直接走 Esri World Imagery，0 额外请求开销。
  2. **PNG 占位水印图精准识别与自动回退（`isValidImagery`）**：高德真实卫星瓦片恒为 **JPEG 格式（魔数 `FF D8 FF`）**，而“此区域无卫星图”占位图恒为 **PNG 格式（`89 50 4E 47`）**。拦截器现严格校验 JPEG 魔数，一旦遇到高德返回“无卫星图”占位块（包括边境跨界处或 Z17–Z18 偏远无人区），立即自动无缝回退至 Esri 全球高清卫星影像，彻底消除灰色“此区域无卫星图”块！

---

## 2. 修复图 2：深色模式下底部抽屉文字发黑看不清、按钮文字折行与紫色突兀配色
- **问题原因**：
  1. `MapPanelSheet` 使用了 `containerColor = Color.Transparent` 以展现液态玻璃背景，而 Material 3 的 `ModalBottomSheet` 在透明容器下会将内部默认文字颜色 `LocalContentColor` 回退为纯黑 `Color.Black`，导致深色模式下抽屉标题（“本地数据与备份”、“外观主题”、“地图与缓存”、“简洁底图”等）全部变成黑字，在深色毛玻璃上几乎看不清。
  2. `darkColorScheme` 未显式指定 `onPrimary`、`onPrimaryContainer` 等派生色，导致 Material 3 默认混入紫色调（如“导出完整备份”按钮上的深紫字、“真实卫星图”单选圆点与“叠加道路与地名”的紫色开关滑块）。
  3. “导出完整备份”与“从备份恢复”按钮内边距过大，导致窄屏下断成两行（“导出完整备 / 份”、“从备份恢 / 复”）。
- **修复方案**（[`FrostedPanel.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/FrostedPanel.kt)、[`MainActivity.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/MainActivity.kt)）：
  1. **强制注入主题前景文字色**：在 `FrostedPanel` 与 `LiquidGlassCard` 内部通过 `CompositionLocalProvider(LocalContentColor provides colors.onSurface)` 显式绑定主题文字色，并对所有标题和说明文字明确指定 `onSurface` / `onSurfaceVariant`，深色模式下所有标题呈现清晰柔和的亮白/浅青灰，对比度满足 WCAG AAA 标准。
  2. **全面统一松石绿/森林绿语义色板**：完整定义 `darkColorScheme` 与 `lightColorScheme` 的 `onPrimary`、`onPrimaryContainer`、`onSecondaryContainer`、`onSurface`、`onSurfaceVariant`，并为 `Switch` 与 `RadioButton` 配置专属主题色，彻底消除突兀的默认紫色。
  3. **修复按钮断行**：为“导出完整备份”与“从备份恢复”设置 `maxLines = 1, softWrap = false` 及紧凑水平内边距，确保单行完整显示。
