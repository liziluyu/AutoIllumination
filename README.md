# Auto Illumination

Minecraft Forge 1.20.1 模组，包含可复用的客户端光柱渲染器和游戏内预览命令。目前尚未接入车辆前灯。

## 光柱预览

进入世界后执行客户端命令：

```text
/autoillumination beam preview
/autoillumination beam preview 24 8 ffffff
/autoillumination beam preview 16 15 ff8800
/autoillumination beam clear
```

`preview` 的可选参数依次为长度（2–128，默认 16）、控制强度（0–15，默认 8）和六位十六进制 RGB 颜色（不带 `#`，默认 `ffffff`）。强度越大，光柱越集中、越不透明；0 不显示光柱。命令将光柱固定在执行时玩家眼前一格的位置，沿当时的视线方向发射，之后可以走动观察。再次执行会替换预览，`clear` 清除；退出世界或切换维度也会清除。预览仅本地可见，不修改世界或向服务器发送光源数据。

光柱使用四面渐扩几何体、末端渐隐和方块碰撞射线截断。它自身呈现发光颜色，但不会提高周围方块的亮度。截断以中心射线为准，宽光柱与侧面障碍物之间不做体积碰撞计算。

## 渲染接口

实现位于 `com.liziluyu.autoillumination.client.beam`，仅供客户端调用：

- `BeamStyle`：长度、发射口半宽、扩散率、起点/渐隐前透明度、渐隐长度和 RGB 颜色；`spotlight(range, signal, color)` 提供原聚光灯的强度映射。
- `BeamRenderer.render(...)`：沿当前 `PoseStack` 的局部 +Z 方向绘制，长度从发射口起算。
- `BeamRenderer.renderWorld(...)`：从世界坐标发射，支持任意方向和相机相对变换。
- `BeamRenderer.clipLength(...)`：在 `ClientLevel` 中计算碰撞截断后的长度，遇到未加载区块边界保守截断，不请求加载地形。
- `BeamRenderer.isReady()` 和 `renderType()`：检查着色器就绪状态、提交光柱批次。

渲染器基于 Create Shining Stage 的聚光灯视觉方案，为 Forge 1.20.1 改写。它使用 Forge 的 `RegisterShadersEvent`、原生 `ShaderInstance` 和本模组的 GLSL 着色器，无需安装或内嵌 Veil，也不依赖 Create、Sable。透明度和渐隐数据放在浮点 UV 中，保留低强度下的精度；绘制时启用深度检测、关闭背面剔除和深度写入。

预览在 `RenderLevelStageEvent.AFTER_PARTICLES` 中使用独立缓冲区绘制，渲染类型选择粒子输出目标以配合 Fabulous 模式。复用时应在同一阶段提交批次，避免与其他渲染阶段的输出目标混用。资源重载会重新注册着色器。光影包兼容性仍需实际游戏验证。

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
