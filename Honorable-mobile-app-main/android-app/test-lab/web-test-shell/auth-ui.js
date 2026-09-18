// Startup is governed by verified account/session state, never a first-open flag.
window.authBootstrapped = true;
window.renderAuthGate = () => {
  document.querySelector(".test-tools").hidden =
    authState !== "online" && authState !== "offline";
  if (authState === "online" || authState === "offline") return false;
  if (authState === "restoring" || authState === "signing-in") {
    screen.innerHTML =
      "<div class=auth-launch role=status><b>honorable</b><span>Opening your memories…</span></div>";
    return true;
  }
  screen.innerHTML = `<div class="auth-welcome page"><b class=auth-wordmark>honorable</b><div class=auth-photo><img src="/prompt_beach.png" alt="A quiet beach memory"></div><h1>Find any<br>memory.</h1><p>You remember the moment.<br>Honorable finds it.</p><div id=google-signin></div><p class=auth-error role=status>${esc(
    webNotice
  )}</p><strong>Your memories stay yours.</strong><small>Signing in keeps your Memory Passes, credits and account access connected across sessions and devices.</small><button id=auth-retry>Try again</button></div>`;
  if (webConfig.googleClientId) {
    if (window.google) renderGoogleButton();
    else if (!document.querySelector("#google-script")) {
      const script = document.createElement("script");
      script.id = "google-script";
      script.src = "https://accounts.google.com/gsi/client";
      script.onload = () => {
        renderGoogleButton();
      };
      script.onerror = () => {
        script.remove();
        webNotice = "Connect to the internet to continue with Google.";
        render();
      };
      document.head.append(script);
    }
  } else {
    screen.querySelector("#google-signin").innerHTML =
      "<button disabled>Continue with Google</button>";
    screen.querySelector(".auth-error").textContent =
      webNotice || "Google Sign-In is not configured.";
  }
  screen.querySelector("#auth-retry").onclick = restoreStartup;
  return true;
};
renderGoogleButton = () => {
  const target = screen.querySelector("#google-signin");
  if (!target || !window.google) return;
  window.google.accounts.id.initialize({
    client_id: webConfig.googleClientId,
    callback: async (response) => {
      authState = "signing-in";
      webNotice = "";
      render();
      try {
        await webRequest("/account/v1/auth/google", {
          idToken: response.credential,
        });
        await acceptSession();
      } catch (error) {
        authState = "welcome";
        webNotice = error.message;
      }
      render();
    },
  });
  window.google.accounts.id.renderButton(target, {
    theme: "outline",
    size: "large",
    shape: "pill",
    text: "continue_with",
    width: 300,
  });
};
async function restoreStartup() {
  authState = "restoring";
  webNotice = "";
  render();
  try {
    if (localStorage.getItem("honorable-pending-signout")) {
      await webRequest("/account/v1/auth/logout", {});
      localStorage.removeItem("honorable-pending-signout");
    }
    const [config, c] = await Promise.all([
      webRequest("/web/config"),
      webRequest("/account/v1/catalog"),
    ]);
    webConfig = config;
    catalog = c;
    await loadAccount();
    state.app = "honorable";
    state.honorableTab = "home";
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      authState = "welcome";
      accountState = null;
      entitlements = null;
      localStorage.removeItem("honorable-account-cache");
      webNotice = "";
    } else {
      let cached;
      try {
        cached = JSON.parse(
          localStorage.getItem("honorable-account-cache") || "null"
        );
      } catch {}
      if (cached?.until > Date.now()) {
        accountState = cached.account;
        entitlements = null;
        authState = "offline";
        webNotice =
          "Offline · using previously verified account. Reconnect for credits and purchases.";
      } else {
        authState = "welcome";
        webNotice =
          "Connect to the internet to restore your account or sign in.";
      }
    }
  }
  render();
}
const priorBind = window.bindWebTest;
window.bindWebTest = () => {
  priorBind();
  screen
    .querySelector("#account-signout")
    ?.addEventListener("click", () => webAction(signOutAccount));
  if (authState === "offline")
    screen
      .querySelectorAll("[data-purchase],#restore")
      .forEach((b) => (b.disabled = true));
};
addEventListener("offline", () => {
  if (authState === "online") {
    authState = "offline";
    entitlements = null;
    webNotice =
      "Offline · using previously verified account. Reconnect for credits and purchases.";
    render();
  }
});
addEventListener("online", () => {
  if (
    authState === "offline" ||
    localStorage.getItem("honorable-pending-signout")
  )
    restoreStartup();
});
// Development sign-in remains outside the phone and is never enabled by a production account server.
const testLogin = document.createElement("button");
testLogin.id = "test-account-login";
testLogin.textContent = "Use test account";
testLogin.className = "test-auth-tool";
document.body.append(testLogin);
testLogin.onclick = async () => {
  if (!webConfig.developmentPurchases) return;
  const email = webConfig.betaRequired
    ? prompt("Invited development tester email")
    : "browser-tester";
  if (!email) return;
  await webAction(async () => {
    await webRequest("/account/dev/auth/token", { email });
    await acceptSession();
  });
};
const gate = window.renderAuthGate;
window.renderAuthGate = () => {
  testLogin.hidden = !webConfig.developmentPurchases || authState !== "welcome";
  return gate();
};
restoreStartup();

addEventListener("storage", (event) => {
  if (event.key === "honorable-account-cache" && event.newValue === null) {
    accountState = null;
    entitlements = null;
    authState = "welcome";
    render();
  }
});
