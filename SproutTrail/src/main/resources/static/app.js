const promptInput = document.querySelector("#prompt");
const promptBox = document.querySelector(".prompt-box");
const promptCount = document.querySelector("#prompt-count");
const optimizeButton = document.querySelector("#optimize-button");
const optimizeButtonLabel = optimizeButton.querySelector("span");
const generateButton = document.querySelector("#generate-button");
const generateButtonLabel = generateButton.querySelector("span");
const clearButton = document.querySelector("#clear-prompt");
const exampleButtons = document.querySelectorAll("[data-prompt]");
const aspectRatioInputs = document.querySelectorAll('input[name="aspect-ratio"]');
const form = document.querySelector("#generate-form");
const suggestionSection = document.querySelector("#suggestion-section");
const suggestionStatus = document.querySelector("#suggestion-status");
const suggestionOptions = document.querySelector("#suggestion-options");
const promptPreview = document.querySelector("#prompt-preview");
const finalPrompt = document.querySelector("#final-prompt");
const confirmGenerateButton = document.querySelector("#confirm-generate");
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
let isOptimizing = false;
let hasGeneratedOnce = false;
let suggestions = [];
let selectedSuggestionIndex = -1;
let currentImageUrl = null;

const aspectRatioDetails = Object.freeze({
  AUTO: { label: "自动构图", size: "模型自动选择" },
  SQUARE: { label: "1:1", size: "1024 × 1024" },
  LANDSCAPE: { label: "16:9", size: "1344 × 768" },
  PORTRAIT: { label: "9:16", size: "768 × 1344" },
  LANDSCAPE_4_3: { label: "4:3", size: "1152 × 864" }
});

/** 获取当前选中的服务端画幅枚举值。 */
function selectedAspectRatio() {
  return document.querySelector('input[name="aspect-ratio"]:checked')?.value || "SQUARE";
}

/** 在尚未生成图片时同步右侧的尺寸提示。 */
function syncAspectRatioSummary() {
  if (emptyState.hidden) return;
  const details = aspectRatioDetails[selectedAspectRatio()];
  resultStatus.textContent = `当前选择：${details.label} · ${details.size} PNG`;
}

/**
 * 同步输入计数和按钮状态。提示词只用 trim 判断是否为空，实际请求始终发送原始字符串。
 * 优化或生成请求进行时暂时锁定输入，避免结果对应到已经变化的内容。
 */
function syncPromptState() {
  const rawPrompt = promptInput.value;
  const hasPrompt = rawPrompt.trim().length > 0;
  const isBusy = isGenerating || isOptimizing;

  promptCount.textContent = `${rawPrompt.length} / 800`;
  promptBox.classList.toggle("has-content", rawPrompt.length > 0);
  promptInput.disabled = isBusy;
  clearButton.disabled = isBusy;
  exampleButtons.forEach((button) => {
    button.disabled = isBusy;
  });
  aspectRatioInputs.forEach((input) => {
    input.disabled = isBusy;
  });
  optimizeButton.disabled = isBusy || !hasPrompt;
  generateButton.disabled = isBusy || !hasPrompt;
  confirmGenerateButton.disabled = isBusy || selectedSuggestionIndex < 0;
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
 * 读取后端统一错误响应。上游代理返回 HTML 等非 JSON 内容时使用默认提示。
 */
async function readErrorMessage(response, fallback) {
  try {
    const error = await response.json();
    return error.message || fallback;
  } catch {
    return fallback;
  }
}

/** 清除旧建议，保证编辑过的原文不会继续搭配之前生成的建议。 */
function clearSuggestions() {
  suggestions = [];
  selectedSuggestionIndex = -1;
  suggestionOptions.replaceChildren();
  suggestionStatus.textContent = "";
  suggestionSection.hidden = true;
  promptPreview.hidden = true;
  finalPrompt.textContent = "";
  syncPromptState();
}

/**
 * 根据当前选中的追加内容更新最终提示词预览。
 * 原始输入直接作为前缀拼接，不会被格式化或改写。
 */
function selectSuggestion(index) {
  selectedSuggestionIndex = index;
  const suggestion = suggestions[index];
  if (!suggestion) return;

  const completedPrompt = `${promptInput.value}\n\n${suggestion.supplement}`;
  finalPrompt.textContent = completedPrompt;
  promptPreview.hidden = false;
  suggestionOptions.querySelectorAll(".suggestion-card").forEach((card, cardIndex) => {
    card.classList.toggle("is-selected", cardIndex === index);
  });
  confirmGenerateButton.disabled = isGenerating || isOptimizing;
}

/** 使用 DOM 文本节点展示模型建议，避免将模型返回内容当作 HTML 执行。 */
function renderSuggestions(items) {
  suggestions = items;
  selectedSuggestionIndex = -1;
  suggestionOptions.replaceChildren();

  items.forEach((suggestion, index) => {
    const card = document.createElement("label");
    card.className = "suggestion-card";

    const radio = document.createElement("input");
    radio.type = "radio";
    radio.name = "prompt-suggestion";
    radio.value = String(index);
    radio.setAttribute("aria-label", suggestion.title);
    radio.addEventListener("change", () => selectSuggestion(index));

    const copy = document.createElement("span");
    copy.className = "suggestion-copy";

    const title = document.createElement("strong");
    title.textContent = suggestion.title;

    const supplement = document.createElement("span");
    supplement.textContent = suggestion.supplement;

    copy.append(title, supplement);
    card.append(radio, copy);
    suggestionOptions.append(card);
  });

  suggestionSection.hidden = false;
  promptPreview.hidden = true;
  suggestionStatus.textContent = "选择一条建议查看完整提示词；原文会保持不变。";
}

/**
 * 请求三条文本建议。此处只访问提示词接口，不会调用图片生成接口。
 */
async function optimizePrompt() {
  const originalPrompt = promptInput.value;
  if (!originalPrompt.trim() || isOptimizing || isGenerating) return;

  isOptimizing = true;
  suggestions = [];
  selectedSuggestionIndex = -1;
  optimizeButtonLabel.textContent = "正在生成建议…";
  suggestionSection.hidden = false;
  promptPreview.hidden = true;
  suggestionOptions.replaceChildren();
  suggestionStatus.textContent = "正在根据原文整理三个补充方向…";
  syncPromptState();

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), 135_000);

  try {
    const response = await fetch("/api/prompts/optimize", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt: originalPrompt }),
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response, "提示词优化失败，请稍后重试"));
    }

    const result = await response.json();
    const items = result.suggestions;
    if (!Array.isArray(items) || items.length !== 3 || items.some((item) =>
      typeof item?.title !== "string" || typeof item?.supplement !== "string" ||
      !item.title.trim() || !item.supplement.trim()
    )) {
      throw new Error("优化服务没有返回完整的三条建议，请重试");
    }

    // 输入在请求期间会锁定；此检查也防止将来调整交互时显示过期结果。
    if (promptInput.value !== originalPrompt) return;
    renderSuggestions(items);
  } catch (error) {
    suggestionSection.hidden = false;
    suggestionStatus.textContent = error.name === "AbortError"
      ? "优化等待时间过长，请稍后重试"
      : error.message;
  } finally {
    window.clearTimeout(timeoutId);
    isOptimizing = false;
    optimizeButtonLabel.textContent = "优化提示词";
    syncPromptState();
  }
}

/**
 * 请求生成图片。supplement 为空时直接使用用户原文；有值时由后端原样追加。
 */
async function generateImage(originalPrompt, supplement = "") {
  if (!originalPrompt.trim() || isGenerating || isOptimizing) return;

  const aspectRatio = selectedAspectRatio();
  const ratioDetails = aspectRatioDetails[aspectRatio];
  isGenerating = true;
  generateButtonLabel.textContent = "生成中…";
  confirmGenerateButton.textContent = "正在生成图片…";
  resultStatus.textContent = `正在调用图片模型 · ${ratioDetails.label}`;
  showCanvasState("loading");
  syncPromptState();

  /* 后端图片模型最长等待 120 秒，浏览器额外预留网络传输时间。 */
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), 135_000);

  try {
    const response = await fetch("/api/images/generate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt: originalPrompt, supplement, aspectRatio }),
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response, "图片生成失败，请稍后重试"));
    }

    const imageBlob = await response.blob();
    if (!imageBlob.type.startsWith("image/")) {
      throw new Error("服务器返回的内容不是图片");
    }

    // 替换预览图前释放旧 Blob URL，避免连续生成时占用浏览器内存。
    if (currentImageUrl) {
      URL.revokeObjectURL(currentImageUrl);
    }
    currentImageUrl = URL.createObjectURL(imageBlob);

    const filename = response.headers.get("X-Image-Filename") || "sprouttrail-image.png";
    generatedImage.src = currentImageUrl;
    generatedImage.alt = `根据描述生成的图片：${originalPrompt}`;
    downloadLink.href = currentImageUrl;
    downloadLink.download = filename;
    resultFilename.textContent = filename;
    resultStatus.textContent = `图片已保存 · ${ratioDetails.label} · ${ratioDetails.size} PNG`;
    hasGeneratedOnce = true;
    showCanvasState("result");
  } catch (error) {
    errorMessage.textContent = error.name === "AbortError"
      ? "生成时间过长，请稍后重试"
      : error.message;
    resultStatus.textContent = "本次生成未保存图片";
    showCanvasState("error");
  } finally {
    window.clearTimeout(timeoutId);
    isGenerating = false;
    generateButtonLabel.textContent = hasGeneratedOnce ? "再次使用原文生成" : "使用原文生成";
    confirmGenerateButton.textContent = "确认方案并生成图片";
    syncPromptState();
  }
}

promptInput.addEventListener("input", clearSuggestions);
optimizeButton.addEventListener("click", optimizePrompt);

clearButton.addEventListener("click", () => {
  promptInput.value = "";
  clearSuggestions();
  promptInput.focus();
});

exampleButtons.forEach((button) => {
  button.addEventListener("click", () => {
    promptInput.value = button.dataset.prompt;
    clearSuggestions();
    promptInput.focus();
  });
});

aspectRatioInputs.forEach((input) => {
  input.addEventListener("change", syncAspectRatioSummary);
});

form.addEventListener("submit", (event) => {
  event.preventDefault();
  // 保留 textarea 原值；后端会在原文之后追加用户确认的内容（若有）。
  generateImage(promptInput.value);
});

confirmGenerateButton.addEventListener("click", () => {
  const suggestion = suggestions[selectedSuggestionIndex];
  if (suggestion) {
    generateImage(promptInput.value, suggestion.supplement);
  }
});

/* 页面关闭时释放当前预览使用的 Blob URL，服务器保存的文件不受影响。 */
window.addEventListener("beforeunload", () => {
  if (currentImageUrl) {
    URL.revokeObjectURL(currentImageUrl);
  }
});

syncPromptState();
syncAspectRatioSummary();
