# 留影 (PhotoMap) v1.0.0 正式版更新报告

- **版本信息**：`1.0.0` (`versionCode = 21`)
- **安装包路径**：`deliverables/LiuYing-1.0.0-online-debug.apk`
- **安装包体积**：`502.09 MB`（`526,475,746` 字节，已通过 Android 15/17 `16 KB` 页面对齐校验）
- **SHA-256**：`2324da67a374e9c463fe7336847d7af71431c69e884afcc2cc5acfe5507792f3`

---

## 1. 深空星云背景与 3D 球面大气层光效重构
- **多层次深空星野与星云背景（[`GlobeBackdrop.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/GlobeBackdrop.kt)）**：
  - 告别原先地球外侧单调的纯黑底色，引入三阶深邃宇宙渐变背景（`#030812` → `#060F1E` → `#040B16`）。
  - 在球体后方绘制左上天青色与右下松石绿双层柔和极光星云（Galactic Nebula），并铺设 165 颗固定种子生成的冰蓝、暖白、柔金、青碧多色温繁星（亮星带有柔和星芒光晕）。
- **地球外缘大气电离辉光与 3D 球面立体光影**：
  - 在地球边缘绘制柔和散射的蓝色大气层日冕光晕（Atmospheric Corona）与 `1.35dp` 高光亮边（Rim Light）。
  - 在球面上叠加左上日照高光与右下边缘晨昏暗影（3D Spherical Shading），使地球在深空背景中呈现出真实的太空悬浮立体感。

---

## 2. 深色模式下地球与底图配色全面升级
- **深海午夜蓝 + 青黛板岩配色体系（[`globe-streets-night.png`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/online/assets/maps/globe-streets-night.png)、[`PhotoMapEngine.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/PhotoMapEngine.kt)、[`OnlineBoundaries.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/OnlineBoundaries.kt)、[`build-globe-overviews.ps1`](file:///c:/Users/Nodoby/Desktop/PhotoMap/tools/build-globe-overviews.ps1)）**：
  - 移除原先发闷浑浊的暗灰绿配色（旧海洋 `#172F3B` + 旧陆地 `#202D25`），升级为高对比度、富有科技质感的**深邃午夜海蓝（`#0A1929`）海洋 + 青黛板岩（`#182C36`）陆地 + 冰青色（`#7CA3B5`）国家与省界线**。
  - 地球整球视图与放大后的平面矢量底图采用完全一致的深色海陆色板，缩放切换无缝衔接。

---

## 3. 底部悬浮操作坞「地图选点」文字排版精调
- **彻底消除“点”字右侧挤压（[`OnlineMapChrome.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/OnlineMapChrome.kt)）**：
  - 根据三枚按钮的字数差异（`忆往昔` 3字 / `地图选点` 4字 / `导入照片` 4字），将宽度权重精准调整为 **`0.84f : 1.16f : 1.06f`**，并将前置图标收敛至 `15.dp`、图文间距设为 `3.dp`，让中间的「地图选点」获得最充裕的横向空间，四个字完整舒展居中。
