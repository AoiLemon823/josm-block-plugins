# JOSM 住宅、底商与工业厂房排布插件

独立插件类为 `org.openstreetmap.josm.plugins.blocklayout`，不修改或替换 BlockFill 0.5.0。构建使用 JOSM 19555、隔离 JDK 21 和 `--release 17`；所有中间文件只在 `build-layout\`，安装包输出为 `dist\josm-block-layout.jar`。

## 构建与测试

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-layout.ps1
```

测试覆盖住宅布局、工业可变尺度厂房、同尺寸联排、随机高度、工业排间 service 通道净距、底商交叠面积去重、生成节点复用、JOSM `SequenceCommand` 一次撤销和 UI 启动。

## 使用

设置面板分为“住宅 / 底商 / 工业”三个页签，方向拾取和开始预览位于页签下方。选择闭合地块 way 后，选择相应页签配置参数并预览；满意后点击地图确认，`Ctrl+Z` 一次撤销。

住宅支持矩形或样板轮廓排布、地块边方向拾取及多层/中高层/高层独立预设。每个住宅预设修改后单独保存，自定义参数与预设互不覆盖。

底商支持沿街退距、进深、高度、端部倒角和到底商道路退距；会扣除已有 building 面和 `highway=service` 占用缓冲。重叠候选带做几何 union，输出 `type=multipolygon + building=retail`。

工业支持长短边分别随机的矩形厂房、独立层高/层数范围、同尺寸细长联排、默认或拾取方向、固定随机种子及已有建筑避让。可选“同时生成工业 service 通道”，默认关闭；按相邻厂房排生成直线道路并校验宽度、建筑退距和地块边界。通道与厂房作为一个操作预览和一次撤销。

工业通道目前不生成环路，不自动连接外围公共道路，也不校核消防转弯半径或道路规范。工业厂房尺寸、高度、间距均为概化布局值，不是工程合规证明。具体火灾危险性类别、生产工艺、起重设备、耐火等级、消防车道和地方规划条件需另行核验。

已生成几何中坐标完全相同的新节点会复用同一个 JOSM Node；不会吸附近似坐标或擅自合并已有地图节点。最多500栋/组件。

道路生成历史说明：住宅排间道路和两出口网格寻路曾因高负荷卡顿而移出当前版本。旧源码与上一版插件包存放在 `docs\road-retired-2026-11-05\`，当前仅保留工业厂房排间的轻量直线通道。

## 安装更新

首次试用推荐使用隔离 JOSM profile。更新主 profile 时先退出所有 JOSM 窗口，将现有 `%APPDATA%\JOSM\plugins\josm-block-layout.jar` 备份到插件目录外，再复制 `dist\josm-block-layout.jar` 替换并重启。若异常，关闭 JOSM 后恢复备份文件。整个过程不要更改 `josm-blockfill.jar`。
