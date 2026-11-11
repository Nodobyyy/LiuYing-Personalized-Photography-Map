# 留影 (PhotoMap) v1.0.1 正式版更新报告

- **版本信息**：`1.0.1` (`versionCode = 22`)
- **安装包路径**：`deliverables/LiuYing-1.0.1-online-debug.apk`
- **安装包体积**：`502.10 MB`（`526,492,130` 字节，已通过 Android 15/17 `16 KB` 页面对齐校验）
- **SHA-256**：`582b3b188ee70bf202d2580b23fea39b25ffdb268ac1d6980e2bfe374bc183b6`

---

## 1. 实时地球昼夜晨昏线（可开关，默认开启）
- **实时太阳直射点天文计算（[`GlobeBackdrop.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/GlobeBackdrop.kt)）**：
  - 根据当前系统 UTC 时间与 Spencer 天文公式实时计算太阳赤纬角（Solar Declination）与均时差（Equation of Time），精确得出当前时刻太阳直射地球的经纬度坐标。
- **GPU 球面顶点级昼夜与晨昏霞光渲染**：
  - 在构建 3D 地球网格（`Canvas.drawVertices`）时，逐顶点计算该位置与太阳直射点的球面余弦夹角 $\cos Z$：
  - **向阳面（白天）**：保持 100% 原始明亮色彩；
  - **背阳面（黑夜）**：自然压暗至深邃夜色并保留微弱深蓝月光感，既突显昼夜明暗反差，又能清晰辨认夜半球的大陆与海岸轮廓；
  - **晨昏线交界带（Dawn/Dusk Terminator）**：在太阳高度角 `-9° ~ +9°` 的黄昏过渡带采用三次 Smoothstep 平滑插值，并叠加一层温暖的琥珀金大气瑞利散射霞光，随手指旋转地球时 360° 真实贴合球面。
- **独立功能开关（[`MainActivity.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/MainActivity.kt)、[`PhotoMapEngine.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/PhotoMapEngine.kt)）**：
  - 在「底图」弹窗与「数据 -> 地图与缓存」设置页中均新增了 **「实时地球昼夜晨昏线」** 开关，状态自动持久化保存，点击即可实时切换。

---

## 2. 深空繁星动态随机闪烁与亮星十字星芒
- **多频呼吸闪烁（[`GlobeBackdrop.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/GlobeBackdrop.kt)）**：
  - 为 175 颗深空恒星配置各自独立的随机闪烁频率（`1.4 ~ 4.6 rad/s`）与初始相位，采用二次脉冲波形（`pulse = wave * wave`）同时调制星星的透明度与微半径，使星空呈现灵动自然的明暗闪烁。
  - 当亮星闪烁达到峰值（`pulse > 0.65`）时，自动浮现柔和的**十字衍射星芒（Diffraction Spikes）**。

---

## 3. 收敛地球外圈光晕 & 渲染性能优化
- **精致贴合的大气层光晕**：将地球外缘大气日冕光晕半径从 `1.24x` 收敛至 **`1.095x`**，光晕紧凑细腻、不再显得宽大臃肿。
- **零内存分配（Zero-GC）与智能休眠优化**：
  - 缓存深空背景 `LinearGradient`、双星云 `RadialGradient` 与地球大气层 `RadialGradient` 实例，避免 `onDraw` 每帧创建 Shader 对象导致 GC 卡顿。
  - 当视野放大进入平面街道/卫星视图（`zoom >= 4.8`）时自动停止星空闪烁定时器，后台 0% CPU 占用；并在 [`PhotoMapEngine.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/map/PhotoMapEngine.kt) 中缓存地球概览纹理 `Bitmap`，切换底图或深浅色主题时瞬时复用。
