# 项目文档：AI 小说转剧本工具（Novel2Script）

## 1. 项目概述

**项目名称**：Novel2Script（AI 小说剧本改编工作台）
 **项目定位**：面向小说作者的 AI 剧本初稿生成与结构化编辑工具，通过长文本理解和结构化输出，将小说自动转换为可编辑、可校验、可追溯的剧本初稿。

**项目目标**：

1. 支持上传或粘贴 3 个章节以上的小说文本；
2. 自动分析小说文本，提取人物、场景、事件；
3. 将小说内容转换为结构化剧本（YAML 格式）；
4. 支持 Schema 校验和人工二次编辑；
5. 输出可追溯到原文的剧本片段，便于作者修改。

------

## 2. 用户需求

| 编号 | 用户故事                     | 功能需求                                               |
| ---- | ---------------------------- | ------------------------------------------------------ |
| U1   | 小说作者希望快速生成剧本初稿 | 支持上传/粘贴小说文本，至少 3 章节                     |
| U2   | 作者希望角色、剧情保持一致   | 提取人物、场景、事件，维护人物设定表                   |
| U3   | 作者希望剧本结构清晰         | 生成 YAML 结构化剧本，包括 Acts/Scenes/Beats/Dialogues |
| U4   | 作者希望可追溯原文           | 保留 source_mapping，记录每个剧本片段对应小说原文      |
| U5   | 作者希望剧本可校验           | 提供 Schema 校验工具，自动提示缺失或格式错误           |
| U6   | 作者希望可二次编辑           | 可视化或文本方式编辑剧本，并更新 YAML                  |
| U7   | 作者希望生成结果可导出       | 支持导出 YAML / Markdown / PDF                         |

------

## 3. 功能模块设计

### 3.1 输入模块

- 上传/粘贴小说文本（支持 txt / md / docx）
- 检查文本长度和章节数（至少 3 章节）
- 对文本进行初步预处理：清理空行、特殊字符、统一编码

### 3.2 长文本解析模块

- 分章处理（Chapter segmentation）
- 章节摘要生成（Summarization）
- 人物/地点/事件抽取（NER + 角色关系分析）
- 场景切分（Scene segmentation）

### 3.3 剧本生成模块

- 根据解析结果生成 YAML 格式剧本

- 结构层级：

  ```
  script:
    metadata:
      title: "作品标题"
      author: "作者"
      source_chapters: []
    characters:
      - name: "角色名"
        description: "角色性格/背景"
    locations:
      - name: "场景名"
        description: "场景描述"
    acts:
      - act_id: 1
        scenes:
          - scene_id: 1
            setting: "场景描述"
            characters: ["角色A", "角色B"]
            beats:
              - beat_id: 1
                description: "事件或动作"
                dialogues:
                  - speaker: "角色A"
                    text: "对白内容"
                stage_directions: "舞台指示"
                source_mapping: "原文引用"
  ```

### 3.4 Schema 校验模块

- 校验 YAML 是否符合 Schema
- 提示缺失字段、类型错误或格式错误
- 支持部分修复或全量修复

### 3.5 编辑与导出模块

- 可视化编辑或文本编辑 YAML
- 更新 source_mapping 与角色关系
- 支持导出 YAML / Markdown / PDF

### 3.6 修复与版本控制模块（可选加分）

- 自动修复生成错误或不一致部分
- 支持保存版本、对比历史修改

------

## 4. 数据建模

### 4.1 核心实体

| 实体           | 属性                                                         | 描述         |
| -------------- | ------------------------------------------------------------ | ------------ |
| Character      | name, description, role_type                                 | 人物角色信息 |
| Location       | name, description                                            | 场景信息     |
| Act            | act_id, title, scenes                                        | 剧本章节/幕  |
| Scene          | scene_id, setting, characters, beats                         | 场景信息     |
| Beat           | beat_id, description, dialogues, stage_directions, source_mapping | 剧情节拍     |
| Dialogue       | speaker, text                                                | 对白         |
| ScriptMetadata | title, author, source_chapters                               | 剧本元信息   |

### 4.2 数据流

```
小说文本
   ↓ 长文本解析（分章、摘要、NER）
   ↓ 场景/角色/事件抽取
   ↓ YAML 生成
   ↓ Schema 校验
   ↓ 编辑 / 修复
   ↓ 导出 YAML / PDF / Markdown
```

------

## 5. 技术选型

| 模块             | 技术选型                             | 说明                          |
| ---------------- | ------------------------------------ | ----------------------------- |
| 文本处理         | Python / NLTK / spaCy / Transformers | 分章、分句、清理文本          |
| 长文本理解       | OpenAI GPT / LLaMA / ChatGLM         | 摘要、人物/事件抽取、场景切分 |
| YAML 生成        | PyYAML / ruamel.yaml                 | 结构化输出与读写              |
| Schema 校验      | jsonschema / PyYAML schema           | 校验生成 YAML 是否符合设计    |
| 可视化编辑       | React / Vue + YAML 编辑器组件        | 前端可视化编辑剧本            |
| 导出             | Python Markdown / PDF 库             | 导出可用格式                  |
| 数据存储         | SQLite / JSON 文件                   | 保存历史生成 / 版本控制       |
| 版本对比（可选） | difflib / Git 集成                   | 对比历史 YAML 修改            |
| 测试             | pytest                               | 单元测试，确保生成和校验稳定  |

------

## 6. 技术实现思路

1. 文本输入
   - 支持批量上传章节文本
   - 做文本预处理，标准化空格、换行、标点
2. 文本解析
   - 分章、分句
   - 使用大模型生成章节摘要
   - 使用 NER 提取人物、地点、事件
   - 根据事件和人物生成场景卡片
3. 剧本生成
   - 将解析结果映射到 YAML 结构
   - 每个场景包含 beats、对白、舞台指示
   - source_mapping 保留原文引用
4. Schema 校验
   - 定义严格 YAML Schema
   - 校验生成结果，提示或自动修复字段缺失/格式错误
5. 编辑与导出
   - 前端展示 YAML，支持编辑
   - 编辑后可更新 source_mapping
   - 支持导出 YAML / Markdown / PDF
6. 高级优化（可选）
   - 修复生成错误部分的循环调用
   - 保存版本历史，便于作者对比
   - 支持不同剧本风格（喜剧 / 剧情 / 推理）

------

## 7. 开发与交付建议

- **第一阶段**：完成核心功能（上传文本 → 分析 → YAML 生成 → 校验 → 导出）
- **第二阶段**：可视化编辑和 source_mapping 追溯
- **第三阶段（加分）**：修复循环、版本对比、多剧本风格