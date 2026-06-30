# LingForge AI

LingForge AI 是一个基于 Spring Boot、Spring AI、React、Vite 和本地模型生态的开源 AI 应用框架。它把聊天、RAG、Prompt 评测、文档解析、文档质量评估、模型测评、多模态生成、3D 建模演示和运行态监控组织成一个可本地运行的 AI 工作台。

## 核心能力

- AI 对话与用户记忆
- 知识库检索与 RAG 示例
- Prompt 评测、优化与版本沉淀
- 文档摘要提取与文档质量评估
- 模型评测趋势与指标看板
- 文生图、图生文、文生视频能力接入
- 图像建模与 TripoSplat 3DGS 预览
- React + Ant Design 智能中枢工作台
- Spring Boot 运行状态、部署状态和服务大屏

## 技术栈

- Java 17
- Spring Boot 3.2.5
- Spring AI 1.0.0-M4
- Maven
- React 18
- Vite
- TypeScript
- Ant Design 5
- Ollama / OpenAI / DashScope / Stability AI 等模型接入方式

## 本地运行

### 后端

```bash
mvn spring-boot:run
```

默认访问：

```text
http://localhost:8080
```

### React 工作台

```bash
npm install
npm run dev
```

默认访问：

```text
http://127.0.0.1:5173/react-dashboard/
```

### 前端构建

```bash
npm run build
```

构建产物会输出到：

```text
src/main/resources/static/react-dashboard/
```

该目录是生成产物，默认不会提交到 Git。

## 模型配置

### Ollama

本地免费模型推荐先使用 Ollama。

```bash
ollama pull qwen2.5
ollama serve
```

常见可选模型：

```bash
ollama pull llama3.2
ollama pull mistral
ollama pull deepseek-coder
```

### OpenAI

```bash
export OPENAI_API_KEY=your-api-key
```

### 其他模型服务

项目通过环境变量读取模型服务配置。请使用占位值、`.env.example` 或本机环境变量管理配置，不要把真实密钥提交到仓库。

## 文生视频 Wan2.1

文生视频页支持接入本地 Wan2.1-T2V-1.3B 桥接服务。它不是云端免费 API，需要本机准备 Wan2.1 仓库、模型权重和可运行的 Python/GPU 环境。

```bash
export WAN_REPO_DIR=/path/to/Wan2.1
export WAN_CKPT_DIR=/path/to/Wan2.1/Wan2.1-T2V-1.3B
./scripts/start-wan-video-server.sh
```

默认接口：

- `FREE_VIDEO_API_URL=http://localhost:7861/api/text-to-video`
- `FREE_VIDEO_STATUS_URL=http://localhost:7861/api/text-to-video/status/{taskId}`
- `FREE_VIDEO_HEALTH_URL=http://localhost:7861/api/text-to-video/health`
- `FREE_VIDEO_MODEL=Wan2.1-T2V-1.3B`

## 测试与校验

```bash
mvn -q test
npm run build
npm run test:static-layout
npm run test:layout
```

开源脱敏守护测试：

```bash
mvn -q -Dtest=OpenSourceSanitizationTest test
```

## 项目结构

```text
.
├── frontend/                         # React + Vite 工作台源码
├── scripts/                          # 本地模型桥、页面契约校验脚本
├── src/main/java/com/example/demo/    # Spring Boot 后端
├── src/main/resources/static/         # 静态页面和共享前端资源
├── src/test/java/com/example/demo/    # 单元测试、页面契约测试、脱敏守护测试
├── docs/                             # 开源整理后的文档与变更记录
├── pom.xml
├── package.json
└── vite.config.ts
```

## 开源安全说明

- 不提交真实密钥、token、cookie、生产日志和本地模型权重。
- 不提交 `target/`、`node_modules/`、运行日志、生成视频、构建 jar 和本地虚拟环境。
- 若新增外部平台接入，请使用通用配置和脱敏示例。
- 推送前建议运行 `OpenSourceSanitizationTest` 和全文敏感词扫描。
