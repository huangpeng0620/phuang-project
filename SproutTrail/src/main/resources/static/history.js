const AUTH_TOKEN_KEY = "sprouttrail_token";
const PAGE_SIZE = 20;
const historyGrid = document.querySelector("#history-grid");
const historyStatus = document.querySelector("#history-status");
const historyError = document.querySelector("#history-error");
const historyEmpty = document.querySelector("#history-empty");
const historyEmptyTitle = document.querySelector("#history-empty-title");
const historyEmptyMessage = document.querySelector("#history-empty-message");
const loadMoreButton = document.querySelector("#history-load-more");
const historyTabs = [...document.querySelectorAll("[data-history-type]")];

let currentPage = 1;
let hasMore = false;
let isLoading = false;
let historyType = "generation";
const historyItems = new Map();

function redirectToLogin() {
  sessionStorage.removeItem(AUTH_TOKEN_KEY);
  window.location.replace("/login.html");
}

async function authenticatedFetch(url, options = {}) {
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY);
  if (!token) {
    redirectToLogin();
    throw new Error("登录状态已失效");
  }

  const headers = new Headers(options.headers || {});
  headers.set("satoken", token);
  const response = await fetch(url, { ...options, headers });
  if (response.status === 401) redirectToLogin();
  return response;
}

async function readError(response) {
  try {
    const body = await response.json();
    return body.message || "请求失败，请稍后重试";
  } catch {
    return "请求失败，请稍后重试";
  }
}

function createPromptBlock(title, prompt, recordId, copyKind) {
  const block = document.createElement("div");
  block.className = "history-prompt-block";
  const heading = document.createElement("h3");
  heading.textContent = title;
  const content = document.createElement("p");
  content.textContent = prompt;
  const copyButton = document.createElement("button");
  copyButton.type = "button";
  copyButton.className = "history-action";
  copyButton.dataset.copyId = recordId;
  copyButton.dataset.copyKind = copyKind;
  copyButton.textContent = "复制提示词";
  block.append(heading, content, copyButton);
  return block;
}

function createImageLink(url, alt, label) {
  const link = document.createElement("a");
  link.className = "history-image-link";
  link.href = url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.setAttribute("aria-label", label ? `在新标签页查看${label}` : "在新标签页查看图片");
  const image = document.createElement("img");
  image.className = "history-image";
  image.src = url;
  image.alt = alt;
  image.loading = "lazy";
  link.append(image);
  if (label) {
    const badge = document.createElement("span");
    badge.className = "history-image-label";
    badge.textContent = label;
    link.append(badge);
  }
  return link;
}

function createHistoryCard(item) {
  const isOptimization = historyType === "optimization";
  historyItems.set(String(item.id), item);
  const card = document.createElement("article");
  card.className = "history-card";

  if (isOptimization) {
    const imagePair = document.createElement("div");
    imagePair.className = "history-image-pair";
    imagePair.append(
      createImageLink(item.sourceImageUrl, "图片优化前的原图", "原图"),
      createImageLink(item.resultImageUrl, "图片优化后的结果", "优化结果"));
    card.append(imagePair);
  } else {
    card.append(createImageLink(item.minioImageUrl, item.originalPrompt, null));
  }

  const body = document.createElement("div");
  body.className = "history-card-body";
  const prompt = document.createElement("p");
  prompt.className = "history-prompt";
  prompt.textContent = isOptimization ? item.prompt : item.originalPrompt;

  const meta = document.createElement("div");
  meta.className = "history-meta";
  const ratio = document.createElement("span");
  ratio.textContent = item.aspectRatio.replaceAll("_", " ");
  const date = document.createElement("time");
  date.dateTime = item.createdAt;
  date.textContent = item.createdAt.replace("T", " ").slice(0, 16);
  meta.append(ratio, date);

  const details = document.createElement("details");
  details.className = "history-details";
  const summary = document.createElement("summary");
  summary.textContent = isOptimization ? "查看优化要求" : "查看提示词";
  details.append(summary);
  if (isOptimization) {
    details.append(createPromptBlock("图片优化要求", item.prompt, item.id, "prompt"));
  } else {
    details.append(
      createPromptBlock("原始提示词", item.originalPrompt, item.id, "original"),
      createPromptBlock("最终提示词", item.optimizedPrompt, item.id, "optimized"));
  }

  const actions = document.createElement("div");
  actions.className = `history-actions${isOptimization ? " history-actions-single" : ""}`;
  const downloadButton = document.createElement("button");
  downloadButton.type = "button";
  downloadButton.className = "history-action";
  downloadButton.dataset.downloadId = item.id;
  downloadButton.textContent = "下载图片";
  actions.append(downloadButton);
  if (!isOptimization) {
    const continueButton = document.createElement("button");
    continueButton.type = "button";
    continueButton.className = "history-action";
    continueButton.dataset.continueId = item.id;
    continueButton.textContent = "继续创作";
    actions.append(continueButton);
  }

  body.append(prompt, meta, details, actions);
  card.append(body);
  return card;
}

function updateEmptyState() {
  const isOptimization = historyType === "optimization";
  historyEmptyTitle.textContent = isOptimization ? "这里还没有优化记录" : "这里还没有生成图片";
  historyEmptyMessage.textContent = isOptimization
    ? "上传图片并完成一次优化后，记录会显示在这里。"
    : "使用文字生成图片后，记录会显示在这里。";
}

function historyEndpoint() {
  return historyType === "optimization"
    ? "/api/images/optimization-history"
    : "/api/images/history";
}

async function loadHistory() {
  if (isLoading) return;
  isLoading = true;
  loadMoreButton.disabled = true;
  historyTabs.forEach((tab) => { tab.disabled = true; });
  historyError.hidden = true;
  historyStatus.textContent = currentPage === 1 ? "正在加载图片记录…" : "正在加载更多…";

  try {
    const response = await authenticatedFetch(
      `${historyEndpoint()}?page=${currentPage}&pageSize=${PAGE_SIZE}`);
    if (!response.ok) throw new Error(await readError(response));

    const result = await response.json();
    result.items.forEach((item) => historyGrid.append(createHistoryCard(item)));
    hasMore = result.hasMore;
    currentPage = result.page + 1;
    const hasItems = historyGrid.childElementCount > 0;
    historyEmpty.hidden = hasItems;
    loadMoreButton.hidden = !hasMore;
    historyStatus.textContent = hasItems
      ? (historyType === "optimization" ? "按优化时间倒序显示" : "按生成时间倒序显示")
      : "";
  } catch (error) {
    historyError.textContent = error.message || "图片记录加载失败，请稍后重试";
    historyError.hidden = false;
    historyStatus.textContent = "";
  } finally {
    isLoading = false;
    loadMoreButton.disabled = false;
    historyTabs.forEach((tab) => { tab.disabled = false; });
  }
}

async function downloadHistoryImage(recordId, button) {
  button.disabled = true;
  button.textContent = "正在下载…";
  const basePath = historyType === "optimization"
    ? "/api/images/optimization-history"
    : "/api/images/history";
  try {
    const response = await authenticatedFetch(`${basePath}/${recordId}/download`);
    if (!response.ok) throw new Error(await readError(response));

    const objectUrl = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = `${recordId}.png`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    button.textContent = "已下载";
  } catch (error) {
    historyError.textContent = error.message || "图片下载失败，请稍后重试";
    historyError.hidden = false;
    button.textContent = "下载图片";
  } finally {
    button.disabled = false;
  }
}

historyGrid.addEventListener("click", async (event) => {
  const downloadButton = event.target.closest("[data-download-id]");
  if (downloadButton) {
    await downloadHistoryImage(downloadButton.dataset.downloadId, downloadButton);
    return;
  }

  const copyButton = event.target.closest("[data-copy-id]");
  if (copyButton) {
    const item = historyItems.get(copyButton.dataset.copyId);
    if (!item) return;
    const text = copyButton.dataset.copyKind === "optimized"
      ? item.optimizedPrompt
      : copyButton.dataset.copyKind === "prompt"
        ? item.prompt
        : item.originalPrompt;
    try {
      await navigator.clipboard.writeText(text);
      copyButton.textContent = "已复制";
    } catch {
      copyButton.textContent = "复制失败";
    }
    return;
  }

  const continueButton = event.target.closest("[data-continue-id]");
  if (continueButton) {
    const item = historyItems.get(continueButton.dataset.continueId);
    if (!item) return;
    const query = new URLSearchParams({ prompt: item.originalPrompt, aspectRatio: item.aspectRatio });
    window.location.assign(`/?${query}`);
  }
});

historyTabs.forEach((tab) => tab.addEventListener("click", () => {
  if (isLoading || tab.dataset.historyType === historyType) return;
  historyType = tab.dataset.historyType;
  historyGrid.setAttribute("aria-labelledby", tab.id);
  historyTabs.forEach((item) => {
    const isSelected = item === tab;
    item.classList.toggle("is-active", isSelected);
    item.setAttribute("aria-selected", String(isSelected));
  });
  currentPage = 1;
  hasMore = false;
  historyItems.clear();
  historyGrid.replaceChildren();
  historyEmpty.hidden = true;
  historyError.hidden = true;
  loadMoreButton.hidden = true;
  updateEmptyState();
  void loadHistory();
}));

loadMoreButton.addEventListener("click", loadHistory);

document.querySelector("#logout-button").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  button.textContent = "正在退出…";
  try {
    await authenticatedFetch("/api/auth/logout", { method: "POST" });
  } catch {
    // 即使服务器暂时不可用，也清理本地会话。
  } finally {
    redirectToLogin();
  }
});

async function initializeHistoryPage() {
  try {
    const response = await authenticatedFetch("/api/auth/token");
    if (!response.ok) {
      redirectToLogin();
      return;
    }
    const session = await response.json();
    document.querySelector("#current-user").textContent = session.username;
    document.documentElement.classList.remove("auth-checking");
    updateEmptyState();
    await loadHistory();
  } catch {
    redirectToLogin();
  }
}

void initializeHistoryPage();
