# 验证记录

## 0.3.0 构建与测试环境

留影·离线和留影·联网分别构建。核心 JVM 测试 13 项、离线应用单元测试 15 项、联网应用单元测试 14 项、地图工具测试 6 项通过。变体不适用的测试按条件跳过。两版 Lint 均为 0 错误、10 警告。

Android 11 使用 API 30、4 KB 页和 OpenGL ES 3.1 配置。Android 17 使用 API 37、16 KB 页模拟器。两版 APK 的原生库和 ZIP 均检查 16 KB 对齐。离线版与 0.2.0 使用同一签名。

17 GB 全国包使用 ZIP64。初次测试定位到原打包工具的 STORED 条目使用数据描述符，Android 无法完整读取。现用 JDK 写入带大小和 CRC 的本地 ZIP64 头。交付目录保存实际 APK 与 ZIP 哈希和对应原生测试记录。

骁龙 8 至尊版 Android 17 真机尚未连接。正式地图审查仍待完成。

## 0.3.0 原生验收

两个模拟器分别通过 7 项原生检查，包括首启与相机范围、联网街道与真实卫星、缓存清理后保留本机照片、全国 ZIP64 解包和重复导入、离线地图及摄影流程。离线检查关闭 Wi-Fi 和移动数据。北京、成都、上海、台北抽查街道和景点标签，天坛检查错误水域已消除。首次解包已完成后，使用修正过的 JSON 断言复跑，确认重复导入复用原目录。源文件和两版 APK 哈希以交付目录为准。

## 0.2.0：2026-11-03

本轮五项图片反馈与版图更正的说明见 UPDATE_0.2.0.md。当前构建 core 13 项、Robolectric 11 项及 Node 6 项通过；Lint 为 0 errors、10 warnings。新增覆盖视口约束、标记尺寸/聚合间距、34 省级名称、中国重点位置及北京街道数据复制。

同一最终 APK 在 Android 11 / 4 KB 与 Android 17 / 16 KB 模拟器各执行 2 项原生测试通过，日志含 APK SHA-256。验证真实路名/POI 渲染、国家视图隐藏台湾文字、省级视图同级显示台湾省、选点隐藏照片、越界拖动修正、Activity 重建和作品数据工作流。北京样本实际渲染道路文字 14 项、POI 文字 47 项。

Android 17 另实际导入完整 4,292,745,830 字节全国增强 ZIP，并切换到成都验证道路和 POI 可见，1 项通过；本次模拟器导入与渲染测试约 69 秒。测试后仅移除新增测试地图，不删除摄影作品。普通与大包日志、画面和最终 SHA 见 deliverables。

版图检查保留 34 区域、8 个境界线要素与南海岛屿，针对达旺、阿克赛钦、台湾、钓鱼岛、香港、澳门验证参考几何。使用注明 WGS84 的中国数据替换开发过程中不适用的通用陆地轮廓；完整参考源与许可随 APK 保留。边界规范性和公开发布审批仍未完成，参考图审图号未赋给应用。

街道渲染测试曾发现复杂国界对整条道路进行 within 判断会阻塞；已改为线文字遮罩、点文字简化可见性判断。越界测试另发现纯尺寸公式与原生投影边缘存在差异；现直接依据原生可见视口约束并留小余量。这些测试失败未作为通过结果交付。

仍无用户骁龙 8 至尊版物理手机的连接日志，未把模拟器结果作为该机型或旧手机性能验收。

## 0.1.1 历史记录

更新：2026-11-03，Asia/Shanghai。源码、数据与最终交付对应 0.1.1 研发调试版本。

## 工具与构建

本机已准备 JDK 17.0.2、Gradle 8.9、Android SDK 35 / Build Tools 35.0.0、Node、adb 和项目专用 Android 11 与 Android 17 测试模拟器。工具仅在 .tooling 内，不修改系统 JAVA_HOME。Gradle 和 JDK 与官方 SHA-256 匹配；模拟器及系统镜像与 Google 仓库 SHA-1 匹配。Lint 为 0 errors、9 warnings：竖屏限制 4 项、保守磁盘空间检查 4 项，以及为崩溃检测同步提交启动标记 1 项。

普通构建包含 core:test、app:testDebugUnitTest、assembleDebug、lintDebug。真实文件压力测试单独使用 includeStress=true，避免每次小修改重建一万张照片。

## 自动化证据

- Node 地图包工具 6 项：路径安全、本地资源、篡改、联网资源、字形缺失、坐标系。
- core 聚合 6 项、Archive 5 项：数量与稳定性、ZIP 往返、越界、大小限制、校验失败、未声明文件。
- Robolectric Library 7 项：多记录、重复图片、编辑位置/记录/照片、EXIF 日期/GPS/旋转、损坏图片批次继续、损坏 ZIP/数据库保留现有作品、目录切换中断恢复。
- Robolectric OfflineBundle 1 项：实际内置地图复制、全量哈希与本地 URI 改写，无 HTTP/HTTPS 引用。
- Robolectric MapPackages 2 项：含目录项的有效地区 ZIP 导入/移除、恶意路径拒绝。
- 真实 JPEG 数据压力测试：100/500/1000 地点及 1000/5000/10000 原图与缩略图，全部数量匹配。
- Android 11 与 Android 17 各执行原生 instrumentation 2 项：真实系统 EXIF/解码/SQLite/重启/编辑/备份恢复，以及真实 PMTiles 矢量与文字渲染、国家视图隐藏台湾标签、省级视图同时显示台湾省与大陆省名、Activity 重建；数据工作流只写入独立测试缓存，不清除作品。

测试期间发现并修复旧 SQLite 不支持的相关子查询 ORDER BY、MapLibre 必需网络状态权限缺失导致的启动崩溃、地区 ZIP 目录项拒绝、恢复数据库必需字段校验和巨型资源空间预检查在 Robolectric 重复分配内存的问题。

## 地图证据

官方 PMTiles verify 通过；全部 31,763 份内容 gzip CRC 通过；256 段字形的源 SHA-1 和大小匹配。底图和全部资源实际随 APK 打包。App 没有 INTERNET、定位、Wi-Fi 状态或广泛存储读取权限；保留 ACCESS_NETWORK_STATE 只读权限满足 MapLibre 广播接收器。

原生界面与首次无网启动截图、日志在 deliverables 中保存；最终 APK 大小、签名和 SHA-256 以 deliverables/BUILD_INFO.txt、SHA256SUMS.txt 为准。

## Android 17 与用户反馈的启动闪退

用户反馈骁龙 8 至尊版 / Android 17 旧包启动闪退，当前未连接该物理设备，无法取得其原始 logcat，不能确认唯一根因或宣称该手机已经修复。旧探针包与新版应用 ID 不同；本次应安装 PhotoMap-0.1.1-debug.apk（cn.photomap）。

当前内置 MapLibre 11.8.0 已包含上游从 11.5.0 引入的 16 KB 支持；tools/check-apk-native.mjs 实际逐个检查最终 APK 的 ARM64/x86_64 PT_LOAD 对齐与未压缩 SO 的 ZIP 位置，Google zipalign -c -P 16 4 及 apksigner 验证通过。32 位库保留给旧设备，16 KB 系统检查针对 64 位 ABI。现有 0.1 代码的 ARM64 对齐也通过，不能把 16 KB 对齐猜测成已证实的旧包故障原因。

Google 官方 x86_64-ps16k-37.0_r08.zip，SHA-1 3e04f6c168a573640e63fdd8afd71a3c4d947f64。实际设备属性 Android 17、API37、getconf PAGE_SIZE=16384；4GB RAM，1080×1920，WHPX / AMD 主机 GPU。在飞行模式及 Wi-Fi/移动数据关闭状态，首次安装成功加载内置全国地图，原生测试通过。启动测试包含页面重建，不只检查安装成功。Activity 的启动耗时不等于完整地图首屏时间，不作为性能达标声明。

受控故障注入另外使用 adb am crash（shell-induced crash），验证有未完成启动标记且退出原因为崩溃时，下次启动显示恢复页、可经系统 SAF 导出含设备/API/ABI/内存页/异常的报告、重试后恢复地图。注入日志与正常启动日志分开保存；这两次人为崩溃不冒充用户闪退复现。Java 异常会在本机记录；原生崩溃可读取系统退出原因，仍需物理手机日志定位 GPU 等差异。

Android 17 系统照片选择器实际选择本地测试 JPEG，回到编辑器保存地点与记录，并打开详情和全屏查看器；通过系统 SAF 导出 ZIP，再选回同一 ZIP 确认恢复，显示“恢复完成”且作品数量一致。测试图为开发机生成的纯色 JPEG，未内置到 APK。查看器系统栏尺寸和深色首页系统栏背景也已针对该系统修正。截图仅展示模拟器的运行结果。

技术依据：[Android 16 KB 支持](https://developer.android.com/guide/practices/page-sizes)、[Android 17 全应用行为变化](https://developer.android.com/about/versions/17/behavior-changes-all)、[MapLibre 11.5.0 原生页大小支持](https://github.com/maplibre/maplibre-native/releases/tag/android-v11.5.0)。

## 未完成的最终验收

未取得中国国界/行政区/地名规范性审核通过证明，未生成正式 Release；内置底图只有 Z0–10，缺少全国更高层级细节。未进行用户骁龙手机和旧旗舰/中端真机的兼容与性能、真实高分辨率万张照片连续浏览及长时间低内存/低空间验证。模拟器和桌面测试不能替代这些项目。
