// Helm Pad — marketing site
//
// On load, fetch the latest GitHub Release and rewrite the primary CTA to
// link directly at the signed APK asset, plus surface version + release date
// in the footer. On any failure (rate limit, 404, no APK asset, network),
// silently fall back to the static "go to releases page" CTA already in HTML.
//
// Design notes:
// - Pure progressive enhancement: HTML works without JS, JS only upgrades.
// - No external dependencies, no analytics, no tracking.
// - APK selection rule: name endsWith('.apk') AND not '-debug'. .aab assets
//   are intentionally ignored (the site only links sideload-friendly APKs).
// - Locale for the release date is taken from <html lang="..."> so the
//   English page formats dates as en-US and the Chinese page as zh-CN.

(function () {
  "use strict";

  var REPO = "huozhou/HelmPad";
  var API = "https://api.github.com/repos/" + REPO + "/releases/latest";

  function pickApkAsset(assets) {
    if (!Array.isArray(assets)) return null;
    for (var i = 0; i < assets.length; i++) {
      var a = assets[i];
      if (!a || typeof a.name !== "string") continue;
      var name = a.name.toLowerCase();
      if (!name.endsWith(".apk")) continue;
      if (name.indexOf("-debug") !== -1) continue;
      if (typeof a.browser_download_url !== "string") continue;
      return a;
    }
    return null;
  }

  function formatDate(iso, locale) {
    try {
      var d = new Date(iso);
      if (isNaN(d.getTime())) return "";
      return d.toLocaleDateString(locale, {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch (e) {
      return "";
    }
  }

  function applyRelease(release) {
    var apk = pickApkAsset(release && release.assets);
    if (!apk) {
      // Treat "no usable asset" the same as a fetch failure: keep static fallback.
      return false;
    }

    var cta = document.getElementById("cta-download");
    if (cta) {
      var template = cta.getAttribute("data-i18n-template") || "Download {version} APK";
      var label = template.replace("{version}", release.tag_name || "");
      cta.href = apk.browser_download_url;
      // Keep the trailing arrow for visual consistency with the fallback state.
      cta.innerHTML =
        escapeHtml(label) +
        ' <span aria-hidden="true">↓</span>';
      cta.setAttribute("data-state", "live");
    }

    var meta = document.getElementById("release-meta");
    var ver = document.getElementById("release-version");
    var dateEl = document.getElementById("release-date");
    var changelog = document.getElementById("release-changelog");

    if (ver) ver.textContent = release.tag_name || "";
    if (dateEl && release.published_at) {
      var locale = document.documentElement.lang || "en";
      var formatted = formatDate(release.published_at, locale);
      dateEl.textContent = formatted;
      dateEl.setAttribute("datetime", release.published_at);
    }
    if (changelog && release.html_url) changelog.href = release.html_url;
    if (meta) meta.hidden = false;

    return true;
  }

  function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, function (ch) {
      switch (ch) {
        case "&": return "&amp;";
        case "<": return "&lt;";
        case ">": return "&gt;";
        case '"': return "&quot;";
        case "'": return "&#39;";
      }
      return ch;
    });
  }

  function init() {
    if (typeof fetch !== "function") return; // Very old browsers: keep static fallback.

    fetch(API, {
      headers: { Accept: "application/vnd.github+json" },
      // No-store keeps the link fresh across releases without forcing a hard reload,
      // and avoids stale CDN-cached responses pointing at superseded assets.
      cache: "no-store",
    })
      .then(function (r) {
        if (!r.ok) throw new Error("GitHub API returned " + r.status);
        return r.json();
      })
      .then(function (release) {
        var ok = applyRelease(release);
        if (!ok) console.warn("[HelmPad] No usable APK asset on latest release; keeping static fallback.");
      })
      .catch(function (err) {
        console.warn("[HelmPad] Could not load latest release:", err && err.message ? err.message : err);
      });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
