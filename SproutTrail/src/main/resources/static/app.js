const promptInput = document.querySelector("#prompt");
const promptBox = document.querySelector(".prompt-box");
const promptCount = document.querySelector("#prompt-count");
const generateButton = document.querySelector("#generate-button");
const generateButtonLabel = generateButton.querySelector("span");
const form = document.querySelector("#generate-form");
const emptyState = document.querySelector("#empty-state");
const loadingState = document.querySelector("#loading-state");
const errorState = document.querySelector("#error-state");
const errorMessage = document.querySelector("#error-message");
const resultFigure = document.querySelector("#result-figure");
const generatedImage = document.querySelector("#generated-image");
const resultActions = document.querySelector("#result-actions");
const resultFilename = document.querySelector("#result-filename");
const resultStatus = document.querySelector("#result-status");
const downloadLink = document.querySelector("#download-link");

let isGenerating = false;
let currentImageUrl = null;

/**
 * 同步输入框计数、清空按钮和生成按钮状态。
 *
 * 生成请求进行中时保持按钮禁用，防止用户重复提交并产生多次计费。
 */
function syncPromptState() {
  const length = promptInput.value.length;
  promptCount.textContent = `${length} / 800`;
  promptBox.classList.toggle("has-content", length > 0);
  generateButton.disabled = isGenerating || promptInput.value.trim().length === 0;
}

/**
 * 在空状态、加载状态、错误状态和结果状态之间进行唯一切换。
 *
 * @param {"empty"|"loading"|"error"|"result"} state 要显示的状态
 */
function showCanvasState(state) {
  emptyState.hidden = state !== "empty";
  loadingState.hidden = state !== "loading";
  errorState.hidden = state !== "error";
  resultFigure.hidden = state !== "result";
  resultActions.hidden = state !== "result";
}

/**
 * 读取后端统一错误响应。网络代理返回 HTML 等非 JSON 内容时使用默认提示，
 * 避免 JSON 解析异常覆盖真正需要展示给用户的信息。
 */
async function readErrorMessage(response) {
  try {
    const error = await response.json();
    return error.message || "图片生成失败，请稍后重试";
  } catch {
    return "图片生成失败，请稍后重试";
  }
}

promptInput.addEventListener("input", syncPromptState);

document.querySelector("#clear-prompt").addEventListener("click", () => {
  promptInput.value = "";
  syncPromptState();
  promptInput.focus();
});

document.querySelectorAll("[data-prompt]").forEach((button) => {
  button.addEventListener("click", () => {
    promptInput.value = button.dataset.prompt;
    syncPromptState();
    promptInput.focus();
  });
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const prompt = promptInput.value.trim();
  if (!prompt || isGenerating) return;

  isGenerating = true;
  generateButtonLabel.textContent = "生成中…";
  resultStatus.textContent = "正在调用图片模型";
  showCanvasState("loading");
  syncPromptState();

  /*
   * 后端超时为 120 秒，浏览器多预留 15 秒用于网络传输。
   * 超时后主动中止 fetch，避免页面一直停留在加载状态。
   */
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), 135_000);

  try {
    const response = await fetch("/api/images/generate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt }),
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }

    const imageBlob = await response.blob();
    if (!imageBlob.type.startsWith("image/")) {
      throw new Error("服务器返回的内容不是图片");
    }

    /*
     * Blob URL 只存在于当前浏览器页面。展示下一张图片前必须释放上一张，
     * 否则用户连续生成图片时浏览器会一直占用旧图片的内存。
     */
    if (currentImageUrl) {
      URL.revokeObjectURL(currentImageUrl);
    }
    currentImageUrl = URL.createObjectURL(imageBlob);

    const filename = response.headers.get("X-Image-Filename") || "sprouttrail-image.png";
    generatedImage.src = currentImageUrl;
    generatedImage.alt = `根据描述生成的图片：${prompt}`;
    downloadLink.href = currentImageUrl;
    downloadLink.download = filename;
    resultFilename.textContent = filename;
    resultStatus.textContent = "图片已保存到服务器，可下载到当前设备";
    showCanvasState("result");
  } catch (error) {
    const message = error.name === "AbortError"
      ? "生成时间过长，请稍后重试"
      : error.message;
    errorMessage.textContent = message;
    resultStatus.textContent = "本次生成未保存图片";
    showCanvasState("error");
  } finally {
    window.clearTimeout(timeoutId);
    isGenerating = false;
    generateButtonLabel.textContent = "再次生成";
    syncPromptState();
  }
});

/*
 * 页面关闭时释放最后一个 Blob URL。服务器上已经保存的文件不会受到影响。
 */
window.addEventListener("beforeunload", () => {
  if (currentImageUrl) {
    URL.revokeObjectURL(currentImageUrl);
  }
});

syncPromptState();
