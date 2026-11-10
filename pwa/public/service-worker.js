const CACHE = "paron-experience-v5";
const ASSETS = ["./", "./index.html", "./landing.css", "./demo.html", "./styles.css", "./src/app.js", "./src/landing.js", "./manifest.webmanifest", "./icons/paron.svg", "./icons/paron-maskable.svg"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(caches.match(event.request).then((saved) => saved || fetch(event.request)));
});
