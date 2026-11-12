/* Judge-facing Paron walkthrough. Every value and action in this page is local simulation. */
const $ = (id) => document.getElementById(id);
const dbKey = "paron-judge-simulation-v1";
const state = { online: true, merchantConnected: false, token: null, transactions: [], total: 1000 };
const money = (n) => `₹${Number(n || 0).toFixed(2)}`;
const escapeHtml = (s) => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const activeToken = () => state.token?.status === "ACTIVE" ? state.token : null;
function notify(text) { $("notice").textContent = text; }
function save() { localStorage.setItem(dbKey, JSON.stringify({ token: state.token, transactions: state.transactions, total: state.total })); }
function remaining() {
  const token = activeToken();
  if (!token) return 0;
  const spent = state.transactions.filter(t => !["REJECTED", "FAILED"].includes(t.state)).reduce((sum, t) => sum + Number(t.amount), 0);
  return Math.max(0, Number(token.maxAmount || 0) - spent);
}
function render() {
  const online = state.online && navigator.onLine;
  const token = activeToken(), locked = token ? remaining() : 0;
  $("network-indicator").classList.toggle("offline", !online);
  $("connection-state").textContent = online ? "Online · simulated" : "Offline · simulated";
  $("connection-state-copy").textContent = online ? "Connected · ready for sample flow" : "Offline · sample allowance available";
  $("demo-network-toggle").textContent = online ? "Simulate offline" : "Reconnect";
  $("account-total").textContent = money(state.total);
  $("locked-offline-amount").textContent = money(locked);
  $("available-online-amount").textContent = money(Math.max(0, state.total - Number(token?.maxAmount || 0)));
  $("token-state").textContent = token ? "SAMPLE LIMIT SET" : "NO SAMPLE LIMIT";
  $("offline-allowance-display").textContent = money(locked);
  const amount = Number($("payment-amount").value || 0);
  $("remaining-limit-display").textContent = money(Math.max(0, locked - amount));
  $("pay").disabled = !token || locked < amount;
  $("pay").innerHTML = `Make sample offline payment · ${money(amount)} <span>→</span>`;

  const merchantId = $("merchant-id").value;
  const merchantTx = state.transactions.filter(t => t.merchantId === merchantId);
  const received = merchantTx.reduce((sum, t) => sum + Number(t.amount), 0);
  const completed = merchantTx.filter(t => t.state === "SIMULATED").reduce((sum, t) => sum + Number(t.amount), 0);
  $("merchant-pending-total").textContent = money(received - completed);
  $("merchant-settled-total").textContent = money(completed);
  document.querySelector(".merchant-metrics > div:first-child small").textContent = "SAMPLE RECEIPTS";
  document.querySelector(".merchant-metrics > div:nth-child(2) small").textContent = "SAMPLE TOTAL";
  $("merchant-display-name").textContent = $("merchant-picker").selectedOptions[0]?.textContent || "Merchant";
  $("merchant-display-id").textContent = merchantId;
  $("receipt-count").textContent = String(merchantTx.length);
  $("merchant-inbox").innerHTML = merchantTx.length ? merchantTx.slice().reverse().map(t => `<div class="inbox-receipt"><span class="receipt-icon">✓</span><div><strong>${escapeHtml(t.statusLabel || "Sample receipt received")}</strong><small>${escapeHtml(t.id.slice(0, 8))} · ${t.state === "SIMULATED" ? "Sample check complete" : "Saved in this browser"}</small></div><span class="receipt-amount">${money(t.amount)}</span></div>`).join("") : '<div class="empty-inbox"><span>⌁</span><p>Sample payment receipts will appear here.</p></div>';
  $("transactions").innerHTML = state.transactions.length ? state.transactions.slice().reverse().map(t => `<article class="transaction"><div><strong>${money(t.amount)} → ${escapeHtml(t.merchantName)}</strong><small>${new Date(t.createdAt).toLocaleString()} · ${escapeHtml(t.id.slice(0, 8))}</small></div><span class="tx-badge-group ${t.state === "SIMULATED" ? "settled" : ""}">${escapeHtml(t.stateLabel || t.state)}</span></article>`).join("") : '<p class="empty-inbox">No sample transactions yet.</p>';
  $("sync-now").disabled = !state.transactions.some(t => t.state === "PENDING_SYNC") || !online;
}
function run(action) { return async () => { try { await action(); } catch (e) { console.error(e); notify(e.message || "Action failed."); } }; }
function reserve() {
  if (!state.online || !navigator.onLine) throw new Error("Reconnect before setting aside a sample offline limit.");
  const amount = Number($("new-token-amount").value || 500), hours = Number($("expiry-hours").value || 6);
  if (!(amount > 0) || amount > state.total) throw new Error(`Choose an amount from ₹1 to ${money(state.total)}.`);
  state.token = { tokenId: crypto.randomUUID(), token: `sample-${crypto.randomUUID()}`, maxAmount: amount, expiresAt: new Date(Date.now() + hours * 3600000).toISOString(), status: "ACTIVE" };
  $("allowance-status").textContent = `${money(amount)} sample allowance set aside in this browser. No checkout, backend, or money movement occurred.`;
  save(); render();
  notify(`Sample allowance of ${money(amount)} set aside. This is a local simulation only.`);
}
function connectMerchant() {
  state.merchantConnected = true;
  $("ble-status").textContent = "Sample handoff ready · this website does not connect to Bluetooth or a merchant device.";
  notify("Sample merchant view is ready.");
}
function payOffline() {
  const token = activeToken(), amount = Number($("payment-amount").value), id = $("merchant-id").value;
  if (!token || !(amount > 0) || amount > remaining()) throw new Error("Set a sample offline limit large enough for this payment first.");
  if (!state.merchantConnected) connectMerchant();
  const tx = { id: crypto.randomUUID(), amount, merchantId: id, merchantName: $("merchant-picker").selectedOptions[0]?.textContent || id, createdAt: new Date().toISOString(), state: "PENDING_SYNC", stateLabel: "Sample receipt · waiting to sync" };
  state.transactions.push(tx); save(); render();
  notify("Sample receipt shown on the merchant side. No real payment was made.");
}
function syncSimulation() {
  if (!state.online || !navigator.onLine) throw new Error("Reconnect before running the sample sync.");
  const pending = state.transactions.filter(t => t.state === "PENDING_SYNC");
  if (!pending.length) throw new Error("There are no sample receipts waiting to sync.");
  pending.forEach(t => { t.state = "SIMULATED"; t.stateLabel = "Sample check complete"; t.statusLabel = "Sample check complete · no account update"; });
  save();
  $("sync-timeline-container").hidden = false;
  $("sync-timeline-container").querySelector(".timeline-label").textContent = "LOCAL SIMULATION · NO BACKEND";
  $("step-settle").textContent = "Simulated result";
  ["step-upload", "step-sig", "step-cap", "step-settle"].forEach(id => $(id).classList.add("done"));
  $("sync-timeline-status").textContent = "Sample walkthrough complete. No backend received these receipts and no money moved.";
  notify($("sync-timeline-status").textContent); render();
}
function restore() {
  try {
    const data = JSON.parse(localStorage.getItem(dbKey) || "{}");
    state.token = data.token || null;
    state.transactions = data.transactions || [];
    state.total = data.total ?? 1000;
  } catch { /* Start with the sample wallet defaults. */ }
}
function init() {
  restore();
  $("truth-banner").className = "truth-banner simulation-banner";
  $("truth-banner").innerHTML = "<b>SIMULATION ONLY</b><span>Wallet balance, allowance, receipts, and sync are sample data saved in this browser. No PayPal checkout, backend request, or real money movement occurs.</span>";
  $("mode-description").textContent = "Local sample data · no services or real money";
  $("allowance-status").textContent = "Sample allowance only · no payment service is connected.";
  $("request-token").textContent = "Set aside sample limit →";
  $("ble-status").textContent = "This website simulates the nearby handoff. It does not use Bluetooth or Android secure hardware.";
  $("sync-timeline-container").querySelector(".timeline-label").textContent = "LOCAL SIMULATION · NO BACKEND";
  $("request-token").addEventListener("click", run(reserve));
  $("connect-merchant").addEventListener("click", run(connectMerchant));
  $("pay").addEventListener("click", run(payOffline));
  $("sync-now").addEventListener("click", run(syncSimulation));
  $("payment-amount").addEventListener("input", render);
  $("merchant-picker").addEventListener("change", e => { $("merchant-id").value = e.target.value; render(); });
  $("demo-network-toggle").addEventListener("click", () => { state.online = !state.online; notify(state.online ? "Connection restored in this simulation." : "Offline mode simulated. Sample payments remain available."); render(); });
  window.addEventListener("online", render);
  window.addEventListener("offline", render);
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("./service-worker.js").catch(() => {});
  render();
}
window.addEventListener("DOMContentLoaded", init);
