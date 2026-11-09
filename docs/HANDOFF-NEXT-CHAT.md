# Chatbox 交接归档：JOSM 街区工具与住宅楼排布

更新日期：2026-11-03

## 当前范围

**本归档的下一阶段只构建一个独立的 JOSM 住宅楼排布插件。** 不在此阶段继续扩展“街区填充”插件。

- 用户要求：现有街区填充先停止改动，下个新对话专门构建新插件。
- 项目目录：`C:\Users\AoiLemon\Desktop\Workplace\josm-blockfill`
- JOSM：`C:\Users\AoiLemon\AppData\Local\JOSM`，运行版 19555，内置 Java 21。
- 插件用户目录：`C:\Users\AoiLemon\AppData\Roaming\JOSM\plugins`
- 用户当前的街区填充安装版仍是 **0.5.0**，因为最新命名词库压力测试未完全通过，未覆盖安装包。
- 构建命令：项目目录内执行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`。

## 旧插件状态及风险

工作树最近被一次中断的 0.6.0 构建工作污染，目前未完成，**不要直接安装 `dist\josm-blockfill.jar`，不要使用当前 `build.ps1` 作为发布流程**。

已经完成但仅在源码中的改动：

- 词库经 `tools\repair-vocabulary.mjs` 去重和短词条清理；资源 `resources\name-vocabulary.tsv` 当前 32 个唯一词池。
- 生成器增加必需词池、重复词池、空词、过长 token 和句子式标点校验，以及禁用古文/危险片段过滤。
- `PlaceNameGeneratorTest` 通过。
- 但 `NameQualityTest` 中刚需档与改善/豪宅档的压力测试仍失败，所以该命名修复没有完成验证，也未安装。
- 新增的 4500 样本压力测试可能将合法但相似的名称/分档范围判得过严，先复现并改正测试策略，不可简单删掉质量约束。
- 已从源码移除楼栋排布的 Java 实验类，但 `build.ps1` 和 `UiStartupTest.java` 还残留 `BlockLayoutGeometryTest` / `BlockLayout*` 引用，导致旧插件构建失败。
- `build.ps1` 在打包新 JAR 前会 `Remove-Item build\classes` 和 `build\test-classes`。**绝对不要在修复这些测试引用前运行 build.ps1**，否则会清除最近一个可运行的构建类文件。
- JAR Manifest 仍可能标记 0.5.0，确保新任务不修改或替换此 JAR/安装版。
- 曾误在测试或代码中生成测试文件；先检查 `git status`（本机没有 git 命令，若不可用用文件时间戳/目录清单审查）。

如果要之后恢复旧插件，必须先备份或重新构建可用的 0.5.0；清理构建脚本和 UI 测试中的残留排布引用；分档测试通过后才更新版本并安装。

## 新插件需求

目标：让用户在 JOSM 内依据住宅小区地块，快速规则布置公寓建筑轮廓。

### 输入和布局参数

- 建筑短边宽度、长边长度、高度，单位米。
- 建筑端部退距。
- 同排楼栋之间的侧向净距。
- 最小排间距。
- 可选“日照距离系数”；原型计算为 `有效排距 = max(用户最小排距, 建筑高度 × 日照系数)`。
- 清楚标注它是概化布局参数，不是当地日照规范合规证明。纬度、朝向、地形、当地规范尚未纳入。
- 生成行列式布局，隔行错列作为可选开关。

### 地块输入与约束

- 优先支持用户选择一个或多个闭合 `way` 作为小区范围。
- 选区为空时，可考虑在当前视口内查找 `landuse=residential` 闭合面，或提示用户先选择；优先级低于手动选择。
- 支持避让已有 `building=*` 闭合面。
- 建筑矩形必须完全在对应用地边界内部；越界不生成，不裁切。非矩形/凹多边形应检测完整面积包含关系。
- 多个选中的地块不得把所有建筑跨地块分配；每栋必须完整落在某一单独地块里。
- 生成 N 条 `building=apartments`（或用户可选择的 `building=residential` / `building=yes`）闭合 way，节点是合法 LatLon；可加 `height`、`building:levels`，所有几何/属性写入 JOSM 一个 `SequenceCommand`，`Ctrl+Z` 一次撤销。
- 默认只预览，不修改现有地物；左键确认后创建。加入上限（如 500 或 1000 栋）和没有合适位置的提示。
- 随机角度旋转/多种楼型可后续做，不是第一版必需。

### 工作流/UI

- 独立插件或独立 MapMode/侧栏，不破坏 BlockFill 原功能。
- 选边界 → 填参数 → 点“开始排布预览” → 鼠标移动时显示矩形轮廓 → 左键一次生成 → 状态提示栋数 → Ctrl+Z 撤销 → Esc 退出。
- 参数包含比例/距离单位清晰，尺寸和间距输入要验证。
- 模式和命名使用分开的控件/组件，避免与旧插件状态共享。
- 安装测试时保留现有 JAR 备份；如果旧 JOSM 正在运行，不覆盖正在加载的 JAR，优先使用隔离测试 profile 或关闭所有 JOSM.exe 后安装。

### 工程环境

- JOSM jar：`C:\Users\AoiLemon\AppData\Local\JOSM\app\josm-custom.jar`。
- 本地项目带 Azul JDK 21：`tools\zulu21.54.109-ca-jdk21.0.13.0.3-win_x64`。
- Java 编译字节码用 `--release 17`。
- 先不要直接接入旧 `build.ps1`，创建独立 `build-layout.ps1`，使用独立的 `build-layout\classes`、`build-layout\test-classes` 和 `dist\josm-block-layout.jar`。不要删除或复用旧 `build\classes`。
- JOSM GUI 构造测试可参考 `test\...\UiStartupTest.java` 初始化 Preferences、content pane、toolbar 和 menu；HEADLESS UI 中不要真的让 JOSM 写用户 preferences。

## 独立几何测试最低要求

- 简单矩形地块：产生正确数量、尺寸、间距的行列楼栋。
- 日照开/关、不同高度和日照系数，计算有效排距符合公式。
- 凹形地块：所有生成矩形完整在地块内；越界栋拒绝，其他合规栋保留。
- 狭窄/过小地块：友好报告无法放置，不死循环。
- 与既有建筑相交：避让或拒绝该栋。
- 两个分离地块：楼栋不能跨越地块之间的空白区域。
- 超大楼数：触发明确上限。
- 坏尺寸、NaN/infinity、负间距、极端太阳系数：安全拒绝。
- 隔行错列开关生成可预期偏移。
- JOSM `DataSet` 集成测试：通过 `UndoRedoHandler` 单条序列命令创建，撤销后新 nodes/ways 全部移除。
- UI 测试：插件面板、MapMode 可以初始化，快捷键和鼠标预览挂载/退出时正确注册/解除。

## 新对话开始提示词

复制下面这段到新对话，或提示助手先读取本文件：

> 请先读取 `C:\Users\AoiLemon\Desktop\Workplace\josm-blockfill\docs\HANDOFF-NEXT-CHAT.md`，遵守“当前范围”，只构建独立的住宅楼排布插件，先不要改名命名相关代码，也不要覆盖现有 BlockFill 0.5.0。当前旧工作树处于不完整状态：不要运行旧 `build.ps1`，它会删除 `build\classes` 并且仍引用已移除的 BlockLayout 类。请新建独立构建脚本/目录，用现有 JOSM 19555 jar 和隔离 JDK 21（字节码 release 17），从几何、UI 到 JOSM undo command 全程测试，然后提供独立安装包和安装/回滚说明。
