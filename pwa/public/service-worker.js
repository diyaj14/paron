const CACHE = "paron-wallet-v1";
const ASSETS = ["./", "./index.html", "./styles.css", "./src/app.js", "./manifest.webmanifest", "./icons/paron.svg", "./icons/paron-maskable.svg"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(caches.match(event.request).then((saved) => saved || fetch(event.request)));
});
