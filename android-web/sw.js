/* VidGravity P160: offline app shell only; never cache API, license, or download responses. */
const CACHE_NAME = "vidgravity-p177-shell-v1";
const APP_SHELL = ["./","./index.html","./styles.css","./app.js","./social-export.css","./social-export.js","./player-refactor.js","./manifest.webmanifest","./icons/icon-192.svg","./icons/icon-512.svg"];
self.addEventListener("install", event => event.waitUntil((async () => {
  const cache = await caches.open(CACHE_NAME); await cache.addAll(APP_SHELL); await self.skipWaiting();
})()));
self.addEventListener("activate", event => event.waitUntil((async () => {
  const keys = await caches.keys();
  await Promise.all(keys.filter(key => key.startsWith("vidgravity-") && key !== CACHE_NAME).map(key => caches.delete(key)));
  await self.clients.claim();
})()));
function isSensitiveRequest(url, request) {
  if (request.method !== "GET") return true;
  if (url.origin !== self.location.origin) return true;
  return url.pathname.includes("/api/") || /\/(license|download|payment|checkout|activate|deactivate)(\/|$)/i.test(url.pathname);
}
self.addEventListener("fetch", event => {
  const request = event.request, url = new URL(request.url);
  if (isSensitiveRequest(url, request)) return;
  const shellBase = new URL("./", self.location.href);
  const knownAsset = APP_SHELL.some(asset => { const candidate = new URL(asset, shellBase); return candidate.origin === url.origin && candidate.pathname === url.pathname; });
  if (!knownAsset && request.mode !== "navigate") return;
  if (request.mode === "navigate") {
    event.respondWith((async () => {
      try { return await fetch(request); }
      catch (_) { return (await caches.match("./index.html")) || Response.error(); }
    })());
    return;
  }
  // Network-first for app assets so deployed fixes replace stale cached code.
  // Keep the last successful response available for offline use.
  event.respondWith((async () => {
    try {
      const response = await fetch(request, { cache: "no-cache" });
      if (response.ok && response.type === "basic") {
        const cache = await caches.open(CACHE_NAME);
        await cache.put(request, response.clone());
      }
      return response;
    } catch (_) {
      const cached = await caches.match(request, { ignoreSearch: true });
      return cached || Response.error();
    }
  })());
});
