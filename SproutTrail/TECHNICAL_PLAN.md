# SproutTrail 文生图 MVP 实现方案

## 1. 第一版范围

用户输入一段文字后，可以直接用原文生成，也可以先获取三条可追加的文字建议。用户选中并确认后，后端把原文和补充内容组合起来生成图片，并上传到 MinIO。

第一版生成一张 PNG，支持自动、1:1、16:9、9:16 和 4:3 五种画面比例，不提供模型、质量、数量等选项。
图片优化模式支持单张 JPG/PNG 上传和文字编辑要求，一次生成一张 PNG，并提供原图与结果切换。

不做以下功能：登录、历史列表、数据库、Redis、消息队列、多图编辑、局部涂抹、任务轮询、管理后台和付费。

## 2. 技术选型

| 项目 | 选择 |
| --- | --- |
| JDK | JDK 21 |
| 构建 | Maven 3.9+ |
| Web 框架 | Spring Boot 3.5.16、Spring MVC |
| AI 框架 | LangChain4j `1.20.2` |
| Java 样板代码 | Lombok |
| 图片模型接入 | `langchain4j-open-ai` 的 `OpenAiImageModel` |
| 图片编辑 | `QwenImageModel` 实现 LangChain4j `ImageModel`，适配 Qwen 的 JSON 编辑协议 |
| 提示词建议 | `langchain4j-open-ai` 的 `OpenAiChatModel`，只生成文字建议 |
| 页面 | HTML、CSS、原生 JavaScript |
| 图片格式 | PNG |
| 存储 | MinIO |
| 部署 | 可执行 jar，生产环境前置 Nginx |

只引入这些 Maven 依赖：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>1.20.2</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.1</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

不用 LangChain4j 的 Spring Boot starter。图片模型和文本模型分别手工配置成 Bean；提示词建议不使用 AI Services、Memory 或 Tool。

## 3. 整体流程

```text
浏览器输入原始提示词
    ├─ 可选：POST /api/prompts/optimize → 文本模型返回三条追加建议
    │          用户选择建议、编辑补充内容并确认，页面实时预览“原文 + 补充内容”
    └─ POST /api/images/generate（只传原文，或附带已确认的 supplement）
           ↓ Spring MVC 校验请求
ImageService 调用 LangChain4j ImageModel.generate(原文 + 已确认的补充内容)
    ↓
OpenAI 兼容图片接口返回 base64
    ↓
后端解码为 PNG
    ├─ 上传到 MinIO，使用 {uuid}.png 作为对象名
    └─ 将 PNG 二进制返回浏览器
          ↓
页面使用 Blob URL 展示，下载按钮保存到用户电脑
```

第一次生成会同步等待模型返回。图片生成通常比普通 HTTP 请求慢，但 MVP 访问量不高，不需要先上任务队列。

## 4. 项目目录

```text
SproutTrail/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/sprouttrail/image/
    │   │   ├── SproutTrailApplication.java
    │   │   ├── config/ImageModelConfig.java
    │   │   ├── config/ImageProperties.java
    │   │   ├── config/MinioConfig.java
    │   │   ├── controller/ImageController.java
    │   │   ├── controller/PromptController.java
    │   │   ├── controller/ApiExceptionHandler.java
    │   │   ├── exception/ImageWorkflowException.java
    │   │   ├── model/GenerateImageRequest.java
    │   │   ├── model/EditImageRequest.java
    │   │   ├── model/QwenImageModel.java
    │   │   ├── model/ImageAspectRatio.java
    │   │   ├── model/OptimizePromptRequest.java
    │   │   ├── model/OptimizePromptResponse.java
    │   │   ├── model/PromptSuggestion.java
    │   │   ├── model/GeneratedImage.java
    │   │   ├── service/ImageService.java
    │   │   ├── service/MinioService.java
    │   │   └── service/PromptOptimizationService.java
    │   └── resources/
    │       ├── application.yml
    │       └── static/
    │           ├── index.html
    │           ├── styles.css
    │           └── app.js
    └── test/
        └── java/com/sprouttrail/image/
            ├── controller/ImageControllerTest.java
            ├── model/QwenImageModelTest.java
            └── service/ImageServiceTest.java
```

## 5. 配置

`application.yml`：

```yaml
server:
  port: 8080

spring:
  threads:
    virtual:
      enabled: true

app:
  image:
    api-key: ${OPENAI_API_KEY}
    base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
    image-model: ${OPENAI_IMAGE_MODEL:gpt-image-2.5-flare}
    timeout: 120s
```

API Key 只从环境变量读取，不能写进配置文件或前端代码。

`OpenAiImageModelName` 的枚举可能晚于新模型发布，因此配置时使用 `modelName(String)`，直接传入配置中的模型名。

## 6. LangChain4j Bean

`ImageModelConfig` 创建文本模型 Bean，并按用户选择的画面比例缓存图片模型。提示词模型默认使用 `qwen-plus`，复用现有图片接口地址和 `app.image.api-key`。`PROMPT_OPTIMIZATION_MODEL` 与 `PROMPT_OPTIMIZATION_BASE_URL` 绑定到 `ImageProperties`，用于调整文本模型。

图片模型选择逻辑：

```java
public ImageModel imageModel(ImageAspectRatio aspectRatio) {
    return imageModels.computeIfAbsent(aspectRatio, this::createImageModel);
}

private ImageModel createImageModel(ImageAspectRatio aspectRatio) {
    return OpenAiImageModel.builder()
            .apiKey(properties.getApiKey())
            .baseUrl(properties.getBaseUrl())
            .modelName(properties.getImageModel())
            .size(aspectRatio.imageSize())
            .timeout(properties.getTimeout())
            .maxRetries(1)
            .build();
}
```

`ImageProperties` 使用 `@ConfigurationProperties("app.image")` 映射配置。提示词优化使用 LangChain4j `ChatModel` 直接发送一条请求并解析 JSON，不使用 Memory、Tool 或 AI Service。

## 7. 接口设计

### 生成图片

```http
POST /api/images/generate
Content-Type: application/json

{
  "prompt": "清晨森林中的玻璃小屋，电影感，写实风格",
  "supplement": "柔和的晨光穿过树叶",
  "aspectRatio": "LANDSCAPE"
}
```

请求 DTO：

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageRequest {
    @NotBlank(message = "请输入图片描述")
    @Size(max = 800, message = "图片描述不能超过 800 个字符")
    private String prompt;

    @Size(max = 1200, message = "提示词补充内容不能超过 1200 个字符")
    private String supplement;

    private ImageAspectRatio aspectRatio;
}
```

`aspectRatio` 可取 `AUTO`、`SQUARE`、`LANDSCAPE`、`PORTRAIT`、`LANDSCAPE_4_3`。未传时默认使用 `SQUARE`，兼容旧版请求。

成功时直接返回图片：

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Disposition: inline; filename="b5fd...png"
Cache-Control: no-store
X-Image-Filename: b5fd...png

<PNG binary>
```

失败时返回统一 JSON：

```json
{
  "code": "IMAGE_GENERATION_FAILED",
  "message": "图片生成失败，请稍后重试"
}
```

一个 POST 同时完成生成、服务器保存和向浏览器返回图片，不需要额外的任务状态接口或图片查询接口。

### 提示词建议

```http
POST /api/prompts/optimize
Content-Type: application/json

{"prompt":"清晨森林中的玻璃小屋"}
```

返回恰好三条 `{ "title", "supplement" }` 建议。模型只提供可追加内容；页面以只读方式保留原始输入，用户选择建议后可以编辑补充内容，并实时预览最终提示词，确认按钮才会调用图片生成接口。

### 图片优化

`POST /api/images/edit` 接收 multipart 表单字段 `image`、`prompt`、`aspectRatio`。未传比例时使用 `AUTO`。

1. 校验原图真实格式、大小、尺寸及可解码性，仅接受 JPG/PNG、5MB 以内、1600 万像素以内，宽高各不超过 8000 且长短边比例不超过 10。
2. 将原始字节转为 Base64 data URI，编辑要求保持原样。
3. `QwenImageModel.edit()` 将图片放入 `/images/generations` 的 JSON `image` 字段，设置 `n=1` 和 `prompt_extend=false`。不使用 OpenAI 的 `/images/edits` 协议。
4. 使用现有的图片下载、UUID 命名和 MinIO 上传逻辑返回 PNG。原图不永久保存，multipart 临时文件由框架清理。

模型编辑请求不自动重试，避免重复提交付费任务。错误继续使用 `ImageWorkflowException`；上传超限返回 413，非法图片或表单返回 400，模型或结果下载失败返回 502。

## 8. 上传图片到 MinIO

`ImageService` 的职责：

1. 根据画面比例获取缓存的图片模型并调用 `imageModel.generate(prompt)`；原始输入及用户确认的补充内容不做 trim 或改写。
2. 读取 `response.content().base64Data()`。
3. 使用 `Base64.getDecoder().decode(...)` 转成字节。
4. 使用 `UUID.randomUUID() + ".png"` 作为 MinIO 对象名。
5. 调用 `MinioService.uploadFile(objectName, bytes, "image/png")` 上传图片。
6. 返回图片字节和对象名给 Controller，供页面预览和下载。

返回一个简单的只读 class：

```java
@Getter
@AllArgsConstructor
public class GeneratedImage {
    private final String filename;
    private final byte[] bytes;
}
```

不使用用户输入作为文件名，避免非法字符与路径穿越问题。

## 9. 前端实现

页面使用轻量双栏布局，包含：

- prompt 文本框
- 自动、1:1、16:9、9:16 和 4:3 画面比例选项
- “优化提示词”和“使用原文生成”按钮
- 三条补充建议、选择状态、可编辑的补充内容、最终提示词预览和确认生成按钮
- 生成中状态
- 图片预览和下载按钮
- 文字生成 / 图片优化模式切换；单张原图上传、替换与移除；原图 / 优化结果切换

用户编辑原文后会清除之前的建议，避免过期建议用于新提示词。选择建议后，原文只读展示，补充内容允许二次编辑；编辑结果会实时进入最终提示词预览并原样提交。

请求逻辑：

```javascript
const response = await fetch("/api/images/generate", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ prompt, supplement, aspectRatio })
});

if (!response.ok) {
  const error = await response.json();
  throw new Error(error.message);
}

const blob = await response.blob();
const imageUrl = URL.createObjectURL(blob);
preview.src = imageUrl;
download.href = imageUrl;
download.download = response.headers.get("X-Image-Filename") ?? "image.png";
```

开始生成后禁用按钮，完成或失败后恢复。再次生成时先调用 `URL.revokeObjectURL` 释放上一张预览。

## 10. 异常与边界

- prompt 为空或超过 800 字符：返回 HTTP 400。
- 补充内容为空时沿用旧请求行为；补充内容最多 1200 字符。
- 画面比例为空时默认使用 1:1；不接受预定义枚举之外的任意尺寸。
- 提示词优化模型失败时返回 HTTP 502，页面仍可直接使用原文生成。
- AI 服务超时、限流或返回异常：记录 requestId 和异常类型，返回 HTTP 502。
- 本地磁盘写入失败：返回 HTTP 500。
- 日志不记录 API Key、base64 数据和完整图片。
- 第一版允许同步等待，Nginx 的 `proxy_read_timeout` 设置为 300 秒，上传请求上限设为 6MB。
- 服务器目录会持续增长，第一版先人工清理；正式开放给大量用户前再增加定时清理策略。

## 11. 最小测试

自动化测试使用模拟 HTTP 客户端或模型，不访问真实 AI 接口：

1. 检查 Qwen 图片编辑实际请求地址、Base64 data URI、原样提示词与关闭自动改写的参数。
2. 校验伪造图片、超限文件与极端长宽比，验证生成结果落盘且原图不被保存。
3. 使用 MockMvc 验证 multipart 表单绑定、默认自动比例及统一错误响应。

真实 AI 调用不放进自动化测试，开发时使用一个实际 prompt 做一次冒烟验证即可。

## 12. 打包与部署

服务器安装 JDK 21。本地使用 Maven 打包，并将 jar 上传到服务器：

```bash
mvn clean package
scp target/sprouttrail.jar user@your-server:~/sprouttrail.jar
```

SSH 登录服务器，将 jar 放到应用目录，配置 API Key 和 MinIO 连接信息后启动应用：

```bash
mkdir -p "$HOME/sprouttrail"
mv "$HOME/sprouttrail.jar" "$HOME/sprouttrail/sprouttrail.jar"
export OPENAI_API_KEY="你的 API Key"
java -jar "$HOME/sprouttrail/sprouttrail.jar"
```

应用监听 `8080` 端口。生产环境可使用 Nginx 负责域名、HTTPS 和反向代理：

```nginx
location / {
    client_max_body_size 6m;
    proxy_pass http://127.0.0.1:8080;
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
}
```

## 13. 开发顺序

1. 创建 Spring Boot Maven 项目和配置。
2. 把页面原型放进 `static/`，删除比例选择等非 MVP 选项。
3. 配置 LangChain4j `ImageModel` Bean。
4. 完成 `ImageService` 的生成、解码和文件保存。
5. 完成 Controller、参数校验和异常处理。
6. 前端接入真实接口与下载功能。
7. 完成两个最小测试和一次真实 API 冒烟测试。
8. Maven 打包，将可执行 jar 部署到测试服务器。
