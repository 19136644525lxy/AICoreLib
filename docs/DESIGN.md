# AICoreLib 设计方案（v0.2）

> 目标版本：Minecraft 1.21.1 / Java 21
> 定位：库模组（Library Mod），提供「实体 AI 注入」的底层框架、内置预设与社区扩展接口
> 状态：方案定稿中，未写任何代码

---

## 0. 结论速览

| 项 | 结论 |
|---|---|
| 平台框架 | **Architectury 双端：Fabric + NeoForge**（路线 A，已定） |
| 核心机制 | 注入器物品 → 给实体挂 `Agent` → 双层大脑（本地规则 + 云端 LLM）驱动 |
| 行为接管 | 插入高优先级、带 MOVE/LOOK/TARGET 冲突标记的 `Goal` 压制原版决策目标；**不清空原版 goal** |
| 实体范围 | 第一版只做 `Mob`（见 §8），非 Mob 留 `AgentHost` 接口 |
| 数据落盘 | 服务端 `SavedData`（UUID → AgentState）+ 实体上的轻量标记 |
| 预设 | 数据包 JSON（Codec）+ Java API 双轨 |
| LLM | 单一「OpenAI 兼容」通用口，配 `base_url` 覆盖所有供应商 |
| API Key | 只存服务端配置里的环境变量名，物品/网络包零明文 |
| 客户端 | **不强制安装**：纯服务端可跑；装客户端才有 GUI/贴图/HUD |
| 核心掉落 | 实体死亡掉 `ai_core`，可迁移 AI 到新实体（带损耗，防刷） |

---

## 1. 平台决策：Architectury 双端（已定）

### 查证事实
| 项 | 数据 |
|---|---|
| Architectury API 1.21/1.21.1 最新版 | 13.0.11（Fabric + NeoForge） |
| `architectury-forge` 最后版本 | 10.1.20，对应 1.20.2 |
| Forge 1.21.1 本体 | 存在（52.x），但 Architectury 未适配 |

**决策：路线 A —— 1.21.1 只做 Fabric + NeoForge，不做 Forge。**

理由：
1. 三端里 Forge 与 NeoForge 在 1.21 已彻底分叉，同时维护两套 Forge 系适配的收益极低。
2. Forge 玩家可通过 Sinytra Connector 在 NeoForge 上跑 Fabric 版。
3. 保留后路：`common` 层只依赖 vanilla + 自研 Platform 接口（§14 架构纪律），将来若要做 Forge，补一个平台模块即可，业务代码不动。

**已放弃的路线**：路线 B（MultiLoader 四模块，自研全部平台抽象，工作量 +30%）；路线 C（1.21.1 + 1.20.1 双线并行，暂不启动，但架构上兼容）。

---

## 2. 产品定位与边界

### 是什么
- 一个**框架**：定义「实体如何被 AI 接管」的标准模型。
- 一个**运行时**：调度感知、大脑、动作、记忆、网络同步、存档。
- 一个**内容包**：内置注入器 + AI 核心 + 若干开箱即用的预设。
- 一个**扩展平台**：数据包 JSON + Java SPI。

### 不是什么（第一版明确不做）
- 不做通用 NPC 对话引擎（留 `say` 动作与人格字段）。
- 不做本地小模型推理（`LlmProvider` 接 Ollama/vLLM 即可）。
- 不接管非生物实体（船、矿车、掉落物）。
- 不做跨维度传送、实体克隆。

---

## 3. 总体架构

```
┌─────────────────────────── 客户端（可选安装） ──────────────────────┐
│  AgentPanelScreen / 预设轮盘 / 状态 HUD / 调试渲染(粒子路径)         │
└───────────────────────────────▲─────────────────────────────────────┘
                                │ 低频率状态同步（dirty flag, 1~4Hz）
┌───────────────────────────────┴─────────────────────────────────────┐
│                        服务端（权威）                                │
│                                                                     │
│  AgentManager ── 生命周期 / 上限 / 激活门控                          │
│      │                                                              │
│      ├─ AgentState（持久化：UUID, preset, owner, memory, 预算…）     │
│      │                                                              │
│      ├─ Brain（可插拔）                                             │
│      │    ├─ LocalBrain：Utility 打分 → Intent（每 2~10 tick）       │
│      │    └─ RemoteBrain：LLM 请求（异步）→ Intent（事件驱动）        │
│      │                                                              │
│      ├─ Perception（节流采样）                                       │
│      ├─ IntentQueue → Validator → Executor（主线程，逐 tick）        │
│      └─ Memory（环形缓冲 + 摘要）                                    │
│                                                                     │
│  PresetRegistry  ActionRegistry  LlmProviderRegistry  CoreRegistry   │
│  NetworkManager（Architectury）  Config  Command  EventBus           │
└─────────────────────────────────────────────────────────────────────┘
```

**单向数据流**：Perception → Brain → Intent → Validator → Executor → 世界变化 → 再感知。
**纪律**：Brain 永远不直接碰实体，Executor 永远不决策。这是所有扩展点能干净存在的前提。

---

## 4. 领域模型

```
AgentState
├─ uuid            实体 UUID（主键）
├─ presetId        ResourceLocation
├─ ownerUuid       注入者 / 认主对象
├─ brainType       LOCAL | REMOTE | HYBRID
├─ profileId       可选，LLM 配置别名（不存 key）
├─ memory          Memory（环形事件 + 摘要 + 关系值）
├─ budget          本小时 token 用量、上次决策 tick、失败计数
├─ lineage         迁移链：{originUuid, hops}，用于核心防刷
├─ flags           paused / muted / debug
└─ createdAt / dataVersion
```

**为什么状态放 `SavedData` 而不是实体 NBT / Attachment：**
1. Fabric 与 NeoForge 都叫 `AttachmentType` 但 API 不同，Architectury 不统一。
2. 实体卸载后 NBT 不 tick，但 agent 身份需要跨 chunk 保留。
3. `SavedData` 是 vanilla 的，双端都通，Codec 可控，能做 `dataVersion` 迁移。
4. 实体侧只留一个极轻的标记位，用于快速判定「是不是 agent」。

代价：实体加载时查一次 map，用 `Long2ObjectOpenHashMap` + 弱引用缓存解决。

---

## 5. 双层大脑

### LocalBrain（永远在线的底座）
Utility AI：每个 `UtilityGoal` 给出 `score(ctx) ∈ [0,1]`，取最高分执行；带冷却、打断、惯性（防抖动）。
- 决策频率：每 `decisionIntervalTicks`（默认 10 tick）。
- 开销目标：单 agent < 0.03 ms/tick。
- 离线可用、零延迟、可预测、可调试。**这是预设 AI 的主体。**

### RemoteBrain（可选的接管层）
- 触发：事件驱动（被攻击、目标出现、玩家下令、心跳 5~10s），不是每 tick。
- 异步：有界线程池 + 超时（默认 8s）+ `sequence` 号，过期响应丢弃。
- 输出：严格 JSON → schema 校验 → Intent。
- 失败：连续 N 次 → 熔断 60s → 降级 LocalBrain → 提示主人。

### HYBRID（默认）
LocalBrain 常驻跑保命/寻路/追击；RemoteBrain 只出高层意图。安全类 Intent（逃跑、灭火）永远压过 LLM 的 Intent。

---

## 6. 行为接管的技术实现（重点）

### 6.1 插入式 Goal，而非清空式
原版 `goalSelector` 里有 `FloatGoal`（防溺水）、`PanicGoal`、`MeleeAttackGoal`、`WaterAvoidingRandomStrollGoal`…**清空它们**会导致：不会游泳淹死、卡在河里、站着不动。

正确做法：插入优先级 0 的 `AgentGoal`，声明 `EnumSet.of(MOVE, LOOK, TARGET)`，**绝不声明 `JUMP`**（`FloatGoal` 靠它上浮）。

`GoalSelector` 规则是同 flag 互斥、高优先占：
- `canUse()` true → 原版决策目标自动停摆，完全接管；
- false → 原版行为立刻恢复，不会傻站。

零侵入、可逆。`OVERRIDE`（清空重写）只作为预设里的高级开关。

### 6.2 执行层
| Intent | 实现 |
|---|---|
| `MOVE_TO(pos, speed)` | `getNavigation().moveTo(...)`，带超时与卡住检测 |
| `LOOK_AT(target)` | `getLookControl().setLookAt(...)` |
| `ATTACK(target)` | 设 target + 靠近 + `doHurtTarget()`，受攻击冷却 |
| `USE_ITEM(slot)` | `useItem` / 手动 ItemStack 逻辑 |
| `SAY(text)` | 服务端广播（附近玩家 / 聊天栏 / 头顶，可配） |
| `WAIT(ticks)` | 空转但保留感知 |
| `FLEE(from)` | 反向寻路 + 加速 |
| `MINE / PLACE` | 默认禁用，需预设显式开启 + 权限校验 |

每个动作都有：前置校验（`canRun`）、冷却、失败原因回传（进 Memory）。

### 6.3 卡住检测
连续 N tick 位移 < ε 且 Intent 是 MOVE → 判定卡住 → 重选路径 / 跳过 / 写 Memory。这是让 AI「不显蠢」的关键工程细节。

---

## 7. 注入器（AI 注入器）

### 7.1 物品与图标
- 注册 `aicorelib:ai_injector`，`stacksTo(1)`。
- **贴图沿用原版调试棒**：模型 `parent: minecraft:item/generated`，`layer0: minecraft:item/debug_stick`，零新美术资源。
- 图标语义强化：`enchantment_glint_override = true`（附魔光泽但无附魔条目）+ `rarity = RARE`（物品名青色 `#55FFFF`），远看一眼就知道是特殊工具。
- 物品标签 `#aicorelib:ai_injectors`，供配方 / 战利品表 / 其他模组引用。
- 组件 `aicorelib:injector_config`：`{preset, profile, mode}`，只存 ID。

### 7.2 客户端不强制安装
物品是自定义注册的，原版客户端会显示「缺失模型」（紫黑块），但**功能完全正常**（交互由服务端判定）。

- 默认：自定义物品，客户端装 mod 才有正确贴图与 GUI。
- 可选：配置 `compat.legacy_stick_model = true` 时，注入器改为 `minecraft:stick` + `custom_data` 标记，原版客户端看到木棍，观感正常但失去贴图/工具提示/GUI。
- 二选一，不并存，避免「两个物品身份」造成的配方混乱。

### 7.3 交互
| 操作 | 行为 |
|---|---|
| 右键实体（未注入） | 用当前预设注入，粒子 + 音效 + 短暂发光 |
| 右键实体（已注入） | 打开 Agent 面板 / 聊天栏输出状态 |
| Shift + 右键实体 | 解除注入（二次确认） |
| 右键空气 | 预设轮盘 |
| 潜行 + 滚轮 | 快速切换预设 |
| 空手 Shift+右键已注入实体 | 调试视图（Intent / 分数 / 记忆） |

### 7.4 注入校验（防滥用）
`Mob` 判定 → 黑名单标签 `#aicorelib:not_injectable` → 权限（默认 OP 2）→ 每玩家上限（8）→ 每区块上限（4）→ 全局上限（64/维度）→ 是否已被注入。

### 7.5 AI 核心（掉落与迁移）
- 实体死亡掉落 `aicorelib:ai_core`，携带 `aicorelib:agent_snapshot` 组件：预设 ID、记忆摘要（≤512 字符）、好感度、创建者、`lineage`。**绝不携带 key 或 profile 凭据**。
- 掉落条件可配，默认「被玩家或玩家拥有的 agent 杀死」才掉，避免自然死亡刷核心。
- 迁移：用核心右键新实体 → 继承预设与部分记忆，重置预算。
- 防刷与损耗：同一 `lineage` 最多迁移 3 次；每次迁移记忆保留 60%、好感度 -20%；超过上限核心变为「损坏的核心」（只能提取少量经验）。
- 核心不可堆叠，带 `rarity = UNCOMMON`，模型可后续单独设计。

---

## 8. 实体范围（第一版只做 Mob）

「实体范围」= 哪些实体允许被注入 AI 并被接管行为。分四档：

| 档 | 范围 | 能做什么 | 成本 | 第一版 |
|---|---|---|---|---|
| A 完整 | `Mob`（PathfinderMob 子类） | 寻路、攻击、用物品、看、说、逃跑 | 低（原版已具备） | ✅ 做 |
| B 受限 | 非 Mob 的 `LivingEntity`（盔甲架等） | 看、说、用物品、原地攻击；不能寻路 | 中（移动要自己写） | ❌ 留接口 |
| C 傀儡 | 非生物 `Entity`（船、矿车、TNT） | 只能被推着走/瞬移/播动画 | 高、易出 bug | ❌ 不做 |
| D 禁止 | `Player`、Boss（末影龙/凋灵） | — | — | 🚫 硬禁（Boss 可配） |

默认判定顺序：
1. `entity instanceof Mob` → 通过；
2. 否则查 `#aicorelib:injectable` 标签（给 B/C 档留的扩展口）；
3. 命中 `#aicorelib:not_injectable` → 拒绝（默认含 `minecraft:ender_dragon`、`minecraft:wither`）；
4. `Player` 永远拒绝。

理由：A 档覆盖 99% 的玩法需求（僵尸、骷髅、村民、铁傀儡、狼、马、猪灵…），且原版已经提供 goal/寻路/攻击全套能力。B/C 档投入产出比很差，且非生物实体没有 AI 概念，接管只能靠 `setDeltaMovement` 硬推，极易出 bug。

**A 档包含全部 `Mob`，不只是敌对生物**：

| 类别 | 例子 |
|---|---|
| 敌对 | 僵尸、骷髅、苦力怕、蜘蛛、女巫 |
| 中立 | 末影人、猪灵、蜜蜂、北极熊 |
| 友好 | 牛、猪、羊、鸡、村民、狼、猫、马 |
| 水生 | 鱿鱼、海豚、鳕鱼、守卫者 |
| 飞行 | 蝙蝠、鹦鹉、幻翼、恶魂 |

**友好生物同样可以注入 AI，且是有意为之**：让「给自家牛装上 AI」成为合法玩法，是库的能力，不该由内容模组来限制。敌对与友好的差别只体现在预设参数（初始恐惧、是否主动攻击），不体现在准入。

API 侧用 `AgentHostFactory` 抽象（`getNavigation / getLook / attack / useItem / setTarget`），默认只注册 `Mob` 实现；社区可自行补 B/C 档。

运行时还要拒绝：已死亡/正在移除的实体、正被骑乘的实体（可配）、已被其他模组接管的实体（冲突检测）。

---

## 9. 预设系统

### 9.1 数据包 JSON
路径 `data/<namespace>/ai_core/presets/<id>.json`，用 `SimpleJsonResourceReloadListener` 加载（双端一致，不碰各加载器 DataPackRegistry 事件），加载后把「预设摘要」同步给客户端。

```json
{
  "display": { "name": "守卫", "description": "忠于主人，巡逻并驱赶敌对生物" },
  "brain": "hybrid",
  "persona": "你是一名沉默寡言的卫兵，优先保护主人，绝不主动伤害村民。",
  "goals": [
    { "type": "aicorelib:protect_owner", "weight": 1.0 },
    { "type": "aicorelib:patrol",        "weight": 0.6 },
    { "type": "aicorelib:retreat_low_hp","weight": 0.9 }
  ],
  "tools": ["move_to", "attack", "look_at", "say", "follow"],
  "attributes": { "follow_range": 24, "speed_modifier": 1.1 },
  "safety": { "allow_pvp": false, "allow_block_break": false, "action_radius": 32 },
  "budget": { "decision_interval_ticks": 10, "max_tokens_per_hour": 20000 }
}
```

### 9.2 内置预设
| ID | 定位 | 特点 |
|---|---|---|
| `aicorelib:guard` | 守卫 | 认主、巡逻、驱敌、低血撤退 |
| `aicorelib:companion` | 随从 | 紧跟主人、帮忙打 |
| `aicorelib:farmer` | 农夫 | 日出耕作、收割补种、回仓 |
| `aicorelib:sentry` | 哨兵 | 定点警戒、发现敌人报警 |
| `aicorelib:blank` | 空壳 | 只剩 LLM 接管，用于自然语言指挥 |

### 9.3 覆盖与继承
数据包可覆盖内置预设（同 ID）；支持 `"extends": "aicorelib:guard"` 做增量修改。

---

## 10. 扩展 API（社区接口）

包名 `com.aicorelib.api`，独立于实现，承诺 semver 兼容。

| 扩展点 | 接口 | 注册方式 |
|---|---|---|
| 预设 | `AiPreset` + Codec | 数据包 JSON / `Presets.register()` |
| 感知 | `Sensor` | `AiApi.registerSensor(id, sensor)` |
| 目标 | `UtilityGoal`（`score`/`start`/`tick`/`stop`） | 代码注册，JSON 按 ID 引用 |
| 动作 | `AiAction`（含 JSON Schema 给 LLM 用） | `AiApi.registerAction()` |
| 大脑 | `BrainFactory` | `AiApi.registerBrain()` |
| 模型供应商 | `LlmProvider` | `AiApi.registerProvider()` |
| 提示词 | `PromptContributor` | 事件监听 |
| 安全策略 | `SafetyPolicy`（可否决任何 Intent） | 事件监听 |
| 生命周期 | `AgentListener`（injected/removed/tick/decision/action/error） | 事件监听 |
| 实体适配 | `AgentHostFactory` | `AiApi.registerHost()` |
| 界面 | `AgentPanelTab` | 客户端 API |

### 插件发现
`ServiceLoader` 在 Fabric 上不可靠（每模组独立类加载器）。按平台来，`@ExpectPlatform` 包一层：
- Fabric：自定义 entrypoint `aicorelib`（`FabricLoader#getEntrypointContainers`）
- NeoForge：`@ExtensionPoint` 接口 + `IExtensionPoint` 扫描

同时保留**显式注册**作为主路径：插件在自己初始化里直接调 `AiApi.registerXxx()`。

---

## 11. LLM 接管

### 11.1 配置（服务端）
```toml
[[llm.profiles]]
id = "openai"
provider = "openai_compatible"     # 唯一内置实现
base_url = "https://api.openai.com/v1"
model = "gpt-4o-mini"
api_key_env = "AICORELIB_OPENAI_KEY"   # 只存环境变量名
max_output_tokens = 256
timeout_seconds = 8
```

`api_key_env` 指向环境变量（或 `api_key_file` 指向仅服务器可读的文件）。配置文件里不出现明文 key；物品/网络包只带 `profileId`。

### 11.2 通用兼容口
只做一种协议：**OpenAI 兼容的 `/chat/completions`**。改 `base_url` 即可覆盖 OpenAI、DeepSeek、Moonshot、Qwen、GLM、OpenRouter、vLLM、LM Studio、Ollama(`/v1`)。
新协议（如 Anthropic 原生）由社区通过 `LlmProvider` 补。

### 11.3 请求构造
```
[角色] 你是{name}（{persona}）
[处境] 坐标(32,64,-8) 群系 plains 时间 白天 生命 18/20 手持 铁剑
[周遭] 主人 Steve 距离6格；僵尸×2 距离9格 正在接近
[记忆] 被僵尸攻击(-4HP)；主人命令"守住门口"
[可行动作] move_to / attack / follow / look_at / say / wait / flee
[约束] 只输出 JSON；不得离开主人 32 格；不得破坏方块
```
输出 `{"action":"attack","args":{"target":"zombie_12"},"say":"有敌人！"}`
目标用短 ID 而非 UUID（省 token，也避免模型乱编）。

### 11.4 成本控制
| 手段 | 效果 |
|---|---|
| 事件驱动而非定时 | 1800 次/h → 100~400 次/h |
| 32 格内有人才全速思考 | 无人区 agent 休眠，成本≈0 |
| 距离越远决策越慢（10s→30s） | 线性摊薄 |
| 相同状态哈希缓存（TTL 5s） | 重复情境直接复用 |
| 每 agent 每小时 token 上限 | 超限降级 LocalBrain |
| 全局每小时预算 | 超限全体降级并告警 |

### 11.5 合规与隐私
只发结构化状态摘要，不发玩家聊天记录，坐标按区块取整；默认关闭，需管理员显式配置；日志脱敏。

---

## 12. 网络与客户端

- 协议：Architectury `NetworkManager` + `CustomPacketPayload`。
- 同步：Agent 摘要（ID、预设、主人、HP%、当前动作文案）、预设列表、核心信息。
- 频率：dirty flag 驱动，最高 4Hz，只发给追踪该实体的玩家。
- 客户端只做表现，不做权威判定。

---

## 13. 命令

**M0 已实现**（需要权限等级 2）：

```
/aicorelib list                       列出所有被注入的 agent
/aicorelib inject <targets> [preset]  不靠物品，直接把 AI 挂到生物上（管理员/调试）
/aicorelib remove <targets>           解除指定生物
/aicorelib clear                      清空全部 agent
```

**规划中**（M1+）：

```
/aicorelib preset list
/aicorelib preset apply <preset> [targets]
/aicorelib agent info <target>
/aicorelib agent pause|resume <target>
/aicorelib agent memory <target>
/aicorelib llm test <profile>
/aicorelib llm budget
/aicorelib debug on|off
```

---

## 14. 工程结构与依赖

```
AICoreLib/
├─ gradle.properties
├─ settings.gradle          ← Groovy（非 kts），末尾有 loom 平台钩子
├─ build.gradle
├─ common/
│  └─ src/main/java/com/aicorelib/
│     ├─ api/        ← 稳定公开接口（待建）
│     ├─ agent/      ← AgentRecord / AgentSavedData / AgentService
│     ├─ core/       ← ModItems / ModCommands
│     ├─ item/       ← AiInjectorItem / AiCoreItem
│     ├─ preset/     ← 预设加载（待建）
│     ├─ llm/        ← LLM 接入（待建）
│     ├─ net/        ← 网络包（待建）
│     └─ platform/   ← @ExpectPlatform 声明（待建）
├─ fabric/
└─ neoforge/
```

| 组件 | 版本 |
|---|---|
| Java | 21 |
| Gradle | 8.8（wrapper 固定） |
| Architectury API | 13.0.11（1.21/1.21.1） |
| Architectury Loom | 1.7-SNAPSHOT（必须，见 `BUILD.md` §5.3） |
| Fabric Loader / API | 0.16.14 / 0.116.17+1.21.1 |
| NeoForge | 21.1.265 |

构建细节、镜像与五处非标准配置见 `docs/BUILD.md`。

**架构纪律（为将来可能加 Forge 留后路）：**
1. `common` 不 import `net.neoforged.*` / `net.fabricmc.*`。
2. `common` 对 Architectury 的依赖收敛到 `platform/` 包内（注册、网络、事件三处）。
3. 业务代码只用 vanilla 类型 + 自研接口。
4. 平台差异一律走 `@ExpectPlatform`。

---

## 15. 里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| M0 骨架 | 构建跑通、注册注入器与核心、注入/解除、SavedData 落盘、基础命令 | 重启服务器后 agent 仍在 |
| M1 规则大脑 | Goal 插入、Sensor/Goal/Action SPI、3 个内置预设、核心迁移、调试渲染 | 守卫能跟人、能打怪、不溺水不卡墙 |
| M2 数据驱动 | 数据包预设、reload、客户端同步、覆盖/继承 | 加个 JSON 就能出新 AI |
| M3 LLM | Provider、配置、异步调度、schema 校验、预算与熔断、降级 | 断网/超时/乱输出都不影响服务器 |
| M4 打磨 | GUI、权限、上限、命令补全、文档、示例插件 | 第三方模组 30 行代码接入 |
| M5 发布 | Modrinth/CurseForge、API 文档、兼容矩阵 | — |

**当前进度（M0 已验收）**：构建链路打通，`./gradlew clean build` 产出 `aicorelib-fabric-0.1.0.jar` 与 `aicorelib-neoforge-0.1.0.jar`（各 18 KB，common 类已合并、资源齐全）。
运行时已在 Fabric 开发服务器实测：mod 正常加载（46 个 mod 无异常）；注入器与 AI 核心物品注册成功（`/summon` 出实体物品）；`/aicorelib inject|remove|list|clear` 全部按预期返回；agent 记录写入 `world/data/aicorelib_agents.dat`（155 B），**服务器完整重启后 `/aicorelib list` 仍能读回同一条记录**——M0 验收标准达成。
**尚未验证**：NeoForge 只跑到「开发服务器能启动、AICoreLib 出现在 mod 列表里、`Done (7.7s)`」，没有在 NeoForge 上重跑命令/持久化测试；注入器的右键交互（`interactLivingEntity`）没有真人玩家点过，只验证了命令路径。

---

## 16. 风险登记

| 风险 | 影响 | 对策 |
|---|---|---|
| LLM 调用阻塞主线程 | 服务器卡死 | 全异步 + 超时 + 熔断，主线程只消费队列 |
| 模型输出非法动作 | 实体乱跑/破坏存档 | Schema 校验 + 白名单 + 半径/权限约束 + SafetyPolicy 否决 |
| 大量 agent 拖垮 TPS | 卡服 | 上限 + 激活门控 + 降频 + 感知节流 |
| Goal 抢占导致溺水/卡墙 | 行为变蠢 | 不抢 JUMP flag、保留 FloatGoal、卡住检测 |
| API Key 泄露 | 被盗刷 | 只存环境变量名；物品与网络包零明文；日志脱敏 |
| 核心被刷 | 经济崩坏 | 掉落条件限制 + lineage 迁移次数上限 + 记忆/好感衰减 |
| 与其他 AI 模组冲突 | 行为错乱 | goal 优先级可配、冲突检测、文档说明 |
| 存档膨胀 | 老存档变大 | 只存必要字段、死亡清理、记忆摘要截断 |
| Architectury 生态变动 | 未来断供 | common 与平台层隔离（§14 纪律） |
| 玩家滥用（PvP/拆迁） | 服务器事故 | 默认禁用破坏/攻击玩家 |

---

## 17. 决策记录

| # | 决策 | 结论 |
|---|---|---|
| 1 | 平台路线 | **A：Architectury 双端 Fabric + NeoForge** |
| 2 | 客户端是否强制 | **不强制**，纯服务端可跑；贴图/GUI 需客户端 |
| 3 | 实体范围 | **第一版只做 `Mob`（含友好生物）**，B/C 档留接口，Player 硬禁 |
| 4 | LLM 协议 | **单一 OpenAI 兼容通用口** |
| 5 | 核心掉落 | **要**，带迁移损耗与 lineage 上限 |
| 6 | 物品形态 | **自定义物品 + 物品标签**，贴图沿用原版调试棒 |

---

## 18. 附属模组对主模组的 API 约束

已规划一个内容型附属模组 **Monsterheart**（中文名：我跟AI怪物在MC谈恋爱），详见 [ADDON-MONSTERHEART.md](ADDON-MONSTERHEART.md)。

它反推回主模组的硬性要求，**M0/M1 必须做到**：

1. `AgentState` 提供可扩展字段 `Map<ResourceLocation, byte[]> extras`，允许附属模组挂关系数据，而不必另开一份存档。
2. `AgentListener` 必须覆盖 `injected / removed / tick / death` 四个事件。
3. 客户端界面层要暴露 `AgentPanelTab` 注册入口（附属模组要加"关系"页）。
4. 允许注册自定义 `AiAction` 与客户端 `Screen` 的网络通道。
5. `AgentState.flags` 提供通用布尔位，供附属模组挂玩法开关（如 Monsterheart 的「恋爱路线」开关）；主库不解释这些位的语义。

否则附属模组只能自建存档，会导致状态分裂。这也是把 AICoreLib 做成"库"而不是"功能模组"的试金石。
