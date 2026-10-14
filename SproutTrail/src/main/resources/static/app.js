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
const originalPromptPreview = document.querySelector("#original-prompt-preview");
const supplementEditor = document.querySelector("#supplement-editor");
const supplementCount = document.querySelector("#supplement-count");
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
const modeButtons = document.querySelectorAll("[data-mode]");
const composerTitle = document.querySelector("#composer-title");
const composerIntro = document.querySelector("#composer-intro");
const promptLabel = document.querySelector("#prompt-label");
const promptExamples = document.querySelector("#prompt-examples");
const actionRow = document.querySelector("#action-row");
const formNote = document.querySelector("#form-note");
const uploadSection = document.querySelector("#upload-section");
const uploadInput = document.querySelector("#image-upload");
const uploadArea = document.querySelector("#upload-area");
const uploadDropZone = document.querySelector("#upload-drop-zone");
const uploadPreview = document.querySelector("#upload-preview");
const uploadedImage = document.querySelector("#uploaded-image");
const uploadFilename = document.querySelector("#upload-filename");
const uploadDetails = document.querySelector("#upload-details");
const uploadMessage = document.querySelector("#upload-message");
const replaceImageButton = document.querySelector("#replace-image");
const removeImageButton = document.querySelector("#remove-image");
const comparisonSwitch = document.querySelector("#comparison-switch");
const comparisonButtons = document.querySelectorAll("[data-view]");
const resultCaption = document.querySelector("#result-caption");
const emptyTitle = document.querySelector("#empty-title");
const emptyDescription = document.querySelector("#empty-description");
const loadingTitle = document.querySelector("#loading-title");

let isGenerating = false;
let isOptimizing = false;
let hasGeneratedOnce = false;
let suggestions = [];
let selectedSuggestionIndex = -1;
let currentImageUrl = null;
let workflowMode = "GENERATE";
let isCheckingUpload = false;
let uploadedFile = null;
let uploadedImageUrl = null;
let currentResultFilename = "";
let resultDetails = "";

// 切换模式时保留各自的输入草稿与比例，防止误覆盖另一种创作方式的要求。
const promptDrafts = { GENERATE: "", EDIT: "" };
const ratioDrafts = { GENERATE: "SQUARE", EDIT: "AUTO" };
const generationPlaceholder = promptInput.placeholder;

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
  const isBusy = isGenerating || isOptimizing || isCheckingUpload;

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
  generateButton.disabled = isBusy || !hasPrompt || (workflowMode === "EDIT" && !uploadedFile);
  modeButtons.forEach((button) => { button.disabled = isBusy; });
  uploadInput.disabled = isBusy;
  uploadDropZone.setAttribute("aria-disabled", String(isBusy));
  uploadDropZone.tabIndex = isBusy ? -1 : 0;
  replaceImageButton.disabled = isBusy;
  removeImageButton.disabled = isBusy;
  suggestionOptions.querySelectorAll("input").forEach((input) => { input.disabled = isBusy; });
  supplementEditor.disabled = isBusy || selectedSuggestionIndex < 0;
  confirmGenerateButton.disabled = isBusy
    || selectedSuggestionIndex < 0
    || !supplementEditor.value.trim();
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
  comparisonSwitch.hidden = state !== "result" || workflowMode !== "EDIT" || !uploadedImageUrl;
}

/** 释放结果预览；替换原图或切换模式时，避免把旧结果当成本次图片的优化结果。 */
function clearResult() {
  if (currentImageUrl) URL.revokeObjectURL(currentImageUrl);
  currentImageUrl = null;
  currentResultFilename = "";
  resultDetails = "";
  hasGeneratedOnce = false;
  generatedImage.removeAttribute("src");
  downloadLink.removeAttribute("href");
  showCanvasState("empty");
  syncAspectRatioSummary();
}

/** 两种模式复用同一表单和结果区，编辑模式使用独立的输入草稿。 */
function switchMode(mode) {
  if (mode === workflowMode || isGenerating || isOptimizing || isCheckingUpload) return;
  promptDrafts[workflowMode] = promptInput.value;
  ratioDrafts[workflowMode] = selectedAspectRatio();
  workflowMode = mode;
  const isEditing = mode === "EDIT";
  promptInput.value = promptDrafts[mode];
  aspectRatioInputs.forEach((input) => { input.checked = input.value === ratioDrafts[mode]; });
  modeButtons.forEach((button) => {
    button.setAttribute("aria-pressed", String(button.dataset.mode === mode));
  });
  composerTitle.replaceChildren(
    isEditing ? "让已有的画面，" : "把脑海里的画面，",
    document.createElement("br"),
    isEditing ? "更进一步。" : "描述出来。"
  );
  composerIntro.textContent = isEditing
    ? "上传一张图片，说明想修改的部分和希望保留的细节。"
    : "写下主体、场景、风格和光线，我们会生成一张图片。";
  promptLabel.textContent = isEditing ? "图片编辑要求" : "画面描述";
  promptInput.placeholder = isEditing
    ? "例如：保留原图中的宠物狗、树和骨头，以及狗站在树前啃骨头的动作，改善光线，突出毛发细节……"
    : generationPlaceholder;
  uploadSection.hidden = !isEditing;
  promptExamples.hidden = isEditing;
  optimizeButton.hidden = isEditing;
  actionRow.classList.toggle("single-action", isEditing);
  generateButtonLabel.textContent = isEditing ? "优化图片" : "使用原文生成";
  formNote.textContent = isEditing
    ? "编辑要求将原样提交；优化结果会保存，可与原图切换对比。"
    : "原文会保持不变；优化建议需由你选择并确认后才会用于生成";
  emptyTitle.textContent = isEditing ? "等待你的原图" : "等待你的灵感";
  emptyDescription.textContent = isEditing
    ? "上传原图并输入编辑要求，优化结果会出现在这里"
    : "输入画面描述，生成的图片会出现在这里";
  clearSuggestions();
  clearResult();
}

/** 浏览器先检查大小和可读取性；后端仍独立校验真实格式及尺寸。 */
async function setUploadedImage(file) {
  if (!file || isGenerating || isOptimizing || isCheckingUpload) return;
  let previewUrl = null;
  isCheckingUpload = true;
  uploadMessage.textContent = "正在读取图片…";
  syncPromptState();
  try {
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      throw new Error("仅支持 JPG 和 PNG 图片");
    }
    if (file.size === 0 || file.size > 5 * 1024 * 1024) {
      throw new Error("请选择非空且不超过 5MB 的图片");
    }
    previewUrl = URL.createObjectURL(file);
    const image = new Image();
    image.src = previewUrl;
    await image.decode();
    const width = image.naturalWidth;
    const height = image.naturalHeight;
    if (width > 8000 || height > 8000 || width * height > 16_000_000) {
      throw new Error("图片宽高不能超过 8000 像素，总像素不能超过 1600 万");
    }
    if (Math.max(width, height) > 10 * Math.min(width, height)) {
      throw new Error("原图长边不能超过短边的 10 倍");
    }
    clearResult();
    if (uploadedImageUrl) URL.revokeObjectURL(uploadedImageUrl);
    uploadedFile = file;
    uploadedImageUrl = previewUrl;
    previewUrl = null;
    uploadedImage.src = uploadedImageUrl;
    uploadFilename.textContent = file.name;
    uploadDetails.textContent = `${width} × ${height} · ${(file.size / 1024 / 1024).toFixed(2)} MB`;
    uploadPreview.hidden = false;
    uploadDropZone.hidden = true;
    uploadMessage.textContent = "";
    generateButtonLabel.textContent = "优化图片";
  } catch (error) {
    uploadMessage.textContent = error.name === "EncodingError"
      ? "图片无法读取，请重新选择有效的 JPG 或 PNG 图片"
      : error.message;
  } finally {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    uploadInput.value = "";
    isCheckingUpload = false;
    syncPromptState();
  }
}

/** 结果下载始终指向优化结果，原图切换仅改变右侧预览。 */
function displayResult(view = "result") {
  const isOriginal = view === "original" && uploadedImageUrl;
  generatedImage.src = isOriginal ? uploadedImageUrl : currentImageUrl;
  generatedImage.alt = isOriginal ? "上传的原图" : "AI 生成的结果图片";
  resultCaption.textContent = isOriginal ? "原图" : "已保存";
  resultFilename.textContent = isOriginal ? uploadedFile.name : currentResultFilename;
  resultStatus.textContent = isOriginal ? "正在查看原图 · 下载按钮保存优化结果" : resultDetails;
  comparisonButtons.forEach((button) => {
    button.setAttribute("aria-pressed", String(button.dataset.view === (isOriginal ? "original" : "result")));
  });
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
  originalPromptPreview.textContent = "";
  supplementEditor.value = "";
  supplementCount.textContent = "0 / 1200";
  finalPrompt.textContent = "";
  syncPromptState();
}

/**
 * 根据用户正在编辑的补充内容更新最终提示词预览。
 * 原始输入始终作为只读前缀展示和提交，不会被格式化或改写。
 */
function updatePromptPreview() {
  const editedSupplement = supplementEditor.value;
  supplementCount.textContent = `${editedSupplement.length} / 1200`;
  finalPrompt.textContent = editedSupplement.trim()
    ? `${promptInput.value}\n\n${editedSupplement}`
    : promptInput.value;
  syncPromptState();
}

/** 选中建议后，将其补充内容放入编辑框供用户二次调整。 */
function selectSuggestion(index) {
  selectedSuggestionIndex = index;
  const suggestion = suggestions[index];
  if (!suggestion) return;

  originalPromptPreview.textContent = promptInput.value;
  supplementEditor.value = suggestion.supplement;
  promptPreview.hidden = false;
  suggestionOptions.querySelectorAll(".suggestion-card").forEach((card, cardIndex) => {
    card.classList.toggle("is-selected", cardIndex === index);
  });
  updatePromptPreview();
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
  suggestionStatus.textContent = "选择一条建议后，可以继续修改补充内容；原文会保持不变。";
}

/**
 * 请求三条文本建议。此处只访问提示词接口，不会调用图片生成接口。
 */
async function optimizePrompt() {
  const originalPrompt = promptInput.value;
  if (workflowMode !== "GENERATE" || !originalPrompt.trim() || isOptimizing || isGenerating || isCheckingUpload) return;

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
  if (!originalPrompt.trim() || isGenerating || isOptimizing || isCheckingUpload) return;
  const isEditing = workflowMode === "EDIT";
  if (isEditing && !uploadedFile) return;

  const aspectRatio = selectedAspectRatio();
  const ratioDetails = aspectRatioDetails[aspectRatio];
  isGenerating = true;
  generateButtonLabel.textContent = isEditing ? "优化中…" : "生成中…";
  confirmGenerateButton.textContent = "正在生成图片…";
  loadingTitle.textContent = isEditing ? "正在优化你的图片" : "正在描绘你的画面";
  resultStatus.textContent = `正在调用图片模型 · ${ratioDetails.label}`;
  showCanvasState("loading");
  syncPromptState();

  /* 为模型调用、上传和结果下载预留时间；失败后由用户主动重试。 */
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), isEditing ? 270_000 : 135_000);

  try {
    // 文件上传由浏览器自动生成 multipart 边界，不能手动设置 Content-Type。
    let requestBody;
    if (isEditing) {
      requestBody = new FormData();
      requestBody.append("image", uploadedFile);
      requestBody.append("prompt", originalPrompt);
      requestBody.append("aspectRatio", aspectRatio);
    } else {
      requestBody = JSON.stringify({ prompt: originalPrompt, supplement, aspectRatio });
    }
    const response = await fetch(isEditing ? "/api/images/edit" : "/api/images/generate", {
      method: "POST",
      headers: isEditing ? {} : { "Content-Type": "application/json" },
      body: requestBody,
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response, isEditing ? "图片优化失败，请稍后重试" : "图片生成失败，请稍后重试"));
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
    downloadLink.href = currentImageUrl;
    downloadLink.download = filename;
    downloadLink.title = isEditing ? "下载优化结果" : "下载图片";
    downloadLink.setAttribute("aria-label", downloadLink.title);
    currentResultFilename = filename;
    resultDetails = `图片已保存 · ${ratioDetails.label} · PNG`;
    displayResult();
    hasGeneratedOnce = true;
    showCanvasState("result");
  } catch (error) {
    errorMessage.textContent = error.name === "AbortError"
      ? "生成时间过长，请稍后重试"
      : error.message;
    resultStatus.textContent = "本次请求未完成，请检查错误提示后重试";
    showCanvasState("error");
  } finally {
    window.clearTimeout(timeoutId);
    isGenerating = false;
    generateButtonLabel.textContent = isEditing
      ? "优化图片"
      : (hasGeneratedOnce ? "再次使用原文生成" : "使用原文生成");
    confirmGenerateButton.textContent = "确认方案并生成图片";
    syncPromptState();
  }
}

promptInput.addEventListener("input", clearSuggestions);
supplementEditor.addEventListener("input", updatePromptPreview);
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
  const editedSupplement = supplementEditor.value;
  if (selectedSuggestionIndex >= 0 && editedSupplement.trim()) {
    generateImage(promptInput.value, editedSupplement);
  }
});

modeButtons.forEach((button) => {
  button.addEventListener("click", () => switchMode(button.dataset.mode));
});
uploadInput.addEventListener("change", () => setUploadedImage(uploadInput.files[0]));
replaceImageButton.addEventListener("click", () => uploadInput.click());
uploadDropZone.addEventListener("keydown", (event) => {
  if (["Enter", " "].includes(event.key)) {
    event.preventDefault();
    if (!uploadInput.disabled) uploadInput.click();
  }
});
removeImageButton.addEventListener("click", () => {
  if (uploadedImageUrl) URL.revokeObjectURL(uploadedImageUrl);
  uploadedImageUrl = null;
  uploadedFile = null;
  uploadedImage.removeAttribute("src");
  uploadPreview.hidden = true;
  uploadDropZone.hidden = false;
  uploadMessage.textContent = "";
  uploadInput.value = "";
  clearResult();
  syncPromptState();
});
uploadArea.addEventListener("dragover", (event) => {
  event.preventDefault();
  if (!uploadInput.disabled) uploadArea.classList.add("is-dragging");
});
uploadArea.addEventListener("dragleave", (event) => {
  if (!uploadArea.contains(event.relatedTarget)) uploadArea.classList.remove("is-dragging");
});
uploadArea.addEventListener("drop", (event) => {
  event.preventDefault();
  uploadArea.classList.remove("is-dragging");
  if (uploadInput.disabled) return;
  if (event.dataTransfer.files.length !== 1) {
    uploadMessage.textContent = "每次请选择一张图片";
    return;
  }
  setUploadedImage(event.dataTransfer.files[0]);
});
comparisonButtons.forEach((button) => {
  button.addEventListener("click", () => displayResult(button.dataset.view));
});

/* 页面关闭时释放当前预览使用的 Blob URL，服务器保存的文件不受影响。 */
window.addEventListener("beforeunload", () => {
  if (currentImageUrl) {
    URL.revokeObjectURL(currentImageUrl);
  }
  if (uploadedImageUrl) URL.revokeObjectURL(uploadedImageUrl);
});

syncPromptState();
syncAspectRatioSummary();
