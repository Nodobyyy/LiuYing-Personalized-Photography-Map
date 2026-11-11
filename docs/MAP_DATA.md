# 地图数据、覆盖与许可（0.3.0）

| 资源 | 覆盖与层级 | 字节 |
| --- | --- | ---: |
| 离线 APK 街道概览 | 中国及周边，Z0–10 | 202,806,773 |
| 离线 APK 周边背景 | 全球，Z0–6 | 45,095,402 |
| 离线 APK 卫星概览 | 中国及周边，Z0–7 | 10,519,662 |
| 全国包街道 | 73°E–136°E、3°N–54°N，Z0–15 | 15,993,233,656 |
| 全国包精细卫星 | GHSL 2025 的 1,995 个城区，Z8–14 | 1,013,838,620 |

离线街道数据使用 Protomaps 2026-11-01 v4.15.2 和 OpenStreetMap。全国包保留所选范围内的 Z15 矢量数据，实际名称、景点和道路密度取决于 OSM。放大超过数据最高层级时沿用原瓦片。街道的全国覆盖独立于卫星城区索引。地图署名和再分发信息见随包的许可。 [Protomaps 底图](https://docs.protomaps.com/basemaps/downloads) · [OSM 版权](https://www.openstreetmap.org/copyright)

卫星图使用 EOX Sentinel-2 cloudless 2016，按 CC BY 4.0 署名。城区范围取自 GHSL 2025，最高级图像按标准瓦片网格拼接，低层级为重采样。精细覆盖外显示内置概览。 [EOX 地图](https://maps.eox.at/) · [GHSL 城区数据库](https://human-settlement.emergency.copernicus.eu/ghs_ucdb_2024.php)

联网版从 OpenFreeMap 请求街道矢量资源；卫星瓦片在中国境内请求高德卫星影像并在端侧实时重采样校正至 WGS84，中国境外及高缩放补充区域请求部署在国内 EdgeOne CDN 的必应全球卫星影像（`t0-t3.dynamic.tiles.ditu.live.com`，原生 WGS84），并以 Esri World Imagery 作为兜底（内置全球概览使用 EOX Sentinel-2 cloudless 2016）。样式、边界和字形随 APK 提供。应用对地图客户端限制 HTTPS 主机、路径和请求方法，并限制地图缓存为 256 MiB。照片、作品、EXIF 和备注保留在本地。 [OpenFreeMap](https://openfreemap.org/) · [Esri World Imagery](https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9) · [EOX WMTS 说明](https://tiles.maps.eox.at/wmts/1.0.0/WMTSCapabilities.xml)

## 版图和文字

34 个省级区域及 8 个境界线要素取自固定提交 `3cf3485789876c37be6193bf2848cf9aaa40f758` 的 WGS84 中国参考数据。地图保留南海相关要素。国家层级抑制台湾的独立标签，省级层级显示“台湾省”。周边保留地理背景，平移使用 MapLibre 的原生相机边界。许可和数据哈希见 `VIEW-SOURCES.txt` 与 `BOUNDARY-LICENSE.txt`。 [固定版图参考数据](https://github.com/JayMuShui/chinese-global-compliant-geodata/tree/3cf3485789876c37be6193bf2848cf9aaa40f758)

用户提供的参考图上印有 GS(2019)1818。应用交互底图的正式规范性审核仍待完成，包内 `boundaryReviewPassed` 为 `false`，正式 Release 构建保持禁用。

## 全国导入包

- 根目录包含 `bundle.json`、`style.json`、PMTiles、许可、字形和可选 `style-satellite.json`。
- 使用 WGS84，源 URL 写成 `pmtiles://local://file.pmtiles`，字形路径写成 `local://fonts/{fontstack}/{range}.pbf`。清单列出各文件的 SHA-256 和大小。
- 条目名为安全 ASCII 相对路径，少于 2,000 条。提取上限 32 GiB。导入先检查可用空间，再校验条目、文件大小和 SHA-256，校验通过后才启用。相同内容的再次导入会复用原地图。
- 可用 `tools/build-national-package.mjs` 生成暂存目录，再用 `scripts/zip-map-package.ps1` 创建以清单为首项的 ZIP64。后者使用 JDK 17 写入 STORED 条目的 CRC 和本地 ZIP64 头。
- 已把 ZIP 复制到手机时，请另留至少 18 GB 空间。导入后可删除原 ZIP。地图与作品备份独立管理。
