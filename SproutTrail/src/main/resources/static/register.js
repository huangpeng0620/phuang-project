const form = document.querySelector("#register-form");
const usernameInput = document.querySelector("#username");
const passwordInput = document.querySelector("#password");
const confirmPasswordInput = document.querySelector("#confirm-password");
const errorMessage = document.querySelector("#register-error");
const successMessage = document.querySelector("#register-success");
const registerButton = document.querySelector("#register-button");
const registerButtonLabel = registerButton.querySelector("span");

/** 读取后端返回的错误信息，网络或非 JSON 错误使用统一提示。 */
async function readRegisterError(response) {
  try {
    const body = await response.json();
    return body.message || "注册失败，请稍后重试";
  } catch {
    return "注册失败，请稍后重试";
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  errorMessage.textContent = "";

  if (passwordInput.value !== confirmPasswordInput.value) {
    errorMessage.textContent = "两次输入的密码不一致";
    return;
  }

  registerButton.disabled = true;
  registerButtonLabel.textContent = "正在创建…";

  try {
    const response = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: usernameInput.value,
        password: passwordInput.value
      })
    });

    if (!response.ok) {
      errorMessage.textContent = await readRegisterError(response);
      return;
    }

    form.hidden = true;
    successMessage.hidden = false;
  } catch {
    errorMessage.textContent = "无法连接服务器，请稍后重试";
  } finally {
    registerButton.disabled = false;
    registerButtonLabel.textContent = "创建账号";
  }
});
