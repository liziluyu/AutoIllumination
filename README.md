# Auto Illumination

Minecraft Forge 1.20.1 模组开发项目。目前仅包含开发环境和模组入口，尚未实现照明功能。

## 开发环境

- Minecraft：1.20.1
- Forge：47.4.28（官方推荐版）
- Java：JDK 17（64 位）
- Gradle：8.8，使用仓库自带的 Wrapper
- ForgeGradle：6.0.54
- 模组 ID：`autoillumination`
- Java 包：`com.liziluyu.autoillumination`

在 IntelliJ IDEA 中作为 Gradle 项目打开，将 Gradle JVM 设为 JDK 17。项目使用 Java 17 toolchain，并通过 Foojay resolver 在缺少 JDK 时自动下载；首次导入需要联网。

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

## 仓库

上游仓库：[liziluyu/AutoIllumination](https://github.com/liziluyu/AutoIllumination)，开发分支：`1.20.1forge`。本地分支跟踪 `origin/1.20.1forge`。

开发环境基于 [Forge 官方 MDK](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) 初始化。开发说明见 [Forge 文档](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)。

## 许可证

[MIT](LICENSE)
