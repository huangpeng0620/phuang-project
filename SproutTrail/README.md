# SproutTrail

一个基于 Spring Boot、JDK 21、Maven 和 LangChain4j 的轻量文生图应用。

用户输入文字后，后端调用图片模型生成一张 PNG，保存到服务器本地目录，并把图片返回给浏览器预览和下载。
用户也可以先让文本模型给出三条可追加的提示词建议，选择并确认后再生成；原始输入始终保留，用户也可以直接使用原文生成。

## 技术栈

- JDK 21
- Spring Boot 3.5.16
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
export IMAGE_STORAGE_PATH="./data/images"
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

生成的图片默认保存在：

```text
./data/images
```

## Linux 服务器部署

项目以可执行 jar 方式运行，不需要 Docker。服务器安装 JDK 21；在本地完成打包后，将 jar 上传到服务器：

```bash
mvn clean package
scp target/sprouttrail.jar user@your-server:~/sprouttrail.jar
```

SSH 登录服务器，将 jar 放到应用目录，设置 API Key 和图片保存目录后启动：

```bash
mkdir -p "$HOME/sprouttrail/images"
mv "$HOME/sprouttrail.jar" "$HOME/sprouttrail/sprouttrail.jar"
export OPENAI_API_KEY="你的 API Key"
export IMAGE_STORAGE_PATH="$HOME/sprouttrail/images"
java -jar "$HOME/sprouttrail/sprouttrail.jar"
```

应用监听 `8080` 端口。建议使用 Nginx 提供域名与 HTTPS，并限制外部直接访问应用端口：

```nginx
server {
    listen 443 ssl;
    server_name image.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;
    }
}
```

## API

优化接口只返回三个文字补充建议，不会生成图片：

```http
POST /api/prompts/optimize
Content-Type: application/json

{
  "prompt": "清晨森林中的玻璃小屋"
}
```

响应包含 `suggestions` 数组，每一项包含 `title` 和 `supplement`。前端展示建议和完整提示词预览；用户确认后才会调用图片生成接口。

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

成功响应为 `image/png`。失败响应为：

```json
{
  "timestamp": "2026-10-12T10:00:00Z",
  "code": "IMAGE_GENERATION_FAILED",
  "message": "图片生成失败，请稍后重试"
}
```

更完整的实现与部署说明见 [TECHNICAL_PLAN.md](TECHNICAL_PLAN.md)。
