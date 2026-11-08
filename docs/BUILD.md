# 构建说明

本项目是 Architectury 多模块工程（`common` / `fabric` / `neoforge`），目标 Minecraft 1.21.1。
本文记录**非标准配置**：这些配置看起来可疑，但删掉就会构建失败。

---

## 1. 一键构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:_JAVA_OPTIONS = "-Xmx1500m"
cd F:\AICoreLib
.\gradlew.bat clean build --no-daemon --console=plain
```

本机 `JAVA_HOME` 默认指向 jdk-17，**每次调用都必须显式覆盖成 jdk-21**，否则 `options.release = 21` 直接报错。

只编译不打包：`.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava`

---

## 2. 产物

| 文件 | 用途 |
|---|---|
| `fabric/build/libs/aicorelib-fabric-0.1.0.jar` | Fabric 端成品，丢进 `mods/` |
| `neoforge/build/libs/aicorelib-neoforge-0.1.0.jar` | NeoForge 端成品，丢进 `mods/` |
| `*-sources.jar` | 源码包 |
| `*-dev-shadow.jar` | 开发态未 remap 包，**不要发布** |

两个成品 jar 都是 18 KB 左右，内部结构相同：`com/aicorelib/**`（common 合并进来）+ 各自平台的 mod 元数据 + `assets/aicorelib/**` + `data/aicorelib/**`。

---

## 3. 工具链版本

| 组件 | 版本 | 备注 |
|---|---|---|
| Java | 21 | 编译与运行目标 |
| Gradle | 8.8 | 由 wrapper 固定，`gradle-wrapper.properties` 指向官方 distributionUrl |
| architectury-plugin | 3.4-SNAPSHOT | 解析到 3.4.164 |
| Architectury Loom | **1.7-SNAPSHOT** | 解析到 1.7.435，见 §5.3 |
| Architectury API | 13.0.11 | `architectury-fabric` / `architectury-neoforge` |
| Minecraft | 1.21.1 | |
| 映射 | `loom.officialMojangMappings()` | **Mojang 官方映射，不是 Yarn** |
| Fabric Loader / API | 0.16.14 / 0.116.17+1.21.1 | |
| NeoForge | 21.1.265 | |

`GRADLE_USER_HOME` 在本机是 `F:\.gradlecache`，不是 `~/.gradle`。

---

## 4. 目录约定

```
AICoreLib/
├─ settings.gradle          # 仓库 + include + loom 平台钩子（§5.1）
├─ build.gradle             # 根：插件版本、子工程统一配置
├─ gradle.properties        # 版本号与网络超时
├─ common/                  # 全部业务代码，不 import 平台 API
│  └─ src/main/java/com/aicorelib/
│     ├─ AICoreLib.java     # MOD_ID、id() 工具
│     ├─ agent/             # AgentRecord / AgentSavedData / AgentService
│     ├─ core/              # ModItems / ModCommands
│     └─ item/              # AiInjectorItem / AiCoreItem
├─ fabric/                  # ModInitializer 入口 + fabric.mod.json
└─ neoforge/                # @Mod 入口 + neoforge.mods.toml
```

架构纪律：`common` 里**不允许**出现 `net.fabricmc.*` / `net.neoforged.*` import；平台差异走 Architectury 或 `@ExpectPlatform`。

---

## 5. 五处非标准配置（不要"顺手清理"）

### 5.1 `settings.gradle` 里的 loom 平台钩子

```groovy
gradle.projectsLoaded {
    gradle.rootProject.allprojects { p ->
        if (p.name == "neoforge") { p.ext.set("loom.platform", "neoforge") }
    }
}
```

**为什么必须在这里、用这种方式：**

- Architectury Loom 只在 `extension.isForgeLike()` 为真时才注册 `neoForge` 依赖配置。默认平台是 `FABRIC`，此时 `neoForge "net.neoforged:neoforge:..."` 报 `Could not find method neoForge()`。
- 平台在 **loom 插件 apply 的那一刻**就已解析完成，所以在 `neoforge/build.gradle` 里写 `project.ext.set("loom.platform", ...)` 已经太晚。
- 也不能用全局 `-Ploom.platform=neoforge`：会污染 `:fabric`，且让根工程的 `mappings loom.officialMojangMappings()` 报 `No signature of method: java.lang.String.officialMojangMappings()`（因为 `mappings` 变成了字符串属性访问）。
- `gradle.projectsLoaded` 在子工程 apply loom 之前触发，是按工程精确设置的唯一窗口。

验证：构建日志里出现 `AFTER platform=NEOFORGE`，且 `:neoforge` 的 configurations 包含 `neoForge`。

### 5.2 仓库列表里的两个镜像

```groovy
maven { url "https://bmclapi2.bangbang93.com/maven" }   // 代理 neoforge 元数据
maven { url "https://maven.cleanroommc.com/" }          // 只有这里有 cpw.mods:modlauncher
```

本机 `maven.neoforged.net` **被按 SNI 阻断**（TCP 通，TLS 握手即 reset，DNS 还被污染），所以：
- `maven.neoforged.net` 已从仓库列表移除；
- NeoForge 的 pom / universal jar 走 BMCLAPI 镜像（会 302 跳 cernet → ustc）；
- 但 `cpw.mods:modlauncher:11.0.5`（`net.neoforged:coremods` 的依赖）只有 cleanroommc 有，BMCLAPI、aliyun、腾讯 nexus、forge maven 全 404。

### 5.3 Loom 必须是 1.7-SNAPSHOT

Loom 1.6.422 的 `MinecraftPatchedProvider` 把 binarypatcher 主类**硬编码**成 `net.minecraftforge.binarypatcher.ConsoleTool`，而 NeoForge 21.1 附带的 `binarypatcher-2.1.2-fatjar.jar` 里是 `net.neoforged.binarypatcher.ConsoleTool`，于是补丁阶段直接 `ClassNotFoundException`。
Loom 1.7.435 改成从 jar 里自动探测主类（`getMainClass(Iterable)`），才兼容 NeoForge 21.1.x。

注意：1.6 和 1.7 都会打印 "You are using an outdated version of Architectury Loom!"，这是插件对自身版本的固定警告，**与本项目无关**。

### 5.4 内存限制 `_JAVA_OPTIONS=-Xmx1500m`

本机系统提交内存常年在 44/47 GB 附近，Gradle 子 JVM 申请 ~2 GB G1 虚拟空间会直接崩：

```
Native memory allocation (mmap) failed to map 2013265920 bytes for G1 virtual space
```

加 `_JAVA_OPTIONS` 限制即可。它不是项目缺陷，但如果换到内存充裕的机器可以去掉。

### 5.5 归档名

```groovy
base { archivesName = "${rootProject.mod_id}-${project.name}" }
```

否则产物叫 `fabric-0.1.0.jar` / `neoforge-0.1.0.jar`，在 `mods/` 目录里无法分辨。

---

## 6. 常见报错速查

| 报错 | 原因 | 处理 |
|---|---|---|
| `Could not find method neoForge()` | loom 没进入 NeoForge 平台 | 检查 `settings.gradle` 的 `projectsLoaded` 钩子（§5.1） |
| `Could not find cpw.mods:modlauncher:11.0.5` | 只存在于被阻断的 neoforged maven | 确认 `maven.cleanroommc.com` 在仓库列表里（§5.2） |
| `Connection was reset` / `Recv failure` 指向 `maven.neoforged.net` | SNI 阻断 | 该仓库必须保持移除状态 |
| `... is using property loom.forge to enable forge mode` | 平台用旧方式设置 | 用 `loom.platform`，不要用 `loom.forge` |
| `No signature of method: java.lang.String.officialMojangMappings()` | 全局 `-Ploom.platform` 污染了根工程 | 改用 per-project 设置（§5.1） |
| `Native memory allocation (mmap) failed` | 系统内存不足 | `$env:_JAVA_OPTIONS="-Xmx1500m"`（§5.4） |
| `Unexpected character: '?' @ line 1` | 文件被写成 UTF-8 BOM | Groovy 文件不要带 BOM（PowerShell 用 `-Encoding ascii`） |
| `程序包net.neoforged.fml.common不存在` | `:neoforge` 只拿到 universal jar | 平台没生效，回到 §5.1 |

---

## 7. 将来加 Forge 端

1. 新建 `forge/`，`settings.gradle` 里 `include "forge"`；
2. 平台钩子里加 `if (p.name in ["neoforge", "forge"]) p.ext.set("loom.platform", p.name)`；
3. `forge/build.gradle` 复制 `neoforge` 那份，`architectury { forge() }`，依赖换成 `net.minecraftforge:forge:1.21.1-52.x`；
4. `gradle.properties` 的 `platforms` 加 `forge`。

前提是 Forge 1.21.1 与 Architectury 的兼容性届时重新验证过（见 `DESIGN.md` §1）。
