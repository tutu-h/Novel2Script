# Novel2Script

AI 小说转剧本工具 —— 面向小说作者的 AI 剧本初稿生成与结构化编辑工作台。

## 功能特性

- 上传/粘贴小说文本，支持自动分章
- AI 自动分析人物、场景、事件
- 生成 YAML 结构化剧本（Acts / Scenes / Beats / Dialogues）
- 每个剧本片段可追溯到原文（source_mapping）
- Schema 校验与自动修复
- 可视化编辑剧本
- 导出 YAML / Markdown / PDF

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.3 |
| 数据库 | H2 (开发) / SQLite (生产) |
| AI 模型 | DeepSeek API (OpenAI 兼容) |
| 前端 | React 18 + TypeScript + Vite |
| 样式 | Tailwind CSS |

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Node.js 18+
- DeepSeek API Key ([获取地址](https://platform.deepseek.com))

### 后端启动

```bash
cd backend

# 配置 API Key（编辑 .env 文件，填入你的 DeepSeek API Key）
# 或者设置环境变量: export DEEPSEEK_API_KEY=sk-xxx

# 编译并启动
mvn spring-boot:run
```

后端默认运行在 http://localhost:8080

### 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 http://localhost:5173，API 请求自动代理到后端。

## 项目结构

```
Novel2Script/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/novel2script/
│   │   ├── config/             # 配置类（CORS、DeepSeek）
│   │   ├── controller/         # REST API 控制器
│   │   ├── dto/                # 数据传输对象
│   │   ├── entity/             # JPA 实体类
│   │   ├── exception/          # 异常处理
│   │   ├── repository/         # 数据访问层
│   │   └── service/            # 业务逻辑层
│   └── src/main/resources/
│       ├── schema/             # YAML Schema 定义
│       └── application.yml     # 应用配置
├── frontend/                   # React 前端
│   ├── src/
│   │   ├── components/         # UI 组件
│   │   ├── pages/              # 页面组件
│   │   ├── services/           # API 调用服务
│   │   ├── hooks/              # 自定义 Hooks
│   │   └── types/              # TypeScript 类型定义
│   └── vite.config.ts
└── README.md
```

## API 接口

### 项目管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/projects | 创建项目 |
| GET | /api/projects | 获取项目列表 |
| GET | /api/projects/{id} | 获取项目详情 |
| POST | /api/projects/{id}/chapters | 添加章节 |
| POST | /api/projects/{id}/chapters/batch | 批量导入章节 |

### 剧本生成
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/scripts/generate | 生成剧本 |
| GET | /api/scripts/{id} | 获取剧本 |
| POST | /api/scripts/{id}/validate | 校验剧本 YAML |
| POST | /api/scripts/{id}/fix | 自动修复 YAML |

### 导出
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/export | 导出剧本（yaml/markdown/pdf） |

## 剧本 YAML 结构

```yaml
script:
  metadata:
    title: "作品标题"
    author: "作者"
    source_chapters: [1, 2, 3]
  characters:
    - name: "角色名"
      description: "角色描述"
      role_type: "protagonist"
  acts:
    - act_id: 1
      scenes:
        - scene_id: 1
          setting: "场景描述"
          characters: ["角色A", "角色B"]
          beats:
            - beat_id: 1
              description: "事件描述"
              dialogues:
                - speaker: "角色A"
                  text: "对白内容"
              stage_directions: ["舞台指示"]
              source_mapping:
                source_chapter: 1
                source_range: "原文片段..."
                confidence: "high"
```

## 开发计划

- **第一阶段**：核心功能（上传文本 → 分析 → YAML 生成 → 校验 → 导出）
- **第二阶段**：可视化编辑和 source_mapping 追溯
- **第三阶段**：修复循环、版本对比、多剧本风格
