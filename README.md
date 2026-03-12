# MolGenie - 药物发现智能助手

MolGenie 是一个面向药物研发场景的智能助手，基于 **多智能体工作流 + 化学工具链 + 向量知识库（Milvus）**，帮助研究人员：

- 设计与生成候选小分子
- 基于 SDF 分析现有分子
- 将研究报告（Word/Excel 等）中的分子信息整理为可检索的化学知识库

---
<img width="1003" height="514" alt="image" src="https://github.com/user-attachments/assets/44b969c2-e333-4deb-9599-47d6a85fc171" />
<img width="995" height="434" alt="image" src="https://github.com/user-attachments/assets/5094bf26-268f-45c7-a460-c40f79701cd6" />


## 功能概览

- **多智能体发现工作流（Graph）**
  - `Planner`：根据请求判断是“生成新分子”还是“分析 SDF”
  - `Generator`：调用大模型按需求生成候选分子（SMILES）
  - `Validator`：基于 CDK 计算分子量、LogP，并做类似 Lipinski 的规则筛选
  - `Summarizer`：综合候选分子 / SDF 分子，输出中文研发建议

- **化学文件处理**
  - SDF：解析 `SDF`，提取 `SMILES + properties`
  - MOL：解析 `.mol`，生成 `SMILES`

- **化学知识库（Milvus）**
  - 支持从 **Word（.docx）/Excel（.xlsx）/SDF/MOL** 导入
  - 自动解析：
    - 文档中嵌入的 `.sdf/.mol` 文件
    - 正文/单元格中出现的 SMILES（用 CDK 校验）
  - 对每个分子存入 Milvus：
    - **分子指纹 BinaryVector**（CDK Fingerprint）→ SMILES 相似搜索
    - **文本向量 FloatVector**（DashScope Embedding）→ 语义搜索

- **前端控制台（Vue3 + Vite）**
  - Tab 1：**发现**
    - 输入需求 + 可选上传 SDF → 调用 `POST /apiGraph/discover`
    - 展示 AI 输出，可一键复制
  - Tab 2：**知识库**
    - 批量上传 `.docx/.xlsx/.sdf/.mol` → 调用 `POST /apiGraph/kb/ingest`
    - 显示已入库分子数量（Milvus 行数）
    - 检索：
      - 输入 **SMILES** → 分子指纹相似度搜索
      - 输入 **自然语言描述** → 文档/属性语义检索

---

## 技术栈

- **后端**
  - Spring Boot 3.3
  - Spring AI Alibaba（DashScope Chat + Graph Core）
  - CDK 2.11（SDF/MOL 解析、SMILES、性质计算）
  - Milvus 2.x（向量数据库，用于化学知识库）
  - Maven

- **前端**
  - Vue 3 + Vite
  - 纯组件式单页 UI（无路由），通过 Vite 代理直连后端

---

## 后端架构与核心模块

**入口应用**
- `MolgenieApplication`：Spring Boot 启动类

**多智能体 Graph 配置**
- `DrugDiscoveryGraphConfig`
  - 使用 `StateGraph` 搭建工作流：
    - `START → planner → generator → validator → summarizer → END`
  - 使用 PlantUML 输出工作流结构（启动时打印）

**智能体 / 节点**
- `PlannerAgent` / `PlannerNode`
  - 用大模型读取 `user_query`，判断任务类型：
    - `GENERATE`：生成新分子
    - `ANALYZE_SDF`：分析上传的 SDF
- `GeneratorNode`
  - 在 `GENERATE` 模式下，根据描述生成 3 个 SMILES 候选
- `ValidatorNode` + `MoleculeValidator`
  - 用 CDK 解析 SMILES，计算分子量 / 估算 LogP
  - 依据简单规则给出是否“药物样性”通过
- `SummarizerAgent` / `SummarizerNode`
  - 汇总候选分子 / SDF 分子（含部分 properties），生成中文研发建议

**化学解析**
- `SdfParser`：解析 `SDF`，输出 `MoleculeRecord(SMILES + 属性)`
- `MolParser`：解析 `.mol` 文件并生成 `SMILES`
- `SmilesTextExtractor`：从自由文本中抽取可能的 SMILES，并用 CDK 校验

**知识库（KB）**
- `DocumentIngestService`
  - 统一处理上传的 `.docx/.xlsx/.sdf/.mol`：
    - SDF/MOL：直接解析为分子
    - DOCX/XLSX：
      - 扫描 OOXML zip 中嵌入的 `.sdf/.mol`
      - 从 Word 段落 / Excel 单元格文本中抽取 SMILES
  - 对每条分子调用 KB 服务入库

- `MilvusKnowledgeBase`
  - 在 Milvus 中维护 collection（默认：`molgenie_kb_v2`）：
    - 字段：`id, smiles, sourceFileName, sourceType, ingestedAt, properties, text, textVec, fp`
    - Index：
      - `fp`（BinaryVector，JACCARD，BIN_IVF_FLAT）
      - `textVec` (FloatVector，COSINE，IVF_FLAT)
  - 提供：
    - `addMolecule`：插入一条分子 + 指纹 + 文本向量
    - `vectorSearchBySmiles`：按 SMILES 做指纹相似搜索
    - `vectorSearchByText`：按文本做语义向量搜索
    - `count`：获取 collection 行数

- `KbService` / `MilvusKbService`
  - 抽象 KB 能力，为上层提供统一接口：
    - `addMolecule(smiles, props, sourceFileName, sourceType)`
    - `search(q, limit)`：自动区分 SMILES / 文本
    - `size()`：返回总量

**DashScope 嵌入向量**
- `DashScopeEmbeddingService`
  - 通过 DashScope **OpenAI 兼容 Embedding API** 调用 `text-embedding-v4`
  - 默认返回 1024 维浮点向量，用于 Milvus `textVec` 字段

**HTTP 控制器**
- `DrugDiscoveryController`
  - `POST /apiGraph/discover`
    - 参数：
      - `query`（可选）：自然语言需求
      - `file`（可选）：SDF 文件
    - 行为：
      - 从 SDF 中解析分子（如有）
      - 组合 `user_query` + `sdf_molecules` 进入 Graph
      - 返回 Graph 状态中的 `final_response`

- `KnowledgeBaseController`
  - `POST /apiGraph/kb/ingest`
    - `multipart/form-data`，字段名 `files`，支持多文件：
      - `.docx .xlsx .sdf .mol`
  - `GET /apiGraph/kb/stats`
    - 返回：`{"molecules": <Milvus行数>}`
  - `GET /apiGraph/kb/search?q=...&limit=50`
    - `q` 为 SMILES：按分子指纹相似度搜索
    - `q` 为自然语言：按文档/属性语义向量检索

---

## 前端（Vue 控制台）

前端位于 `frontend/`，使用 Vite 代理到后端：

- 代理规则：`/apiGraph/*` → `http://localhost:8080`
- 开发端口：`http://localhost:5173`

页面为单页应用，包含两个 Tab：

1. **发现**
   - 输入需求描述（建议必填）+ 可选上传 SDF 分子文件
   - 一键调用后端 `/apiGraph/discover`
   - 显示并可复制返回的研发建议

2. **知识库**
   - 多文件上传 `.docx/.xlsx/.sdf/.mol` 并导入
   - 显示已入库分子数量
   - 检索：
     - SMILES → 分子相似度（指纹 + Jaccard）
     - 文本 → 语义相似度（DashScope Embedding + Milvus）
   - 检索结果列表可点击复制 SMILES

启动方式：

```bash
cd frontend
npm install
npm run dev
```

---

## 部署与运行

### 依赖环境

- **Java 17+**（Spring Boot 3.x 必须）
- **Node.js 18+**（开发/构建前端）
- **Milvus 2.x**（默认 `localhost:19530`）
- **DashScope API Key**（用于 Chat 与 Embedding）

### 配置

编辑 `src/main/resources/application.yml`：

- 后端端口（默认 8080）：

```yaml
server:
  port: 8080
```

- DashScope：

```yaml
spring:
  ai:
    dashscope:
      api-key: <你的 DashScope API Key>
      options:
        model: qwen3-max
        temperature: 0.7
        max-tokens: 2000
        top-p: 0.9
```

- Milvus & Embedding：

```yaml
molgenie:
  kb:
    milvus:
      host: localhost
      port: 19530
      database: default
      collection: molgenie_kb_v2
      fingerprintBits: 1024
    embedding:
      baseUrl: https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
      model: text-embedding-v4
      dimensions: 1024
```

### 启动后端

```bash
./mvnw spring-boot:run
```

确保 Milvus 已启动并可通过 `localhost:19530` 访问。

### 启动前端（开发模式）

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

---

## 后续可扩展方向

- 将知识库与发现 Graph 更深度打通（在发现流程中自动检索 KB 作为上下文）
- 支持更多性质计算与规则（如 TPSA、HBA/HBD、可溶性评估等）
- 引入更丰富的检索维度（按批次、项目、适应症等标签过滤）
