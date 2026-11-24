# 留影 (PhotoMap) v1.1.0 更新日志

- **版本信息**：`1.1.0` (`versionCode = 23`)
- **安装包路径**：`deliverables/LiuYing-1.1.0-online-debug.apk`
- **安装包体积**：`502.15 MB`（`526,541,282` 字节，已通过单元测试与 Lint 校验）
- **SHA-256**：`27e45e7af5d89eff05f8ee673530a866cc382bae9d36e7b5473015333bd82d09`

---

## 1. 主界面布局与信息架构重构（更通透、不拥挤、更顺手）
- **轻量化悬浮顶栏 + 新增「我的足迹」快捷跳转抽屉**（[`OnlineMapChrome.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/OnlineMapChrome.kt)、[`MainActivity.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/MainActivity.kt)）：
  - 告别原先横贯屏幕顶部的笨重宽条，改为**左侧紧凑品牌足迹胶囊 + 右侧两枚 `46dp` 圆形玻璃图标按钮（底图图层 / 设置与备份）**，大幅释放地图与地球顶部视野。
  - **点击左上角「留影 · N 地点 · M 张」胶囊**即可直接呼出全新的 **「我的足迹」地点列表抽屉**（带封面缩略图、照片数量与坐标），点击任意地点直接平滑飞往地图对应位置并展开详情，再也不用在地球上费力寻找小标记。
- **底部悬浮操作坞重构（彻底杜绝文字拥挤与截断）**：
  - 「忆往昔」与「地图选点」改为现代摄影地图通用的**图标在上、文字在下的双行触控区**，配合右侧宽大醒目的翡翠绿「导入照片」主按钮，在任意尺寸手机屏幕上都舒展清晰。
- **「底图与视图」与「设置与数据」职责分离（消除重复设置项）**：
  - **底图与视图**改为**三列并排可视化卡片（简洁 / 街道 / 卫星）**，无需上下滚动即可一键切换，下方直接排列「叠加道路与地名」与「实时地球昼夜晨昏线」开关。
  - **设置与数据**聚焦于本地数据备份/恢复、外观主题、地图缓存清理与许可信息，不再重复堆砌底图单选列表。

---

## 2. 核心页面交互逻辑全面革新（解决反人类操作痛点）
- **沉浸式全屏看图器（[`PhotoViewer.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/PhotoViewer.kt)）**：
  - 修复此前与 `FullScreenDialog` 叠加导致的**双重系统栏留白**问题，实现真正全出血（Full-Bleed）全屏看图。
  - 新增**单击照片隐藏/显示全部界面控件**的沉浸模式，双击 2.5 倍缩放、双指 6 倍缩放流畅自然。
  - 将**「编辑」与「删除」快捷图标按钮直接置入底部操作栏**，不再需要点开“信息”后再翻到底部寻找；点击“信息”改为平滑滑出的悬浮毛玻璃 EXIF 面板，不再挤压照片显示区域。
- **新建/编辑记录 & 批量导入补填升级（[`RecordEditor.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/RecordEditor.kt)、[`ImportReview.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/ImportReview.kt)）**：
  - **新增 Material 3 原生日历选择器（`PhotoDatePickerDialog`）**：点击日期输入框右侧日历图标即可可视化选择日期，彻底告别手敲 `YYYY-MM-DD`。
  - **批量导入顶部新增胶片缩略图条（Filmstrip）**：用直观的横向缩略图条取代原先笨拙的“上一张 / 下一张”按钮，每张照片右上角用绿勾 / 琥珀点实时标明信息是否齐备，点击即可秒切。
  - **智能取消与流程重排**：在新建记录中将「选择照片」前置为第一步（并给出清晰的数量与缺失提示）；若新建草稿尚未添加照片或输入文字，点“取消”直接关闭不再弹出多余确认弹窗；同时修复了编辑页与导入页的双重系统栏内边距问题。
- **地点详情页与「忆往昔」体验升级（[`MainActivity.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/MainActivity.kt)、[`Memories.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/Memories.kt)）**：
  - 地点详情页中，**单图记录升级为全宽 `204dp` 影院级大图卡片**，多图记录升级为 `144dp` 圆角画廊；将生硬的“记录选项 / 地点选项”文字按钮替换为精致的圆形 `···` 菜单按钮。
  - 「忆往昔」移除照片底部的突兀留白，改为全出血沉浸卡片，顶栏新增页码胶囊（`当前页 / 总天数`），卡片右下角新增 **「看地图」** 按钮，一键从回忆跳回拍摄地。
- **地图选点精准度优化**：
  - 选点中心准星改为**中空精密十字靶环**，不再用半透明实心圆遮挡正中心的街道或建筑像素，并增加键盘避让（`imePadding`）。

---

## 3. 视觉美学与质感精修
- **全新黑曜石/画廊白双色语义色板**：深色模式升级为深邃黑曜石板岩（`#10171A`）搭配莹润薄荷翡翠绿（`#6EE7B7`），浅色模式升级为洁净画廊象牙白（`#F8FAF9`）搭配植物深绿（`#0F5C44`），文字与边框对比度全面符合 WCAG 标准。
- **修复小胶囊毛玻璃残影（[`FrostedPanel.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/FrostedPanel.kt)）**：将静态地图快照模糊限定在全宽底部抽屉与全屏编辑器内使用，悬浮小胶囊与导航坞改用高通透度渐变磨砂与方向性高光细边框，消除闪烁与错位的同时显著降低 GPU 开销。
- **扩充统一 `1.8dp` 描边矢量图标库（[`AppIcons.kt`](file:///c:/Users/Nodoby/Desktop/PhotoMap/app/src/main/java/cn/photomap/ui/AppIcons.kt)）**：新增 `CALENDAR`、`MAP_PIN`、`MORE_VERT`、`SLIDERS`、`CHEVRON_RIGHT`、`GLOBE` 图标，全应用视觉语言高度统一。
