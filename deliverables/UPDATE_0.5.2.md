# 留影 0.5.2 (Online) 更新说明

**发布日期**：2026-11-09  
**应用包名**：`cn.photomap.online`  
**版本号**：`0.5.2` (`versionCode = 10`)  
**安装包文件**：`LiuYing-0.5.2-online-debug.apk` (523,897,491 字节)  
**SHA-256**：`008687b9d67df425fe97db60ab548d42d67f4d3ccd63628fd520ae373df10501`

---

## 1. 液态玻璃（Liquid Glass）与高斯模糊视觉系统

- **多层折射高光与动态磨砂面板 (`FrostedPanel`)**：
  - 在 Android 12+（含 Android 15 / 16 / 17）设备上启用硬件加速 `RenderEffect` 28dp 景深背景模糊，对底图与照片缩略图进行实时/快照虚化。
  - 引入方向性液态玻璃边缘高光（Linear Gradient Specular Rim Border）与表面斜向折射光斑（Specular Sheen），深色模式与浅色模式下均具备清透的玻璃质感与层次感。
  - 所有画笔（`Brush`）与颜色渐变均通过 `remember` 缓存复用，避免重组时的内存分配开销。
- **交互式液态玻璃卡片 (`LiquidGlassCard`)**：
  - 统一重构地点详情页记录卡片、底图模式选择卡片、外观主题切换器、导入补填信息卡片及记录编辑器表单分组容器。
  - 选中态（如当前底图模式、当前主题）提供高光主色描边与柔和容器色过渡。
- **全场景视觉统一**：
  - **主地图悬浮栏 (`OnlineMapChrome`)**：左上角品牌信息卡加入呼吸感主色光点指示器；底图、数据、地球按钮升级为独立液态玻璃胶囊；底部导航操作坞（忆往昔 / 地图选点 / 导入照片）采用磨砂玻璃底座与分割线设计。
  - **地图选点 (`PickLocationPanel`)**：中心准星升级为半透明玻璃环设计；顶部地点搜索框、待定位照片缩略图预览栏与底部选点确认面板全面采用液态玻璃容器。
  - **全屏照片查看器 (`PhotoViewer`)**：顶部「返回」「页码」「信息」按钮与底部照片详情/EXIF 面板均升级为深色液态玻璃悬浮坞，照片双指缩放（1x–6x）与双击缩放顺滑无阻。
  - **忆往昔 (`Memories`)**：顶部导航与纵向翻页卡片采用 30dp 圆角液态玻璃高光边框与 30dp 高斯模糊背景填充。
  - **记录编辑 (`RecordEditor`) 与导入补填 (`ImportReview`)**：待补填照片预览框新增虚化背景衬底，操作区与表单分区卡片化，顶部/底部悬浮操作栏层次分明。
  - **全局提示与加载框 (`SnackbarHost` / `Busy Overlay`)**：提示条与进度加载框均升级为悬浮毛玻璃面板。

---

## 2. 流畅动画与零重排微交互（高性能保证）

- **GPU 硬件加速按压回弹 (`Modifier.liquidPress`)**：
  - 为按钮、胶囊面板与可点击玻璃卡片加入阻尼弹簧（`Spring.DampingRatioMediumBouncy`）按压缩放反馈。
  - 严格在 `Modifier.graphicsLayer` 阶段执行 `scaleX` / `scaleY` 变换，**不触发 Compose 布局重测（Relayout）或重组（Recomposition）**，确保地图浏览与高频操作保持满帧流畅。
- **细腻的状态过渡动画**：
  - 顶部地点与照片统计数字、导入照片按钮文字切换采用 `AnimatedContent` 平滑过渡。
  - 选点搜索状态、搜索结果列表、照片 EXIF 详情面板、相机参数表单均配备 `AnimatedVisibility` + 弹簧 `animateContentSize` 展开/收起过渡。
  - 「忆往昔」纵向翻页画廊加入基于 `graphicsLayer` 的视差缩放与透明度插值过渡，翻页跟手自然。
- **低开销底图快照策略**：
  - 仅在抽屉或面板打开瞬间采集一次地图快照用于 `RenderEffect` 模糊，地图平移、缩放、旋转及地球视图漫游过程中零额外截图或每帧模糊开销。

---

## 3. Android 17 (API 37) 与 16 KB 内存页兼容

- **Edge-to-Edge 与窗口栏兼容**：
  - 适配 Android 15 / 16 / 17 强制全面屏（Edge-to-Edge）规范，在 `FullScreenDialog` 与主界面中针对 API 35+ 跳过已废弃的 `statusBarColor` / `navigationBarColor` 直接赋值，通过透明系统栏与 `WindowInsetsController` 精确控制深浅色图标对比度。
- **16 KB 内存页严格对齐**：
  - 打包产物 `LiuYing-0.5.2-online-debug.apk` 已通过 Android SDK Build-Tools 35.0.0 的 `zipalign -c -P 16 4`（16 KB 页面对齐检查）与 `apksigner verify` 签名验证，完全兼容搭载 16 KB 内存页的 Android 17 系统。
- **功能零改动承诺**：
  - 保持所有原有业务逻辑、SQLite 作品数据库结构、EXIF 解析与参数覆盖、完整 ZIP 备份与恢复、全球内置概览（Z8 矢量 + Z9 道路 + 中国 Z10 + 卫星 Z7）及 OpenFreeMap / EOX 在线瓦片缓存机制完全不变，可直接覆盖安装于旧版 Online 应用之上。
