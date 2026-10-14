# SproutTrail

一个基于 Spring Boot、JDK 21、Maven 和 LangChain4j 的轻量文生图应用。

用户输入文字后，后端调用图片模型生成一张 PNG，上传到 MinIO，并把图片返回给浏览器预览和下载。
用户也可以先让文本模型给出三条可追加的提示词建议，选择并确认后再生成；原始输入始终保留，用户也可以直接使用原文生成。
文生图成功后，应用会把原始提示词、最终提示词、MinIO 图片地址、画幅和生成时间保存到 MySQL 的 `image_generation_record` 表。
切换到“图片优化”后，可以上传单张 JPG/PNG、输入编辑要求并生成新图片；支持原图与结果切换对比和下载。

## 技术栈

- JDK 21
- Spring Boot 3.5.16
- MySQL
- MyBatis-Plus
- Maven
- Lombok
- LangChain4j 1.20.2
- HTML、CSS、原生 JavaScript

## 运行前准备

配置图片模型 API Key：

```bash
export OPENAI_API_KEY="你的 API Key"
```

可选配置：

```bash
export OPENAI_BASE_URL="https://api.openai.com/v1"
export OPENAI_IMAGE_MODEL="gpt-image-2.5-flare"
```

提示词优化默认使用 `qwen-plus`，复用图片模型配置的兼容接口地址和 API Key。若工作空间没有开通该文本模型，或需要指定其他兼容地址，可设置：

```bash
export PROMPT_OPTIMIZATION_MODEL="qwen-plus"
export PROMPT_OPTIMIZATION_BASE_URL="你的文本模型兼容接口地址"
```

这两个值会绑定到 `ImageProperties` 的 `promptModel` 和 `promptBaseUrl`；接口地址留空时沿用图片模型地址。

## 本地运行

本机需要 JDK 21 和 Maven 3.9+：

```bash
mvn clean package
java -jar target/sprouttrail.jar
```

浏览器访问：

```text
http://localhost:8080
```

生成结果会上传到 `application.yml` 中配置的 MinIO bucket。
MySQL 连接配置位于 `application.yml` 的 `spring.datasource` 下；应用启动时会自动创建缺少的记录表。

## Linux 服务器部署

项目以可执行 jar 方式运行，不需要 Docker。服务器安装 JDK 21；在本地完成打包后，将 jar 上传到服务器：

```bash
mvn clean package
scp target/sprouttrail.jar user@your-server:~/sprouttrail.jar
```

SSH 登录服务器，将 jar 放到应用目录，确认 API Key 和 MinIO 连接配置后启动：

```bash
mkdir -p "$HOME/sprouttrail"
mv "$HOME/sprouttrail.jar" "$HOME/sprouttrail/sprouttrail.jar"
export OPENAI_API_KEY="你的 API Key"
java -jar "$HOME/sprouttrail/sprouttrail.jar"
```

应用监听 `8080` 端口。建议使用 Nginx 提供域名与 HTTPS，并限制外部直接访问应用端口：

```nginx
server {
    listen 443 ssl;
    server_name image.example.com;
    # 单张原图最多 5MB，为 multipart 表单字段预留空间。
    client_max_body_size 6m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

## API

### 用户登录

启动后访问 `http://localhost:8080/login.html`，使用数据库中已创建的用户名和密码登录。当前版本不提供注册接口；`schema.sql` 会创建 `user` 表，需由部署者为该表添加至少一个启用用户，`password_hash` 必须是 BCrypt 哈希。

登录相关接口如下：

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "your-username",
  "password": "your-password"
}
```

成功响应包含 `tokenName`、`tokenValue`、用户 ID 和 token 有效期。后续请求需要在 `satoken` 请求头中携带 `tokenValue`。`POST /api/auth/logout` 注销当前会话；`GET /api/auth/token` 查询当前 token 和用户信息。图片生成、图片优化及提示词优化接口都要求登录。

优化接口只返回三个文字补充建议，不会生成图片：

```http
POST /api/prompts/optimize
Content-Type: application/json

{
  "prompt": "清晨森林中的玻璃小屋"
}
```

响应包含 `suggestions` 数组，每一项包含 `title` 和 `supplement`。用户选择建议后，可以继续编辑补充内容；原始提示词保持只读，页面会实时展示完整提示词，确认后才会调用图片生成接口。

生成接口兼容只传 `prompt` 的旧请求。确认补充建议时，额外传入 `supplement`，服务端会将它追加到原文之后。`aspectRatio` 用于选择画面比例：

```http
POST /api/images/generate
Content-Type: application/json

{
  "prompt": "清晨森林中的玻璃小屋，电影感，写实风格",
  "supplement": "柔和的金色晨光，细腻的电影构图",
  "aspectRatio": "LANDSCAPE"
}
```

画面比例可取 `AUTO`、`SQUARE`、`LANDSCAPE`、`PORTRAIT`、`LANDSCAPE_4_3`；不传时默认使用 `SQUARE`。

### 图片优化

图片优化复用当前 `qwen-image-3.0` 配置。上传和编辑要求在一次请求中提交，不需要先上传到 OSS：

```bash
curl http://localhost:8080/api/images/edit \
  -F 'image=@/你的路径/original.png' \
  -F 'prompt=保留狗、树和骨头及原有动作关系，改善光线，突出毛发细节' \
  -F 'aspectRatio=AUTO' \
  --output edited.png
```

- 仅支持 JPG/PNG，单张不超过 5MB；宽高各不超过 8000 像素、总像素不超过 1600 万、长边不超过短边的 10 倍。
- 编辑要求必填，最多 800 字符；比例未传时默认 `AUTO`，其余值与文生图一致。
- 编辑要求原样提交，显式关闭模型侧的提示词改写（`prompt_extend=false`）。
- 上传原图不长期保存，仅持久保存生成结果；页面可以切换原图与优化结果，下载按钮始终保存生成结果。
- `QwenImageModel` 实现 LangChain4j `ImageModel`，使用 `/images/generations` 的 JSON `image` 扩展字段进行编辑；文生图仍委托 `OpenAiImageModel`。
- 模型编辑请求不自动重试；结果不是逐像素修图，未指定的细节仍可能变化。

协议说明见 [阿里云 Qwen 图片生成与编辑文档](https://help.aliyun.com/zh/model-studio/qwen-image-generation-and-editing-api-reference)。

### 响应

成功响应为 `image/png`。失败响应为：

```json
{
  "timestamp": "2026-10-12T10:00:00Z",
  "code": "IMAGE_GENERATION_FAILED",
  "message": "图片生成失败，请稍后重试"
}
```

更完整的实现与部署说明见 [TECHNICAL_PLAN.md](TECHNICAL_PLAN.md)。
