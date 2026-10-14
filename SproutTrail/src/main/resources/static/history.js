const AUTH_TOKEN_KEY = "sprouttrail_token";
const PAGE_SIZE = 20;
const historyGrid = document.querySelector("#history-grid");
const historyStatus = document.querySelector("#history-status");
const historyError = document.querySelector("#history-error");
const historyEmpty = document.querySelector("#history-empty");
const loadMoreButton = document.querySelector("#history-load-more");

let currentPage = 1;
let hasMore = false;
let isLoading = false;
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

function createHistoryCard(item) {
  historyItems.set(String(item.id), item);
  const card = document.createElement("article");
  card.className = "history-card";

  const imageLink = document.createElement("a");
  imageLink.className = "history-image-link";
  imageLink.href = item.minioImageUrl;
  imageLink.target = "_blank";
  imageLink.rel = "noopener noreferrer";
  imageLink.setAttribute("aria-label", "在新标签页查看图片");
  const image = document.createElement("img");
  image.className = "history-image";
  image.src = item.minioImageUrl;
  image.alt = item.originalPrompt;
  image.loading = "lazy";
  imageLink.append(image);

  const body = document.createElement("div");
  body.className = "history-card-body";
  const prompt = document.createElement("p");
  prompt.className = "history-prompt";
  prompt.textContent = item.originalPrompt;

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
  summary.textContent = "查看提示词";
  details.append(summary,
    createPromptBlock("原始提示词", item.originalPrompt, item.id, "original"),
    createPromptBlock("最终提示词", item.optimizedPrompt, item.id, "optimized"));

  const actions = document.createElement("div");
  actions.className = "history-actions";
  const downloadButton = document.createElement("button");
  downloadButton.type = "button";
  downloadButton.className = "history-action";
  downloadButton.dataset.downloadId = item.id;
  downloadButton.textContent = "下载图片";
  const continueButton = document.createElement("button");
  continueButton.type = "button";
  continueButton.className = "history-action";
  continueButton.dataset.continueId = item.id;
  continueButton.textContent = "继续创作";
  actions.append(downloadButton, continueButton);

  body.append(prompt, meta, details, actions);
  card.append(imageLink, body);
  return card;
}

async function loadHistory() {
  if (isLoading) return;
  isLoading = true;
  loadMoreButton.disabled = true;
  historyError.hidden = true;
  historyStatus.textContent = currentPage === 1 ? "正在加载图片记录…" : "正在加载更多…";

  try {
    const response = await authenticatedFetch(
      `/api/images/history?page=${currentPage}&pageSize=${PAGE_SIZE}`);
    if (!response.ok) throw new Error(await readError(response));

    const result = await response.json();
    result.items.forEach((item) => historyGrid.append(createHistoryCard(item)));
    hasMore = result.hasMore;
    currentPage = result.page + 1;
    const hasItems = historyGrid.childElementCount > 0;
    historyEmpty.hidden = hasItems;
    loadMoreButton.hidden = !hasMore;
    historyStatus.textContent = hasItems ? "按生成时间倒序显示" : "";
  } catch (error) {
    historyError.textContent = error.message || "图片记录加载失败，请稍后重试";
    historyError.hidden = false;
    historyStatus.textContent = "";
  } finally {
    isLoading = false;
    loadMoreButton.disabled = false;
  }
}

async function downloadHistoryImage(recordId, button) {
  button.disabled = true;
  button.textContent = "正在下载…";
  try {
    const response = await authenticatedFetch(`/api/images/history/${recordId}/download`);
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
    await loadHistory();
  } catch {
    redirectToLogin();
  }
}

void initializeHistoryPage();
