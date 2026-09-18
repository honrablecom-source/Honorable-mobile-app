/* Browser-only adapter; account ownership is resolved by the server's verified subject. */
let webConfig = {},
  accountState = null,
  catalog = {},
  entitlements = null,
  webBusy = false,
  webNotice = "";
let authState = "restoring";
// Development identity only: resettable browser storage is not device attestation.
const testInstallation =
  localStorage.getItem("honorable-test-installation") || crypto.randomUUID();
localStorage.setItem("honorable-test-installation", testInstallation);
// Remove credentials from the previous browser adapter. Persistent credentials are HttpOnly cookies.
sessionStorage.removeItem("honorable-session");
async function webRequest(route, body, signal) {
  const response = await fetch(route, {
    ...(signal
      ? { signal }
      : route.startsWith("/account/")
      ? { signal: AbortSignal.timeout(20000) }
      : {}),
    credentials: "same-origin",
    method: body === undefined ? "GET" : "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Honorable-Web": "1",
      "X-Honorable-Installation": testInstallation,
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const data = await response.json();
  if (!response.ok) {
    if (response.status === 401) {
      accountState = null;
      entitlements = null;
      authState = "welcome";
      localStorage.removeItem("honorable-account-cache");
    }
    throw Object.assign(Error(data.error || "Request failed"), {
      status: response.status,
    });
  }
  return data;
}
function cacheAccount(expiresAt) {
  localStorage.setItem(
    "honorable-account-cache",
    JSON.stringify({
      account: {
        profile: accountState.profile,
        balance: accountState.balance,
        freeMonthlyRemaining: accountState.freeMonthlyRemaining,
        freeMonthlyTotal: accountState.freeMonthlyTotal,
        nextResetAt: accountState.nextResetAt,
        usage: accountState.usage,
        subscription: accountState.subscription,
        transactions: [],
      },
      until: Math.min(expiresAt || Date.now(), Date.now() + 86400000),
    })
  );
}
async function loadAccount() {
  const data = await webRequest("/account/v1/auth/session");
  accountState = data.account;
  entitlements = data.entitlements;
  authState = "online";
  cacheAccount(data.expiresAt);
}
async function signOutAccount() {
  localStorage.setItem("honorable-pending-signout", "1");
  accountState = null;
  entitlements = null;
  authState = "welcome";
  localStorage.removeItem("honorable-account-cache");
  window.google?.accounts.id.disableAutoSelect();
  state.honorableTab = "home";
  state.app = "honorable";
  try {
    await webRequest("/account/v1/auth/logout", {});
    localStorage.removeItem("honorable-pending-signout");
  } catch {}
  render();
}

async function webAction(action) {
  if (webBusy) return;
  webBusy = true;
  webNotice = "Working…";
  render();
  try {
    await action();
    webNotice = "";
  } catch (error) {
    webNotice = error.message;
  } finally {
    webBusy = false;
    render();
  }
}
async function acceptSession() {
  localStorage.removeItem("honorable-pending-signout");
  await loadAccount();
  state.honorableTab = "home";
  state.app = "honorable";
  render();
}
async function refreshMedia() {
  const m = await webRequest("/api/refresh", {});
  state.media = m.items;
  status = await webRequest("/api/status");
  state.results = null;
}
async function importFiles(files) {
  await webAction(async () => {
    for (const file of files) {
      webNotice = "Importing " + file.name + "…";
      render();
      const response = await fetch(
        "/web/import?name=" + encodeURIComponent(file.name),
        { method: "POST", body: file }
      );
      if (!response.ok) throw Error((await response.json()).error);
    }
    webNotice = "Indexing local media…";
    render();
    await refreshMedia();
  });
}
window.bindWebTest = function () {
  if (webNotice)
    screen.firstChild?.insertAdjacentHTML(
      "afterbegin",
      `<p role=status class=web-notice>${esc(webNotice)}</p>`
    );
  screen.querySelector("#dev-signin")?.addEventListener("click", () => {
    const email = screen.querySelector("#test-identity").value.trim();
    if (email)
      webAction(async () =>
        acceptSession(await webRequest("/account/dev/auth/token", { email }))
      );
  });
  screen
    .querySelector("#sign-out")
    ?.addEventListener("click", () => webAction(signOutAccount));
  screen.querySelector("#restore")?.addEventListener("click", () =>
    webAction(async () => {
      await webRequest("/account/v1/purchases/restore", {});
      await loadAccount();
    })
  );
  screen.querySelectorAll("[data-purchase]").forEach(
    (b) =>
      (b.onclick = () =>
        webAction(async () => {
          await webRequest("/account/dev/purchases", {
            passId: b.dataset.purchase,
            storeTransactionId: "web-dev-" + crypto.randomUUID(),
          });
          await loadAccount();
        }))
  );
  screen
    .querySelector("#media-input")
    ?.addEventListener("change", (e) => importFiles([...e.target.files]));
  screen
    .querySelector("#reindex")
    ?.addEventListener("click", () => webAction(refreshMedia));
  const drop = screen.querySelector("#drop-media");
  if (drop) {
    drop.ondragover = (e) => e.preventDefault();
    drop.ondrop = (e) => {
      e.preventDefault();
      if (!webBusy) importFiles([...e.dataTransfer.files]);
    };
  }
  screen.querySelectorAll(".setting-row").forEach((row, i) => {
    row.setAttribute("role", "button");
    row.tabIndex = 0;
    const activate = () => {
      if ([1, 3].includes(i)) state.honorableTab = "activity";
      else if (i === 2) state.honorableTab = "models";
      else
        webNotice = [
          "Media and search stay on the local test server. Account requests go to the configured account API.",
          "",
          "",
          "",
          "Appearance follows the existing Android visual direction.",
          "Honorable web test shell · shared Kotlin search adapter.",
        ][i];
      render();
    };
    row.onclick = activate;
    row.onkeydown = (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        activate();
      }
    };
  });
  const plus = screen.querySelector(".plus-card");
  if (plus) {
    plus.setAttribute("role", "button");
    plus.tabIndex = 0;
    plus.onclick = () => {
      state.honorableTab = "pass";
      render();
    };
    plus.onkeydown = (e) => {
      if (e.key === "Enter") plus.click();
    };
  }
  screen.querySelectorAll("[data-model]").forEach((b) => {
    b.disabled = !(
      entitlements?.availableModels ||
      catalog.available || ["SERAN_V1", "SERAN_V2"]
    ).includes(b.dataset.model);
    b.onclick = () => {
      state.model = b.dataset.model;
      webNotice =
        "Model preference saved for this test session. Browser search uses the shared adapter’s configured model.";
      render();
    };
  });
  if (screen.querySelector("#google-signin") && webConfig.googleClientId) {
    if (window.google) renderGoogleButton();
    else if (!document.querySelector("#google-script")) {
      const script = document.createElement("script");
      script.id = "google-script";
      script.src = "https://accounts.google.com/gsi/client";
      script.onload = renderGoogleButton;
      script.onerror = () => {
        webNotice = "Unable to load Google Sign-In";
        render();
      };
      document.head.append(script);
    }
  }
};
function renderGoogleButton() {
  const target = screen.querySelector("#google-signin");
  if (!target) return;
  window.google.accounts.id.initialize({
    client_id: webConfig.googleClientId,
    callback: (response) =>
      webAction(() =>
        webRequest("/account/v1/auth/google", {
          idToken: response.credential,
        }).then(acceptSession)
      ),
  });
  window.google.accounts.id.renderButton(target, {
    theme: "filled_black",
    size: "large",
  });
}
