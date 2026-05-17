# AI 智能学习平台 — 开发计划

> 配套文档：[README.md](./README.md)（需求说明）  
> 预估总工期：**3～4 周**（单人全职；兼职可 ×1.5～2）  
> 当前阶段：**Phase 4 — Docker 部署联调**（Phase 0～3 已完成）

---

## 1. 里程碑总览

| 阶段 | 名称 | 工期 | 交付物 |
|------|------|------|--------|
| Phase 0 | 项目脚手架 | 2 天 | 前后端空工程、Docker 骨架、CI 本地可构建 |
| Phase 1 | 文档上传与解析 | 4 天 | PDF 上传 API、文本提取、文档 CRUD |
| Phase 2 | AI 出题引擎 | 5 天 | LLM 集成、试卷/题目持久化、生成 API |
| Phase 3 | 前端核心页面 | 5 天 | 上传、列表、出题、答题、成绩页 |
| Phase 4 | 联调与 Docker 部署 | 3 天 | docker-compose 一键启动、数据持久化 |
| Phase 5 | 测试与 MVP 验收 | 3 天 | 测试用例、Bug 修复、README 运行说明 |

**缓冲**：2 天（联调、LLM 调优、异常处理）

---

## 2. Phase 0：项目脚手架（第 1～2 天）

### 2.1 目标

搭建可运行的前后端空项目及 Docker 目录结构，统一代码规范与 API 约定。

### 2.2 任务清单

| # | 任务 | 负责模块 | 产出 |
|---|------|----------|------|
| 0.1 | 初始化 Spring Boot 3 项目（Web、JPA、Validation、Lombok） | backend | `pom.xml`、主启动类 |
| 0.2 | 配置 SQLite 数据源与 JPA（`sqlite-jdbc` + 方言） | backend | `application.yml` |
| 0.3 | 统一响应体 `Result<T>`、全局异常处理 | backend | `common` 包 |
| 0.4 | 初始化 Vue 3 + Vite + TypeScript 项目 | frontend | 基础路由、Axios 封装 |
| 0.5 | 集成 Element Plus、Pinia、Vue Router | frontend | 布局组件 `AppLayout` |
| 0.6 | 创建 `docker-compose.yml` 占位服务 | 根目录 | 可 `docker compose build` |
| 0.7 | 编写 `.env.example`、`.gitignore` | 根目录 | 环境变量模板 |

### 2.3 验收标准

- [ ] 后端 `mvn spring-boot:run` 启动成功，健康检查 `GET /actuator/health` 返回 200
- [ ] 前端 `npm run dev` 可访问空白布局页
- [ ] Git 仓库结构符合 README 目录规划

---

## 3. Phase 1：文档上传与 PDF 解析（第 3～6 天）

### 3.1 目标

实现 PDF 上传、本地存储、文本提取及文档管理 API。

### 3.2 任务清单

| # | 任务 | 详情 | 依赖 |
|---|------|------|------|
| 1.1 | 实体与 Repository | `Document` 实体、JPA Repository | 0.2 |
| 1.2 | 文件上传服务 | Multipart 接收、类型校验、按 UUID 存盘 | 0.3 |
| 1.3 | PDF 解析服务 | 集成 PDFBox，提取全文与页数 | 1.2 |
| 1.4 | 异步解析（可选） | `@Async` 或同步 MVP；状态字段 `PENDING→PARSED` | 1.3 |
| 1.5 | Document API | upload / list / get / delete | 1.1～1.4 |
| 1.6 | 单元测试 | 解析服务测试（准备 sample.pdf） | 1.3 |

### 3.3 API 实现顺序

1. `POST /api/documents/upload`
2. `GET /api/documents`
3. `GET /api/documents/{id}`
4. `DELETE /api/documents/{id}`

### 3.4 验收标准

- [ ] Postman/curl 上传 PDF 后数据库有记录、`extracted_text` 非空
- [ ] 非 PDF 文件返回 400
- [ ] 删除文档时同步删除磁盘文件

---

## 4. Phase 2：AI 出题引擎（第 7～11 天）

### 4.1 目标

对接大模型 API，根据文档文本生成结构化选择题并持久化。

### 4.2 任务清单

| # | 任务 | 详情 | 依赖 |
|---|------|------|------|
| 2.1 | 实体设计 | `Quiz`、`Question`、`QuizAttempt`、`AnswerRecord` | Phase 1 |
| 2.2 | LLM 客户端 | `RestTemplate` / `WebClient` 调用 OpenAI 兼容接口 | 0.1 |
| 2.3 | Prompt 模板 | 系统提示 + 用户内容；参数：题数、难度 | 2.2 |
| 2.4 | 响应解析 | JSON 解析、校验、失败重试（最多 2 次） | 2.3 |
| 2.5 | Quiz 生成服务 | `generateQuiz(documentId, count)` 全流程 | 2.1～2.4 |
| 2.6 | Quiz API | generate / list / get / delete | 2.5 |
| 2.7 | 答题判分服务 | `submitQuiz(quizId, answers)` 计算得分 | 2.1 |
| 2.8 | Submit API | `POST /api/quizzes/{id}/submit` | 2.7 |
| 2.9 | 集成测试 | Mock LLM 或使用真实 API 冒烟 | 2.6 |

### 4.3 Prompt 迭代计划

| 轮次 | 重点 |
|------|------|
| v1 | 能稳定输出合法 JSON |
| v2 | 减少重复题、提高选项质量 |
| v3 | 解析字段引用原文，长文档截断策略 |

### 4.4 验收标准

- [ ] 对已解析文档调用 generate 可得到 N 道题入库
- [ ] 提交答案后返回正确 `score` 与每题 `isCorrect`
- [ ] LLM 失败时 `quiz.status = FAILED` 且 API 返回明确错误

---

## 5. Phase 3：前端核心页面（第 12～16 天）

### 5.1 目标

完成 MVP 全部用户界面，与后端 API 联调。

### 5.2 任务清单

| # | 页面/模块 | 功能点 | 依赖 API |
|---|-----------|--------|----------|
| 3.1 | 布局与路由 | 侧边栏导航、路由守卫占位 | — |
| 3.2 | 首页 `/` | 简介、快捷按钮 | — |
| 3.3 | 文档上传页 | 拖拽上传、进度条、题数配置 | upload |
| 3.4 | 文档列表页 | 表格、状态标签、删除确认 | documents CRUD |
| 3.5 | 试卷题库页 | 关联文档名、生成时间、进入答题 | quizzes list |
| 3.6 | 生成试卷流程 | 从文档页跳转、Loading、错误提示 | generate |
| 3.7 | 答题页 | 单选题组件、题号导航、提交 | quiz get + submit |
| 3.8 | 成绩页 | 环形进度/分数、错题高亮、解析折叠 | submit 响应 |
| 3.9 | 全局状态 | Pinia 存储当前答题进度（可选） | — |
| 3.10 | 错误与 Loading | Axios 拦截器统一处理 | 0.4 |

### 5.3 UI 规范（简要）

- 主色：教育蓝 `#409EFF`（Element Plus 默认）
- 上传区：虚线边框 + 图标
- 答题页：一屏一题或列表模式（MVP 推荐一屏一题）

### 5.4 验收标准

- [ ] 浏览器完成「上传 → 生成 → 答题 → 看分」全流程无刷新错误
- [ ] 移动端基础可读（响应式 `@media` 简单适配）

---

## 6. Phase 4：Docker 部署与联调（第 17～19 天）

### 6.1 目标

生产式 Docker 部署，数据持久化，前后端通过 Nginx 统一入口。

### 6.2 任务清单

| # | 任务 | 详情 |
|---|------|------|
| 4.1 | 后端 Dockerfile | 多阶段构建：Maven 打包 + JRE 运行 |
| 4.2 | 前端 Dockerfile | `npm run build` + Nginx 镜像 |
| 4.3 | Nginx 配置 | `/` → 静态资源；`/api` → `backend:8080` |
| 4.4 | docker-compose | 服务、环境变量、`./data` 卷挂载 |
| 4.5 | 启动脚本 | `scripts/start.sh` 可选 |
| 4.6 | 端到端联调 | 容器环境完整走通 MVP 流程 |

### 6.3 docker-compose 草案

```yaml
services:
  backend:
    build: ./backend
    environment:
      - LLM_API_KEY=${LLM_API_KEY}
    volumes:
      - ./data:/app/data
    ports:
      - "8080:8080"

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
```

### 6.4 验收标准

- [ ] 新机执行 `docker compose up -d --build` 后访问 `http://localhost` 可用
- [ ] 重启容器后文档与试卷数据仍在

---

## 7. Phase 5：测试与 MVP 发布（第 20～22 天）

### 7.1 测试计划

| 类型 | 范围 | 工具 |
|------|------|------|
| 单元测试 | PDF 解析、JSON 解析、判分逻辑 | JUnit 5 |
| API 测试 | 文档/试卷/答题接口 | MockMvc / Postman |
| 前端测试 | 关键组件（可选） | Vitest |
| 手工测试 | 全流程 + 边界（大文件、空 PDF、API 失败） | 测试用例表 |

### 7.2 Bug 优先级

| 级别 | 定义 | 处理 |
|------|------|------|
| P0 | 阻塞主流程 | 立即修复 |
| P1 | 功能缺失但可绕过 | 发布前修复 |
| P2 | 体验问题 | 记入 Backlog |

### 7.3 文档收尾

- [ ] README 补充「快速开始」章节
- [ ] 提供 1 份示例 PDF（或生成脚本）
- [ ] 记录已知限制（扫描件、并发等）

### 7.4 MVP 验收

对照 [README.md 第 10 节](./README.md#10-验收标准mvp) 逐项勾选。

---

## 8. 技术决策记录（ADR）

| 决策 | 选项 | 结论 | 理由 |
|------|------|------|------|
| ORM | JPA vs MyBatis-Plus | **JPA** | 实体简单、SQLite 够用 |
| PDF 库 | PDFBox vs iText | **PDFBox** | 开源友好 |
| 前端 UI | Element Plus vs Ant Design Vue | **Element Plus** | 与 Vue3 生态契合 |
| 认证 | 首期免登录 vs JWT | **首期免登录** | 缩短 MVP 周期 |
| 异步出题 | 同步 vs 消息队列 | **同步 + 前端 Loading** | 架构简单；题量大时再改异步 |
| LLM 接入 | 官方 SDK vs HTTP | **WebClient + 兼容 API** | 便于切换 DeepSeek/Ollama |

---

## 9. 分支与协作建议

```
main          ← 稳定可部署
└── develop   ← 日常集成
    ├── feature/backend-document
    ├── feature/backend-quiz-llm
    ├── feature/frontend-pages
    └── feature/docker-deploy
```

- 每个 Phase 结束合并至 `develop`，MVP 完成后合并 `main` 并打 tag `v0.1.0`

---

## 10. 二期 Backlog（MVP 后）

| 优先级 | 功能 | 预估 |
|--------|------|------|
| P1 | JWT 用户系统 | 5 天 |
| P1 | 错题本 | 3 天 |
| P2 | PDF 扫描件 OCR（Tesseract） | 5 天 |
| P2 | 题目导出 Word/PDF | 3 天 |
| P2 | 异步出题 + WebSocket 进度 | 4 天 |
| P3 | PostgreSQL 替换 SQLite | 2 天 |

---

## 11. 每日进度模板（可选）

```markdown
## YYYY-MM-DD

### 完成
- 

### 进行中
- 

### 阻塞 / 风险
- 

### 明日计划
- 
```

---

## 12. 下一步行动

完成本文档评审后，建议立即执行：

1. **Phase 0.1～0.5**：初始化 `backend/` 与 `frontend/` 工程  
2. 配置 `.env` 中的 `LLM_API_KEY`  
3. 准备 1～2 份测试用 PDF（教材章节、讲义等）

如需开始编码实现，可从 Phase 0 脚手架任务逐项推进。
