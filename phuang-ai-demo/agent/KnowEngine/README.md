# KnowEngine

KnowEngine 是一个基于 Spring Boot 和 LangChain4j 的知识文档处理服务。它负责接收文档、保存原始文件、转换内容、切分文本、生成向量并写入 Elasticsearch，同时维护文档版本及处理状态。

当前模块是 `phuang-ai-demo` Maven 多模块工程的一部分，默认监听 `10001` 端口。

## 主要能力

- 文档上传与 SHA-256 内容去重
- PDF 通过 MinerU 转换为 Markdown
- Markdown 图片描述自动补全
- 按长度、标题、正则、分隔符或智能策略切分文档
- 使用 DashScope 兼容接口生成文本向量
- 将向量和元数据写入 Elasticsearch
- 文档版本上传、激活和切换
- 基于 Spring 事务事件的异步向量化
- 通过 XXL-Job 补偿失败的向量化及旧版本清理任务
- 使用 MySQL 保存文档、版本和分段信息，使用 MinIO 保存原始及转换后的文件

## 处理流程

```text
上传文件
   │
   ├── 计算内容哈希并去重
   ├── 原始文件写入 MinIO
   ├── 文档及版本信息写入 MySQL
   └── 按文件类型转换内容
          │
          ├── PDF ──────> MinerU ──────> Markdown
          ├── Markdown ─> 图片描述增强 ─> Markdown
          └── 其他类型 ─> 保留原始文件
                                  │
                                  v
                           手动触发文档切分
                                  │
                                  v
                         事务提交后异步向量化
                                  │
                    ┌─────────────┴─────────────┐
                    v                           v
                  MySQL                    Elasticsearch
                分段及状态                    文本向量
```

文档搜索类型的典型状态流转如下：

```text
UPLOADED -> CONVERTING -> CONVERTED -> CHUNKED -> VECTOR_STORED
```

无需向量化的数据查询类型使用 `STORED` 状态。

## 技术栈

| 组件 | 用途 |
| --- | --- |
| Java 21 / Spring Boot 3.5.7 | 应用运行环境与 Web 服务 |
| LangChain4j | 文档切分、嵌入模型与向量存储集成 |
| MyBatis-Plus / MySQL | 文档、版本和分段数据持久化 |
| Elasticsearch | 文本向量存储，默认索引为 `know-engine-vector` |
| MinIO | 原始文件、转换结果和解析图片存储 |
| DashScope 兼容 API | 对话模型、视觉模型和 Embedding 模型 |
| MinerU | PDF 文档解析 |
| XXL-Job | 向量化补偿及清理任务 |
| Redis 分布式锁 | 防止同一用户并发上传 |

## 项目结构

```text
KnowEngine/
├── pom.xml
├── src/main/java/com/phuang/
│   ├── KnowEngineApplication.java    # 应用入口
│   ├── config/                       # MySQL、MinIO、ES、异步和 XXL-Job 配置
│   ├── controller/                   # 文档 HTTP 接口
│   ├── handler/
│   │   ├── event/                    # 文档分段完成事件
│   │   ├── job/                      # XXL-Job 补偿任务
│   │   └── splitter/                 # 文档切分器
│   ├── mapper/                       # MyBatis-Plus Mapper
│   ├── model/                        # 实体、DTO、枚举和异常
│   ├── service/                      # 文档、版本、分段和向量服务
│   └── util/                         # 文件识别、MinerU、版本号等工具
└── src/main/resources/
    ├── application.yaml              # 默认配置
    ├── sql/tables.sql                # MySQL 表结构
    └── static/                       # 演示页面
```

## 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8+
- Elasticsearch 8.x
- MinIO
- Redis
- XXL-Job Admin
- 可用的 DashScope API Key
- 可用的 MinerU API Token（处理 PDF 时需要）
- 本地 Maven 仓库中可解析 `com.phuang:custom-annotation-common-start:1.0-SNAPSHOT`

MinerU 和视觉模型需要访问文档或图片 URL，因此 MinIO 返回的地址必须能被对应的外部服务访问。

## 快速开始

### 1. 获取项目并进入模块

```bash
git clone <repository-url>
cd phuang-ai-demo/agent/KnowEngine
```

本模块的 `pom.xml` 继承仓库根目录的父 POM，请保留原有目录层级。

### 2. 初始化数据库

先创建数据库，再执行项目内的表结构脚本：

```sql
CREATE DATABASE know_engine
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u <username> -p know_engine < src/main/resources/sql/tables.sql
```

当前脚本中 `knowledge_document_version` 表的 `COLLATE` 与 `COMMENT` 之间缺少空格。如果 MySQL 报未知排序规则 `utf8mb4_unicode_cicomment`，请先将该处改为 `COLLATE=utf8mb4_unicode_ci COMMENT='文档版本表'` 再执行。

脚本将创建以下数据表：

- `knowledge_document`：文档主记录
- `knowledge_document_version`：文档版本快照
- `knowledge_segment`：文档分段及向量关联信息

### 3. 配置运行环境

不要把真实密钥提交到仓库。推荐新建不纳入版本控制的 `application-local.yaml`，或使用环境变量覆盖默认配置。

本地配置示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/know_engine?useUnicode=true&characterEncoding=UTF-8
    username: your_mysql_user
    password: your_mysql_password

langchain4j:
  open-ai:
    chat-model:
      api-key: your_dashscope_api_key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: deepseek-r1
    streaming-chat-model:
      api-key: your_dashscope_api_key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: deepseek-r1

minio:
  endpoint: http://127.0.0.1:9000
  access-key: your_minio_access_key
  secret-key: your_minio_secret_key
  bucketName: know-engine

mineru:
  base-url: https://mineru.net/api/v4
  api-token: your_mineru_api_token
  model-version: vlm
  markdown-object-prefix: mineru

elasticsearch:
  host: http://127.0.0.1:9200
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model-name: text-embedding-v4
  api-key: your_dashscope_api_key
  dimensions: 1536

xxl:
  job:
    admin:
      addresses: http://127.0.0.1:8080/xxl-job-admin
    accessToken: default_token
    executor:
      appname: know-engine-executor

redis:
  hlock:
    server-type: SINGLE_SERVER
    single-server:
      host: 127.0.0.1
      port: 6379
      password: your_redis_password
```

注意：MinIO 客户端读取的属性名是 `minio.access-key` 和 `minio.secret-key`。如果使用现有默认配置，请确认属性名与代码保持一致。

### 4. 构建和启动

从仓库根目录构建当前模块及其 Maven 依赖：

```bash
mvn -pl agent/KnowEngine -am clean package
```

在 `KnowEngine` 目录使用本地配置启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

也可以运行构建后的 JAR：

```bash
java -jar target/KnowEngine-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

应用启动后访问：

- 上传页面：<http://localhost:10001/upload.html>
- 文档管理页面：<http://localhost:10001/document.html>

`static` 目录还包含登录和对话演示页面，其中部分页面调用的认证、对话、文档查询或分段管理接口不在当前模块内，需要配套服务后端才能完整使用。

## API 示例

服务基础地址默认为 `http://localhost:10001`。

### 上传文档

```bash
curl -X POST 'http://localhost:10001/api/document/upload' \
  -F 'uploadUser=demo-user' \
  -F 'file=@/absolute/path/to/example.pdf' \
  -F 'title=示例文档' \
  -F 'version=1.0.0' \
  -F 'description=用于演示的知识文档' \
  -F 'knowledgeBaseType=DOCUMENT_SEARCH' \
  -F 'accessibleBy=OWNER'
```

`knowledgeBaseType` 可取：

- `DOCUMENT_SEARCH`：文档搜索，需要切分及向量化
- `DATA_QUERY`：结构化数据查询，不执行向量化

`accessibleBy` 必须是 `VISITOR`、`OWNER` 或 `CUSTOMER_SERVICE`。上传文件最大为 `100MB`。Excel/CSV 的数据查询场景还需要传递只含小写字母、数字和下划线的 `tableName`。

### 切分文档

上传和转换完成后，可手动触发切分：

```bash
curl -X POST \
  'http://localhost:10001/api/document/split/1?splitType=SMART&chunkSize=500&overlap=50'
```

切分事务提交后，服务会异步生成向量并写入 Elasticsearch。

| `splitType` | 说明 | 额外参数 |
| --- | --- | --- |
| `LENGTH` | 按词数切分 | `chunkSize`、`overlap` |
| `TITLE` | 按 Markdown 标题层级切分 | `titleLevel`（1-6）、`chunkSize`、`overlap` |
| `REGEX` | 按正则表达式切分 | `regex`、`chunkSize`、`overlap` |
| `SEPARATOR` | 按分隔符切分 | `separator`、`chunkSize`、`overlap` |
| `SMART` | 标题感知切分，并自动使用约 10% 重叠 | `chunkSize` |

### 上传新版本

```bash
curl -X POST 'http://localhost:10001/api/document/upload-version' \
  -F 'uploadUser=demo-user' \
  -F 'docId=1' \
  -F 'version=1.1.0' \
  -F 'changelog=更新产品说明' \
  -F 'file=@/absolute/path/to/example-v1.1.pdf'
```

版本号使用语义化格式，并且必须大于该文档已有的最新版本号。

### 激活版本

```bash
curl -X POST \
  'http://localhost:10001/api/document/activate-version?versionId=2'
```

该接口对 `CHUNKED` 状态的版本重新执行向量化，使其进入 `VECTOR_STORED` 状态。

### 切换版本

```bash
curl -X POST \
  'http://localhost:10001/api/document/switch-version?docId=1&versionId=2'
```

`DATA_QUERY` 类型不支持切换到旧版本。

## 文件类型与当前实现

| 类型 | 识别 | 当前处理方式 |
| --- | --- | --- |
| PDF | 是 | 通过 MinerU 转为 Markdown |
| Markdown (`.md`) | 是 | 读取内容并通过视觉模型增强图片描述 |
| TXT | 是 | 直接保留原始文件，适合纯 UTF-8 文本切分 |
| Word (`.doc/.docx`) | 是 | 尚无专用转换器，不建议直接进入切分流程 |
| Excel (`.xls/.xlsx`) / CSV | 是 | 已提供切分能力；`DATA_QUERY` 入库处理仍在完善中 |

## XXL-Job 任务

在 XXL-Job Admin 中为执行器 `know-engine-executor` 配置以下 JobHandler：

| JobHandler | 作用 |
| --- | --- |
| `documentEmbeddingCompensation` | 扫描停留在 `CHUNKED` 状态的版本并重试向量化 |
| `retryFailedCleanups` | 清理文档版本切换后残留的向量数据 |

## 开发说明

- 文档切分接口不是幂等重建接口；版本已经处于 `CHUNKED` 状态时会直接返回现有分段数量。
- 向量化由异步事务事件触发，接口返回分段数量时向量可能尚未写入完成。
- Elasticsearch 的向量维度必须与所选 Embedding 模型输出维度一致。
- 上传接口通过内容哈希进行跨文档、跨版本去重，相同内容无法重复上传。
- `application.yaml` 中若包含开发环境地址或凭据，请在部署前轮换凭据并改用环境变量或密钥管理服务。

## License

当前仓库尚未声明开源许可证。如需分发或商用，请先确认项目授权方式。
