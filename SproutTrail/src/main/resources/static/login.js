const AUTH_TOKEN_KEY = "sprouttrail_token";
const form = document.querySelector("#login-form");
const usernameInput = document.querySelector("#username");
const passwordInput = document.querySelector("#password");
const errorMessage = document.querySelector("#login-error");
const loginButton = document.querySelector("#login-button");
const loginButtonLabel = loginButton.querySelector("span");

/** 获取后端统一错误响应中的可读提示。 */
async function readLoginError(response) {
  try {
    const body = await response.json();
    return body.message || "登录失败，请检查用户名和密码";
  } catch {
    return "登录失败，请稍后重试";
  }
}

/** 已有 token 时先向后端验证；有效会话直接进入工作台。 */
async function redirectIfAlreadyLoggedIn() {
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY);
  if (!token) {
    document.documentElement.classList.remove("auth-checking");
    return;
  }

  try {
    const response = await fetch("/api/auth/token", { headers: { satoken: token } });
    if (response.ok) {
      window.location.replace("/");
      return;
    }
  } catch {
    // 后端不可用时仍显示登录表单，用户可以查看并重试登录。
  }

  sessionStorage.removeItem(AUTH_TOKEN_KEY);
  document.documentElement.classList.remove("auth-checking");
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  errorMessage.textContent = "";
  loginButton.disabled = true;
  loginButtonLabel.textContent = "正在登录…";

  try {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: usernameInput.value,
        password: passwordInput.value
      })
    });

    if (!response.ok) {
      errorMessage.textContent = await readLoginError(response);
      return;
    }

    const result = await response.json();
    if (!result.tokenValue) {
      throw new Error("登录响应缺少 token，请稍后重试");
    }

    sessionStorage.setItem(AUTH_TOKEN_KEY, result.tokenValue);
    window.location.replace("/");
  } catch (error) {
    errorMessage.textContent = error.message || "无法连接服务器，请稍后重试";
  } finally {
    loginButton.disabled = false;
    loginButtonLabel.textContent = "登录工作台";
  }
});

void redirectIfAlreadyLoggedIn();
