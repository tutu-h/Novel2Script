# Novel2Script 剧本 YAML Schema 规范

> 版本：1.0 · 最后更新：2026-06-06

本文档定义了 Novel2Script 所使用的剧本 YAML 数据格式。该格式用于将长篇小说文本结构化为可编辑、可导出的剧本（screenplay）形式，是系统各模块之间数据流转的核心载体。

---

## 一、设计理念

Novel2Script 的核心任务是将叙事性的小说文本转换为结构化的剧本格式。在设计 Schema 时，我们面临一个根本矛盾：小说是自由散文体，而剧本需要严格的层级结构。以下设计决策正是为了在两者之间取得平衡。

### 1.1 为什么选择 YAML

在 JSON、XML、YAML 三种常见格式中，我们选择 YAML 作为剧本的存储和交换格式，原因有三。首先，YAML 的缩进式语法天然适合表达层级结构，一个剧本从幕到场景到节拍再到对白，层级深度达到四至五层，YAML 的可读性远优于 JSON。其次，YAML 支持多行字符串和注释，方便人工编辑和审阅——这是剧本工作流中频繁进行的操作。最后，YAML 在影视和戏剧行业已有广泛使用先例（如 Fountain 格式的 YAML 变体），用户更容易理解和接受。

### 1.2 五层嵌套结构的设计考量

剧本采用 `幕(Act) → 场景(Scene) → 节拍(Beat) → 对白(Dialogue) / 舞台指示(Stage Direction)` 的四层内容嵌套，外加一层元数据。这个结构参考了经典戏剧理论和编剧方法论：

**幕（Act）** 对应小说中的章节，是宏观叙事的分割单元。每一幕代表一个完整的戏剧段落，通常包含数个场景。将章节映射为幕，既保留了原作的叙事节奏，又符合剧本的组织方式。

**场景（Scene）** 是戏剧的最小空间单元，由统一的时空背景（setting）定义。当时间、地点或氛围发生显著变化时，就会切换到新的场景。这种划分方式使导演和演员能够清晰地识别每一场戏的排演单元。

**节拍（Beat）** 是剧本中最核心的创作单元。在编剧理论中，一个 Beat 代表一个戏剧动作或情感转变——它是推动剧情前进的最小动力。将小说文本分解为一系列 Beats，实质上是在完成从"描述性叙事"到"动作性戏剧"的转换。每个 Beat 包含描述（发生了什么）、对白（角色说了什么）和舞台指示（角色做了什么）。

**对白与舞台指示** 分离存放，因为在剧本中这两者承担着完全不同的功能：对白是角色的言语表达，舞台指示是角色的肢体动作和环境变化。分离存储使得导出为不同格式时可以分别处理（例如 PDF 导出中，对白居中排版，舞台指示使用斜体）。

### 1.3 溯源映射（Source Mapping）

这是本 Schema 中最不寻常的设计。每个 Beat 都可以携带 `source_mapping` 字段，记录该节拍对应的原文片段和置信度。这是因为 Novel2Script 的转换由 AI 完成，而 AI 的转换质量需要可验证。溯源映射使得用户可以：对照原文检查转换是否忠实，识别 AI 可能"发挥过多"或"遗漏内容"的段落，在编辑修改后仍然保留与原文的关联。

置信度（confidence）分为 `high`、`medium`、`low` 三级，由 AI 在生成时自行评估。`high` 表示该节拍直接来自原文的明确段落，`low` 表示 AI 进行了较多的推理或补充。

### 1.4 章节归属标记

`source_chapter`（场景级）和 `source_chapters`（幕级）字段不在 LLM 生成的 YAML 中出现，而是由后端的组装器（assembler）在合并各章输出时自动注入。这是因为 Novel2Script 支持逐章生成和增量生成——每次只处理部分章节，然后合并到已有剧本中。章节归属标记使得系统能够在增量更新时精确替换对应章节的内容，而不影响其他部分。

---

## 二、Schema 结构总览

```
root
└── script                          [object, 必需]
    ├── metadata                    [object, 必需]
    │   ├── title                   [string, 必需]
    │   ├── author                  [string]
    │   └── source_chapters         [integer[]]
    │
    ├── characters                  [object[]]
    │   └── 每个 character:
    │       ├── name                [string, 必需]
    │       ├── description         [string]
    │       └── role_type           [string, 枚举]
    │
    ├── locations                   [object[]]
    │   └── 每个 location:
    │       ├── name                [string, 必需]
    │       └── description         [string]
    │
    └── acts                        [object[], 必需, 最少1项]
        └── 每个 act:
            ├── act_id              [integer, 必需]
            ├── title               [string]
            ├── source_chapters     [integer[]]
            └── scenes              [object[], 必需, 最少1项]
                └── 每个 scene:
                    ├── scene_id    [integer, 必需]
                    ├── setting     [string, 必需]
                    ├── characters  [string[]]
                    ├── mood        [string]
                    ├── transition  [string]
                    ├── source_chapter [integer]
                    └── beats       [object[], 必需, 最少1项]
                        └── 每个 beat:
                            ├── beat_id          [integer, 必需]
                            ├── description      [string]
                            ├── dialogues        [object[]]
                            │   └── 每个 dialogue:
                            │       ├── speaker  [string, 必需]
                            │       └── text     [string, 必需]
                            ├── stage_directions [string[]]
                            └── source_mapping   [object]
                                ├── source_chapter [integer]
                                ├── source_range   [string]
                                └── confidence     [string, 枚举]
```

---

## 三、字段详细定义

### 3.1 根节点 `script`

剧本的顶层容器。所有剧本内容必须包裹在 `script` 键下，这一层包装用于区分剧本数据和可能的其他 YAML 内容（如配置文件），也使得 Schema 验证器能够快速定位到有效数据。

### 3.2 `metadata` — 剧本元信息

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `title` | string | 是 | 剧本标题，取自项目名称。 |
| `author` | string | 否 | 原作者姓名。 |
| `source_chapters` | integer[] | 否 | 本剧本覆盖的小说章节编号列表，如 `[1, 2, 3]`。由组装器根据实际处理的章节自动填充。 |

### 3.3 `characters` — 角色表

全局角色注册表，列出剧本中涉及的所有角色。角色表在剧本生成流程中先于幕/场内容被提取，供后续 LLM 调用时作为上下文参考，确保角色名称和描述的一致性。

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `name` | string | 是 | 角色名称。 |
| `description` | string | 否 | 角色描述，包括外貌、性格、身份等信息。 |
| `role_type` | string | 否 | 角色分类，取值范围见下表。 |

**`role_type` 枚举值：**

| 值 | 含义 | 说明 |
|----|------|------|
| `protagonist` | 主角 | 故事的核心人物，驱动主线剧情。 |
| `antagonist` | 对手 | 与主角对立的角色或力量。 |
| `supporting` | 配角 | 对剧情有重要影响但非核心的角色。 |
| `minor` | 龙套 | 短暂出场、功能性的角色。 |

### 3.4 `locations` — 场景地点

全局场景地点注册表，记录剧本中出现的所有地点及其环境描述。

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `name` | string | 是 | 地点名称，如"测验广场"、"迎客大厅"。 |
| `description` | string | 否 | 地点描述，包括环境氛围、空间特征等。 |

### 3.5 `acts` — 幕

幕是剧本的最高层级叙事单元，对应小说中的一个或多个章节。

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `act_id` | integer | 是 | 幕的全局唯一编号，从 1 开始递增。由组装器在合并时统一分配，而非 LLM 生成。 |
| `title` | string | 否 | 幕标题，通常为章节名或概括性描述。 |
| `source_chapters` | integer[] | 否 | 该幕来源的章节编号列表，如 `[3]`。由组装器注入。 |
| `scenes` | object[] | 是 | 该幕下的场景列表，至少包含一个场景。 |

**为什么 `act_id` 由组装器分配？** 当 LLM 逐章生成剧本时，每一章的输出都从 `act_id: 1` 开始。如果直接使用 LLM 的编号，合并后会出现多个重复的 `act_id`。组装器重新分配全局递增编号，确保唯一性和连续性。

### 3.6 `scenes` — 场景

场景是统一时空下的戏剧段落，由 setting（场景设定）定义边界。

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `scene_id` | integer | 是 | 场景的全局唯一编号，从 1 开始递增。由组装器分配。 |
| `setting` | string | 是 | 场景设定，描述时间、地点和整体氛围。例如："测验广场，白天，人头汹涌，喧闹嘈杂，氛围压抑且残酷。" |
| `characters` | string[] | 否 | 该场景中出场的角色名称列表。 |
| `mood` | string | 否 | 场景的情感基调，如"压抑，嘲讽，落寞"。 |
| `transition` | string | 否 | 到下一场景的过渡方式，如"切至"、"淡出"。 |
| `source_chapter` | integer | 否 | 该场景来源的章节编号。由组装器注入。 |
| `beats` | object[] | 是 | 该场景下的节拍列表，至少包含一个节拍。 |

**`setting` 字段的设计考量：** 我们将时间、地点、氛围合并为一个自由文本字段，而非拆分为 `time`、`place`、`atmosphere` 三个字段。原因在于：小说中的时空信息往往交织在叙述中（"月如银盘，漫天繁星"同时描述了时间和环境），强行拆分既增加了 LLM 的输出难度，也降低了描述的文学性。

### 3.7 `beats` — 节拍

节拍是剧本的最小叙事动作单元，描述一个戏剧动作或情感变化。

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `beat_id` | integer | 是 | 节拍在场景内的序号，从 1 开始。 |
| `description` | string | 否 | 节拍的动作描述，概括这个节拍中"发生了什么"。 |
| `dialogues` | object[] | 否 | 该节拍中的对白列表。 |
| `stage_directions` | string[] | 否 | 舞台指示列表，描述角色的肢体动作、表情变化、走位等。 |
| `source_mapping` | object | 否 | 溯源映射，指向原文对应段落。 |

**对白与舞台指示的分离设计：** `dialogues` 和 `stage_directions` 是两个独立的数组，而非合并为一个时间线序列。这一选择是有意为之：在大多数剧本格式中，对白和舞台指示的排版方式截然不同（对白通常居中、标注角色名；舞台指示使用斜体、缩进较小）。分离存储使得导出服务可以分别为两者应用不同的样式，而无需在混合序列中判断每个元素的类型。

### 3.8 `dialogues` — 对白

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `speaker` | string | 是 | 说话角色的名称。 |
| `text` | string | 是 | 对白内容。 |

### 3.9 `source_mapping` — 溯源映射

| 字段 | 类型 | 必需 | 说明 |
|------|------|:----:|------|
| `source_chapter` | integer | 否 | 来源章节编号。 |
| `source_range` | string | 否 | 原文摘录片段，展示该节拍对应的小说原文。通常截取首尾关键词加省略号，如"斗之力，三段！...级别：低级！"。 |
| `confidence` | string | 否 | 转换置信度，由 LLM 自评。取值范围：`high`（忠实还原）、`medium`（适度改编）、`low`（较大推理或补充）。 |

**溯源映射的核心价值：** AI 驱动的小说-剧本转换本质上是一个有损过程——AI 在理解原文后重新组织为戏剧形式，必然会引入取舍和创造。溯源映射是这个黑盒过程的"透明化窗口"，让用户能够评估转换质量并在必要时进行修正。

---

## 四、完整示例

以下是一个完整但精简的剧本 YAML 示例，展示了所有字段的使用方式：

```yaml
script:
  metadata:
    title: 斗破苍穹
    author: 天蚕土豆
    source_chapters:
    - 1
    - 2
    - 3

  characters:
  - name: 萧炎
    description: 15岁少年，萧家三少爷。曾是家族百年内最年轻的天才斗者。
    role_type: protagonist
  - name: 萧薰儿
    description: 身着紫色衣裙的少女，气质清冷淡然。斗之气九段。
    role_type: supporting
  - name: 纳兰嫣然
    description: 云岚宗宗主弟子，天赋绝佳，为解除婚约而来。
    role_type: antagonist

  locations:
  - name: 测验广场
    description: 人头汹涌，喧闹嘈杂。是家族举行斗之气测验的公共场所。
  - name: 迎客大厅
    description: 宽敞且肃穆，座位排列等级分明。家族接待贵客的正式场所。

  acts:
  - act_id: 1
    title: 陨落的天才
    source_chapters:
    - 1
    scenes:
    - scene_id: 1
      setting: 测验广场，白天，人头汹涌，喧闹嘈杂，氛围压抑且残酷。
      characters:
      - 萧炎
      - 测验中年男子
      mood: 压抑，嘲讽，落寞
      transition: 切至
      beats:
      - beat_id: 1
        description: 萧炎注视魔石碑，测验员冷漠公布其低微的成绩。
        dialogues:
        - speaker: 测验中年男子
          text: 萧炎，斗之力，三段！级别：低级！
        stage_directions:
        - 萧炎面无表情地望着测验魔石碑上刺眼的五个大字。
        - 萧炎紧握手掌，指甲深深刺进掌心。
        source_mapping:
          source_chapter: 1
          source_range: 斗之力，三段！...级别：低级！
          confidence: high
      - beat_id: 2
        description: 周围族人肆意嘲讽，萧炎默默忍受并落寞退场。
        dialogues:
        - speaker: 萧家子弟甲
          text: 三段？嘿嘿，果然不出我所料，这个天才这一年又是在原地踏步！
        - speaker: 萧家子弟乙
          text: 哎，这废物真是把家族的脸都给丢光了。
        stage_directions:
        - 广场上的人群爆发出一阵嘲讽的骚动。
        - 萧炎落寞地转身，安静地回到队伍最后一排。
        source_mapping:
          source_chapter: 1
          source_range: 三段？嘿嘿...与周围的世界，有些格格不入。
          confidence: high
      source_chapter: 1

  - act_id: 2
    title: 斗之气大陆
    source_chapters:
    - 2
    scenes:
    - scene_id: 2
      setting: 山崖之巅，夜晚，月如银盘，漫天繁星，草地开阔。
      characters:
      - 萧炎
      - 萧战
      mood: 孤独，苦涩，温情
      transition: 淡出
      beats:
      - beat_id: 1
        description: 萧炎独自在山崖仰望星空，发泄内心的不甘。
        dialogues:
        - speaker: 萧炎
          text: 我草你奶奶的，把劳资穿过来当废物玩吗？草！
        stage_directions:
        - 萧炎跳起身来，对着夜空失态地咆哮。
        source_mapping:
          source_chapter: 2
          source_range: 我草你奶奶的...草！
          confidence: high
      source_chapter: 2
```

---

## 五、字段来源与生成流程

剧本中的字段来自两个不同的阶段，理解这一点对于使用和扩展 Schema 非常重要。

### 5.1 LLM 生成的字段

以下字段由大语言模型在转换小说文本时直接产出：

- `metadata.title`、`metadata.author` — 从项目信息传入
- `characters` — 通过专门的角色提取 LLM 调用生成
- `locations` — 通过专门的地点提取 LLM 调用生成
- `acts[].title` — LLM 根据章节内容拟定
- `acts[].scenes[]` 及其所有子字段 — LLM 根据章节原文生成

### 5.2 组装器注入的字段

以下字段由后端组装器（`assembleYaml`）在合并各章输出时自动添加：

- `metadata.source_chapters` — 根据实际处理的章节列表计算
- `acts[].act_id` — 全局递增编号，确保合并后唯一
- `acts[].source_chapters` — 标记该幕来自哪些章节
- `acts[].scenes[].scene_id` — 全局递增编号
- `acts[].scenes[].source_chapter` — 标记该场景来自哪个章节

### 5.3 增量更新时的行为

当用户使用增量生成功能（仅对部分章节重新生成）时，组装器会：解析已有剧本，提取不属于目标章节的幕，为目标章节生成新的幕，合并后重新分配所有 `act_id` 和 `scene_id`，并更新 `metadata.source_chapters`。

---

## 六、校验与容错

### 6.1 硬性校验（缺失则报错）

以下字段缺失时，校验器会将 `valid` 标记为 `false` 并返回错误信息：

- `script` 根节点必须存在且为对象
- `script.metadata` 和 `script.metadata.title` 必须存在
- `script.acts` 必须存在且为非空数组
- 每个幕必须有 `act_id` 和 `scenes`（非空数组）
- 每个场景必须有 `scene_id`、`setting` 和 `beats`（数组）
- 每个节拍必须有 `beat_id`
- 每条对白必须有 `speaker` 和 `text`

### 6.2 软性警告（缺失不阻断）

以下字段缺失时仅产生警告，不影响校验通过：

- `metadata.source_chapters`
- `beats` 为空数组（场景有节拍但列表为空）
- `stage_directions` 不是数组类型
- `source_mapping` 不是对象类型
- `source_mapping.source_chapter` 和 `confidence`

### 6.3 自动修复

校验服务提供 `autoFixCommonErrors` 方法，可以自动修复 LLM 输出中常见的问题：

- 缺少 `script` 包装层时自动添加
- 缺少 `metadata` 或 `title` 时创建默认值（"未命名剧本"）
- `act_id`、`scene_id`、`beat_id` 为字符串时尝试解析为整数
- `stage_directions` 为标量时自动包装为单元素数组
- 对话缺少 `speaker` 时填充默认值（"未知角色"）

---

## 七、导出格式映射

剧本 YAML 可以导出为三种格式，每种格式对 Schema 字段的消费方式有所不同：

### 7.1 YAML 重导出

直接对原始 YAML 进行重新格式化（block flow style, indent=2, unicode, 不分行），所有字段原样保留。

### 7.2 Markdown 导出

| Schema 字段 | Markdown 输出 |
|---|---|
| `metadata.title` | `# 标题` |
| `metadata.author` | `**作者**: xxx` |
| `characters` | `## 角色表` 下的列表项 |
| `locations` | `## 场景地点` 下的列表项 |
| `acts[].act_id` + `title` | `## 第N幕: 标题` |
| `scenes[].scene_id` + `setting` | `### 场 N - 场景设定` |
| `scenes[].mood` | 斜体文本 |
| `scenes[].characters` | `**出场角色**: A, B, C` |
| `beats[].description` | 正文段落 |
| `beats[].stage_directions` | 逐条斜体 |
| `beats[].dialogues` | `**角色名**: 对白内容` |
| `beats[].source_mapping` | 引用块 `> 原文摘录 [置信度]` |
| `scenes[].transition` | 分割线 + 斜体过渡语 |

### 7.3 HTML/PDF 导出

与 Markdown 导出消费相同字段，但渲染为带 CSS 样式的 HTML。PDF 通过 OpenHTMLToPDF 从 HTML 转换，需要注册系统中文字体（SimHei、SimSun 等）以正确渲染中文。HTML 输出额外使用 `source_mapping.source_chapter` 显示"来源: 第N章"标签。

---

## 八、最小合法 YAML

校验器接受的最小合法剧本如下：

```yaml
script:
  metadata:
    title: 未命名剧本
  acts:
    - act_id: 1
      title: 第一幕
      scenes:
        - scene_id: 1
          setting: 默认场景
          beats: []
```

此结构可以通过校验，但在实际使用中，空的 `beats` 数组会触发软性警告。

---

## 九、版本与兼容性

当前 Schema 版本为 1.0，定义文件位于 `backend/src/main/resources/schema/script_schema.yaml`。Schema 设计遵循以下向后兼容原则：

- **新增字段始终为可选**：未来新增的字段不会标记为 `required`，旧数据仍然合法。
- **不删除字段**：已定义的字段不会被移除，只会被标记为弃用（deprecated）。
- **组装器注入的字段独立于 LLM**：LLM prompt 的变更不会影响组装器注入的字段（如 `act_id`、`source_chapter`），反之亦然。
