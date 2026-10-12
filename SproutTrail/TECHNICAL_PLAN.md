# SproutTrail 文生图 MVP 实现方案

## 1. 第一版范围

用户输入一段文字，点击生成，页面展示图片，并允许下载。后端同时把图片保存到服务器本地目录。

第一版固定生成一张 `1024x1024` PNG，不提供模型、尺寸、质量、数量等选项。

不做以下功能：登录、历史列表、数据库、Redis、消息队列、对象存储、多模型切换、图片编辑、任务轮询、管理后台和付费。

## 2. 技术选型

| 项目 | 选择 |
| --- | --- |
| JDK | JDK 21 |
| 构建 | Maven 3.9+ |
| Web 框架 | Spring Boot 3.5.16、Spring MVC |
| AI 框架 | LangChain4j `1.20.2` |
| 图片模型接入 | `langchain4j-open-ai` 的 `OpenAiImageModel` |
| 页面 | HTML、CSS、原生 JavaScript |
| 图片格式 | PNG |
| 存储 | 服务器本地目录 |
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
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

不用 LangChain4j 的 Spring Boot starter。图片模型手工配置成一个 Bean 更直接，也不需要引入 AI Services。

## 3. 整体流程

```text
浏览器输入文字
    ↓ POST /api/images/generate
Spring MVC 校验 prompt
    ↓
ImageService 调用 LangChain4j ImageModel.generate(prompt)
    ↓
OpenAI 兼容图片接口返回 base64
    ↓
后端解码为 PNG
    ├─ 保存到服务器 ./data/images/{uuid}.png
    └─ 将 PNG 二进制返回浏览器
          ↓
页面使用 Blob URL 展示，下载按钮保存到用户电脑
```

第一次生成会同步等待模型返回。图片生成通常比普通 HTTP 请求慢，但 MVP 访问量不高，不需要先上任务队列。

## 4. 项目目录

```text
SproutTrail/
├── pom.xml
├── data/images/                    # 运行时生成，不提交 Git
└── src/
    ├── main/
    │   ├── java/com/sprouttrail/image/
    │   │   ├── SproutTrailApplication.java
    │   │   ├── config/ImageModelConfig.java
    │   │   ├── config/ImageProperties.java
    │   │   ├── controller/ImageController.java
    │   │   ├── controller/ApiExceptionHandler.java
    │   │   ├── model/GenerateImageRequest.java
    │   │   ├── model/GeneratedImage.java
    │   │   └── service/ImageService.java
    │   └── resources/
    │       ├── application.yml
    │       └── static/
    │           ├── index.html
    │           ├── styles.css
    │           └── app.js
    └── test/
        └── java/com/sprouttrail/image/service/ImageServiceTest.java
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
    model: ${OPENAI_IMAGE_MODEL:gpt-image-2.5-flare}
    size: 1024x1024
    quality: medium
    storage-path: ${IMAGE_STORAGE_PATH:./data/images}
    timeout: 120s
```

API Key 只从环境变量读取，不能写进配置文件或前端代码。

`OpenAiImageModelName` 的枚举可能晚于新模型发布，因此配置时使用 `modelName(String)`，直接传入配置中的模型名。

## 6. LangChain4j Bean

`ImageModelConfig` 只创建一个 Bean：

```java
@Bean
ImageModel imageModel(ImageProperties properties) {
    return OpenAiImageModel.builder()
            .apiKey(properties.apiKey())
            .baseUrl(properties.baseUrl())
            .modelName(properties.model())
            .size(properties.size())
            .quality(properties.quality())
            .outputFormat("png")
            .timeout(properties.timeout())
            .maxRetries(1)
            .build();
}
```

`ImageProperties` 使用 `@ConfigurationProperties("app.image")` 映射配置，并在配置类上用 `@EnableConfigurationProperties(ImageProperties.class)` 启用。这里不需要聊天模型、PromptTemplate、Memory、Tool 或 AI Service。

## 7. 接口设计

### 生成图片

```http
POST /api/images/generate
Content-Type: application/json

{
  "prompt": "清晨森林中的玻璃小屋，电影感，写实风格"
}
```

请求 DTO：

```java
public record GenerateImageRequest(
        @NotBlank(message = "请输入图片描述")
        @Size(max = 800, message = "图片描述不能超过 800 个字符")
        String prompt
) {}
```

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

## 8. 保存图片

`ImageService` 的职责：

1. 调用 `imageModel.generate(prompt.trim())`。
2. 读取 `response.content().base64Data()`。
3. 使用 `Base64.getDecoder().decode(...)` 转成字节。
4. 确保 `${IMAGE_STORAGE_PATH}` 目录存在。
5. 使用 `UUID.randomUUID() + ".png"` 作为文件名。
6. 使用 `Files.write(...)` 保存图片。
7. 返回图片字节和文件名给 Controller。

建议返回一个简单 record：

```java
public record GeneratedImage(String filename, byte[] bytes) {}
```

不使用用户输入作为文件名，避免非法字符与路径穿越问题。

## 9. 前端实现

页面只保留四个核心元素：

- prompt 文本框
- 生成按钮
- 生成中状态
- 图片预览和下载按钮

请求逻辑：

```javascript
const response = await fetch("/api/images/generate", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ prompt })
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
- AI 服务超时、限流或返回异常：记录 requestId 和异常类型，返回 HTTP 502。
- 本地磁盘写入失败：返回 HTTP 500。
- 日志不记录 API Key、base64 数据和完整图片。
- 第一版允许同步等待，Nginx 的 `proxy_read_timeout` 设置为 180 秒。
- 服务器目录会持续增长，第一版先人工清理；正式开放给大量用户前再增加定时清理策略。

## 11. 最小测试

只写两类有价值的测试：

1. prompt 为空或超长时，Controller 返回 400。
2. mock `ImageModel` 返回一段 base64 后，`ImageService` 能生成 PNG 文件并返回相同字节。

真实 AI 调用不放进自动化测试，开发时使用一个实际 prompt 做一次冒烟验证即可。

## 12. 打包与部署

服务器安装 JDK 21。本地使用 Maven 打包，并将 jar 上传到服务器：

```bash
mvn clean package
scp target/sprouttrail.jar user@your-server:~/sprouttrail.jar
```

SSH 登录服务器，将 jar 放到应用目录，配置 API Key 和图片保存目录后启动应用：

```bash
mkdir -p "$HOME/sprouttrail/images"
mv "$HOME/sprouttrail.jar" "$HOME/sprouttrail/sprouttrail.jar"
export OPENAI_API_KEY="你的 API Key"
export IMAGE_STORAGE_PATH="$HOME/sprouttrail/images"
java -jar "$HOME/sprouttrail/sprouttrail.jar"
```

应用监听 `8080` 端口。生产环境可使用 Nginx 负责域名、HTTPS 和反向代理：

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_read_timeout 180s;
    proxy_send_timeout 180s;
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
