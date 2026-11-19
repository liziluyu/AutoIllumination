# Auto Illumination

Minecraft Forge 1.20.1 模组，包含车辆头灯、可复用的客户端光柱渲染器和游戏内预览命令。

## 车辆头灯

客户端按车架 ID 选择头灯光柱列表，无需执行命令。光柱从各自的灯位沿车头方向发射，跟随模型的悬挂位移、俯仰、侧倾、车架弹跳和发动机振动，不随驾驶员视线转动。其他玩家的车辆同样按下述规则开灯。

`client/HeadlightActivation.java` 按以下优先级决定车辆是否开灯，光柱和端点虚拟光源共用判定：

1. 车上没有活着的 `LivingEntity` 乘客则关灯；玩家、其他生物以及经座位实体间接乘坐的生物均计入。
2. 有生物乘坐且客户端同步的天内时间满足 `13000 <= dayTime % 24000 < 23000` 则开灯，无需再判断亮度；固定时间的维度不套用昼夜周期，继续检查环境亮度。
3. 否则仅当车辆实体原点所在方块的环境亮度 **小于 5** 时开灯；亮度等于 5 时关灯。

环境亮度取随时间/天气衰减的天空光照与服务器下发的方块光照两者的最大值。天空衰减根据当前维度太阳角度、降雨和雷暴实时计算，不使用客户端未随时间刷新的 `isNight()` / 无参数 `getSkyDarken()` 缓存。保留服务器方块光照的独立副本，避免头灯或预览的虚拟光源照亮车辆后造成自动开关抖动；收到光照更新时刷新，区块和世界卸载时清理。此规则仅作用于车辆头灯，命令预览仍由命令控制。每个客户端游戏 tick 检查开关状态，开灯或关灯时当次更新对应车辆的虚拟光源，不等待下一次 5 tick 位置采样；光照传播和区块重绘仍由客户端引擎执行。

姿态按 Automobility 0.4.2 的实体渲染器与车架渲染器顺序计算：在插值实体位置上应用 `Displacement` 的竖直位移和 X/Z 旋转、车身朝向、轮胎半径偏移，以及车架悬挂和振动位移。RIAutomobility 1.0.4b 的整车渲染也使用这条变换链，无需新增运行依赖。绘制保留完整旋转，碰撞射线使用同一变换后的发射点与方向。这里跟随车架整体姿态，不读取特定模型骨骼或车轮、附件的独立动画。

灯位在 `client/FrameLightProfiles.java` 中维护，按完整 `namespace:path` 精确匹配，未匹配的 ID 不绘制。已列出 Automobility 0.4.2 的 32 个车架和 RIAutomobility 1.0.4b 的 13 个车架；每个 ID 有独立的不可变光柱列表，默认均为空。当前仅显式为五种基础汽车（`wooden_motorcar`、`copper_motorcar`、`steel_motorcar`、`golden_motorcar`、`bejeweled_motorcar`，命名空间均为 `automobility`）配置双灯：宽高均为 3，向前 21.8，左右各 4.5，额外向上偏移为 6.5；左右灯分别向外转 10°，均向下转 5°。其余车架保持 `List.of()`，不绘制光柱。

每项格式为 `new Beam(width16, height16, offsetX16, offsetY16, offsetZ16, yawDegrees, pitchDegrees, rollDegrees)`。前五项尺寸和偏移以 **1/16 格** 为单位，支持小数；后三项旋转以**度**为单位。宽和高是发射口的完整尺寸，而非半径；偏移相对于车架模型原点，正 X 向车身右侧，正 Y 向上，正 Z 向前。偏移随车架整体姿态旋转。车架原点已经按所装轮胎的**半径**抬升，不需要在 `offsetY16` 中重复添加轮高；水平且无弹跳/振动时，灯位高度为 `实体原点Y + 轮胎半径/16 + offsetY16/16`。五种基础汽车的配置示例：

```java
profiles.put(id("automobility", "wooden_motorcar"), List.of(
    new Beam(3f, 3f, -4.5f, 6.5f, 21.8f, -10f, 5f, 0f),
    new Beam(3f, 3f, 4.5f, 6.5f, 21.8f, 10f, 5f, 0f)));
```

旋转围绕每盏灯自身的发射口进行，依次沿灯的局部轴应用偏航、俯仰和横滚，并继续跟随整车姿态。正 `yawDegrees` 向右转，负值向左转；正 `pitchDegrees` 向下转；正 `rollDegrees` 表示从灯后方沿光束方向看时顺时针旋转。旧的五参数构造器仍可用，等同于三轴旋转全为 0。每束光的绘制、碰撞截断和视锥裁剪使用同一套旋转后的位置与方向。

头灯外观由客户端 `config/autoillumination-client.toml` 管理，首次加载时由 Forge 生成；配置加载或重载时会更新缓存，渲染中不解析颜色字符串。这些参数统一作用于所有车架的头灯，预览命令仍使用自身参数。

```toml
[headlights]
length = 2.0
color = "FFFCC6"
openingAngleDegrees = 32.0
opacity = 0.15
```

- `length`：最大长度，单位格，范围 0.1–128；每束光会独立按方块碰撞截短。
- `color`：六位 RGB 十六进制字符串，不带 `#`。
- `openingAngleDegrees`：水平和垂直方向的完整张开角，单位度，范围 0–179；0 为平行光柱，每侧半尺寸按 `tan(角度 / 2)` 随距离增加。
- `opacity`：每个侧面的不透明度，范围 0–1；0 完全透明、1 完全不透明，沿长度保留末端渐隐。

头灯按上述乘客、夜晚和亮度规则自动开关。光柱受车身和地形的正常遮挡，末端渐隐范围为 3 格；实际光柱不足 3 格时从起点开始渐隐。沿用下述 Oculus 适配，并通过下述虚拟客户端光源照亮周围方块。

## 虚拟客户端光源

车辆头灯和命令预览光柱每 **5 个客户端游戏 tick** 重新计算一次起点与碰撞截断后的终点，在这两个坐标所在的方块格提供亮度 **15** 的虚拟光源。车辆开关变化时立即更新对应车辆的端点缓存，其他持续开灯车辆的位置仍维持 5 tick 更新节奏；创建、替换或清除命令预览也会在下一个客户端 tick 更新光源。使用与绘制相同的车架姿态和灯位旋转计算，不依赖相机视锥；被方块截断时，终点沿用光柱的 0.01 格内缩，位于碰撞面前方。

`client/ClientBeamLights.java` 按当前客户端世界管理车辆端点缓存、端点引用和光源区段；同一格的多个端点共用一个光源，不叠加亮度。车辆移动后下一次 5 tick 采样会撤去旧位置的光源；车辆移除、切换到无灯车架或光柱设为完全透明会在当次客户端 tick 清理，清除预览在下一个客户端 tick 清理。区块卸载立即移除其中的光源，退出世界或切换维度清空追踪。仅使用已加载且位于世界高度内的位置，不请求加载区块。

虚拟亮度与原有方块发光取最大值，由客户端原版方块光照引擎传播、衰减、遮挡并通知区块重绘；移除后重新计算原有光照。服务器光照数据应用后，若覆盖了当前或最近移除光源的影响范围，会额外排队重算这些格子及区段边界，避免只检查光源位置时无法修复远处的覆盖。仅为空气区段保留所需的方块光照存储，不改变任何方块或天空光照，不向服务器发送或保存光源数据。Oculus / Embeddium 可读取这份客户端方块光照；它是原版单通道光照，光柱的 RGB 颜色不会变成彩色环境光，光影包最终如何显示方块光照仍取决于其设置。

## 光柱预览

进入世界后执行客户端命令：

```text
/autoillumination beam preview
/autoillumination beam preview 24 8 ffffff
/autoillumination beam preview 16 15 ff8800
/autoillumination beam clear
```

`preview` 的可选参数依次为长度（2–128，默认 16）、控制强度（0–15，默认 8）和六位十六进制 RGB 颜色（不带 `#`，默认 `fffcc6`）。强度越大，光柱越集中、越不透明；0 不显示光柱。命令将光柱固定在执行时玩家眼前一格的位置，沿当时的视线方向发射，之后可以走动观察。再次执行会替换预览，`clear` 清除；退出世界或切换维度也会清除。预览仅本地可见，不修改世界或向服务器发送光源数据。

光柱使用四面渐扩几何体、末端渐隐和方块碰撞射线截断，自身呈现发光颜色，端点另外提供上述客户端方块光照。截断以中心射线为准，宽光柱与侧面障碍物之间不做体积碰撞计算。

## 渲染接口

实现位于 `com.liziluyu.autoillumination.client.beam`，仅供客户端调用：

- `BeamStyle`：长度、发射口半宽/半高、扩散率、起点/渐隐前透明度、渐隐长度和 RGB 颜色；保留方形截面的七参数构造器，`spotlight(range, signal, color)` 提供原聚光灯的强度映射。`boundingRadius(length)` 用于矩形截面的保守视锥裁剪。
- `BeamRenderer.render(...)`：沿当前 `PoseStack` 的局部 +Z 方向绘制，长度从发射口起算。
- `BeamRenderer.renderWorld(...)`：从世界坐标发射，支持任意方向和相机相对变换。
- `BeamRenderer.clipLength(...)`：在 `ClientLevel` 中计算碰撞截断后的长度，遇到未加载区块边界保守截断，不请求加载地形。
- `BeamRenderer.isReady()` 和 `renderType()`：检查着色器就绪状态、提交光柱批次。

渲染器基于 Create Shining Stage 的聚光灯视觉方案，为 Forge 1.20.1 改写。它使用 Forge 的 `RegisterShadersEvent`、原生 `ShaderInstance` 和本模组的 GLSL 着色器，无需安装或内嵌 Veil，也不依赖 Create、Sable。透明度和渐隐数据放在浮点 UV 中，保留低强度下的精度；绘制时启用深度检测、关闭背面剔除和深度写入。

预览在 `RenderLevelStageEvent.AFTER_PARTICLES` 中使用独立缓冲区绘制，渲染类型选择粒子输出目标以配合 Fabulous 模式。复用时应在同一阶段提交批次，避免与其他渲染阶段的输出目标混用。资源重载会重新注册着色器。

## Oculus 光影兼容

光柱包含针对 **Oculus 1.8.0 / Forge 1.20.1** 的可选客户端适配，参考 Veil 的 Iris 帧缓冲接入思路及 [Veil PR #213](https://github.com/FoundryMC/Veil/pull/213) 中关于缓冲恢复和颜色纹理翻转的修正。无需安装 Veil。

Oculus 会在光影开启时关闭未知原生着色器的颜色写入，单独注册 `ShaderInstance` 仍可能看不到光柱。适配只作用于本模组的光柱着色器：在世界渲染期间恢复 Oculus 保存的颜色/深度写入状态，并将光柱输出接入当前 `colortex0` 与场景深度纹理。根据透明阶段和翻转状态选择 main/alt 纹理，避免固定写入错误副本；绘制结束恢复此前的帧缓冲。阴影阶段不进行这项重定向。

关闭光影或未安装 Oculus 时使用原有渲染路径。Oculus 仅作为 `compileOnly` 编译依赖和本地开发运行依赖，不内嵌、不成为玩家的必需依赖。当前接入使用 Oculus 1.8.0 的内部 API，包括读取管线的 `renderTargets` 字段；未来版本若改变接口，需要重新核对。资源重载、窗口尺寸与光影管线变化时会重新读取目标纹理。

这是将光柱写入光影合成前的颜色缓冲；端点的环境照明通过客户端方块光照提供，并未向光影包注册独立的彩色动态光源或阴影。具体光影包的后期处理仍可能改变光柱的颜色和透明度，实际画面需要游戏内验证。

## 开发环境

- Minecraft：1.20.1
- Forge：47.4.28（官方推荐版）
- Java：JDK 17（64 位）
- Gradle：8.8，使用仓库自带的 Wrapper
- ForgeGradle：6.0.54
- 模组 ID：`autoillumination`
- Java 包：`com.liziluyu.autoillumination`

在 IntelliJ IDEA 中作为 Gradle 项目打开，将 Gradle JVM 设为 JDK 17。项目使用 Java 17 toolchain，并通过 Foojay resolver 在缺少 JDK 时自动下载；首次导入需要联网。

开发运行配置启用了 Mixin refmap 的 SRG → 官方映射转换，供 Automobility 等依赖在开发环境中解析注入目标。映射文件 `build/createSrgToMcp/output.srg` 由 ForgeGradle 的运行准备任务生成。更新运行设置后，已有 IntelliJ Application 配置需要重新执行 `genIntellijRuns` 生成；仅刷新 Gradle 不会改写已有配置。若日志出现 Automobility 的 `EntityMixin` 无法找到 `m_20272_`（对应官方名 `collide`），应先检查运行配置是否包含 `-Dmixin.env.remapRefMap=true` 和指向该映射文件的 `-Dmixin.env.refMapRemappingFile=...`。

在 PowerShell 中执行：

```powershell
# 构建模组，产物位于 build/libs/
.\gradlew.bat build

# 生成 IntelliJ IDEA 运行配置
.\gradlew.bat genIntellijRuns

# 启动开发客户端
.\gradlew.bat runClient

# 启动开发服务端，首次启动后按提示在 run/eula.txt 中接受 EULA
.\gradlew.bat runServer

# 运行数据生成器
.\gradlew.bat runData
```

运行目录和构建缓存已加入 `.gitignore`。模组信息和依赖版本在 `gradle.properties` 中维护，生成的资源放在 `src/generated/resources/`。

## 模组依赖

- [Automobility](https://modrinth.com/mod/automobility)：`0.4.2+1.20.1-forge`，加入编译和运行时，并在 `mods.toml` 中声明为必需依赖。
- [RIAutomobility](https://modrinth.com/mod/riautomobility)：`1.0.4b`（JAR 内版本为 `1.0.4`），仅加入开发运行时。
- [GeckoLib](https://modrinth.com/mod/geckolib/version/aC5KMoNg)：Forge 1.20.1 的 `4.8.4`，使用精确 Modrinth 版本 ID `aC5KMoNg` 固定，作为 RIAutomobility 所需的开发运行时依赖。同一个版本号对应多个加载器和 Minecraft 版本，仅使用 `4.8.4` 曾解析到 Fabric 1.21.4 文件，导致启动时找不到 `GeoModel`。

- [Oculus](https://modrinth.com/mod/oculus/version/iQ1SwGc3)：Forge 1.20.1 的 `1.8.0`，仅加入开发运行时，用于测试光影包。
- [Embeddium](https://modrinth.com/mod/embeddium/version/UTbfe5d1)：Forge 1.20.1 的 `0.3.31`，作为 Oculus 所需的开发运行时依赖。

依赖通过 Modrinth Maven 下载并由 ForgeGradle 反混淆。`localRuntime` 只扩展运行时类路径，RIAutomobility、GeckoLib、Oculus 和 Embeddium 不会被声明为本模组的强制依赖，也不会打包进本模组 JAR。玩家安装本模组时需要自行安装 Automobility。

开发客户端的光影包目录为 `run/shaderpacks/`。本地已放入 `BSL_v8.4p1.zip`，进入客户端后可在光影设置中选择。`run/` 已被 Git 忽略，光影包不随仓库提交或模组 JAR 分发。

## 仓库

上游仓库：[liziluyu/AutoIllumination](https://github.com/liziluyu/AutoIllumination)，开发分支：`1.20.1forge`。本地分支跟踪 `origin/1.20.1forge`。

开发环境基于 [Forge 官方 MDK](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) 初始化。开发说明见 [Forge 文档](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)。

## 许可证

[MIT](LICENSE)
