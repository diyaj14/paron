/* Paron BLE v1 customer wallet. No raw tokens are rendered in the UI. */
const BLE_SERVICE = "dabb1d77-39bd-4b1e-a6f2-1abc02d4de01";
const OFFER_CHARACTERISTIC = "dabb1d77-39bd-4b1e-a6f2-1abc02d4de02";
const RECEIPT_CHARACTERISTIC = "dabb1d77-39bd-4b1e-a6f2-1abc02d4de03";
const encoder = new TextEncoder();
const decoder = new TextDecoder();
const $ = (id) => document.getElementById(id);
function fallbackUUID() { return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16); }); }
function uuid() { try { return crypto.randomUUID(); } catch { return fallbackUUID(); } }
function deviceId() { let id = localStorage.getItem("paron-device-id"); if (!id) { id = uuid(); localStorage.setItem("paron-device-id", id); } return id; }
let merchantConnection = null;

const database = (() => {
  let dbPromise;
  function open() { if (!dbPromise) dbPromise = new Promise((resolve, reject) => { const request = indexedDB.open("paron-wallet", 1); request.onupgradeneeded = () => { const db = request.result; db.createObjectStore("settings"); db.createObjectStore("tokens", { keyPath: "tokenId" }); db.createObjectStore("transactions", { keyPath: "deviceTransactionId" }); }; request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }); return dbPromise; }
  async function put(store, value, key) { const db = await open(); return new Promise((resolve, reject) => { const request = db.transaction(store, "readwrite").objectStore(store).put(value, key); request.onsuccess = () => resolve(); request.onerror = () => reject(request.error); }); }
  async function get(store, key) { const db = await open(); return new Promise((resolve, reject) => { const request = db.transaction(store).objectStore(store).get(key); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }); }
  async function all(store) { const db = await open(); return new Promise((resolve, reject) => { const request = db.transaction(store).objectStore(store).getAll(); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }); }
  return { put, get, all };
})();

function message(text) { $("notice").textContent = text; }
function apiBase() { return $("api-base").value.trim().replace(/\/$/, ""); }
function rupees(amount) { return `₹${Number(amount).toFixed(2)}`; }
function statusClass(state) { return state.includes("FAILED") || state.includes("REJECTED") ? "failed" : state.includes("PENDING") || state.includes("QUEUED") || state.includes("ACCEPTED") || state.includes("HELD") ? "pending" : "active"; }
async function sha256(text) { const result = await crypto.subtle.digest("SHA-256", encoder.encode(text)); return btoa(String.fromCharCode(...new Uint8Array(result))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); }

/*
 * Signed receipts (Step 0b) — the wallet proves each offline payment was
 * really created by this device. We generate one ECDSA P-256 keypair per
 * device (private key kept in localStorage; production would use a secure
 * enclave), and sign a canonical form of the transaction. sync-service
 * verifies the signature against the shipped public key and rejects
 * anything tampered with or forged.
 */
function toBase64Url(bytes) { return btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); }
async function loadOrCreateKey() {
  const stored = localStorage.getItem("paron-device-key");
  if (stored) return JSON.parse(stored);
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const privateKeyJwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  const publicKeyJwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  const bundle = { publicKeyJwk, privateKeyJwk };
  localStorage.setItem("paron-device-key", JSON.stringify(bundle));
  return bundle;
}
async function signCanonical(text) {
  const { privateKeyJwk } = await loadOrCreateKey();
  const privateKey = await crypto.subtle.importKey("jwk", privateKeyJwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, privateKey, encoder.encode(text));
  return toBase64Url(signature);
}
/*
 * One deterministic string both sides can rebuild independently. NUL can
 * never appear in the fields, so it's a safe separator. Amount is
 * normalized to the shortest plain decimal (150.00 -> "150", 150.50 ->
 * "150.5") — the backend must do the same before verifying.
 */
function canonicalString(transaction) {
  return [transaction.deviceTransactionId, transaction.offlineToken, transaction.merchantId || "", String(Number(transaction.amount)), transaction.transactedAt, transaction.deviceId || ""].join("\u0000");
}

async function activeToken() { const tokens = await database.all("tokens"); return tokens.find((token) => token.status === "ACTIVE" && new Date(token.expiresAt) > new Date()); }
async function spentOnToken(token) { const transactions = await database.all("transactions"); return transactions.filter((tx) => tx.offlineToken === token.token && ["ACCEPTED_OFFLINE", "QUEUED_FOR_SYNC", "SUBMITTED"].includes(tx.state)).reduce((sum, tx) => sum + Number(tx.amount), 0); }
async function remainingOnToken(token) { return Math.max(0, Number(token.maxAmount) - (await spentOnToken(token))); }
async function refreshBackend() { const base = apiBase(); const { userId } = currentSettings(); if (!base || !userId) { $("account-total").textContent = "Connect online to load"; $("account-detail").textContent = "Set the API base URL and customer ID to see your balance."; return; } try { const [balance, history, statuses] = await Promise.all([fetch(`${base}/api/v1/ledger/balance/${encodeURIComponent(userId)}`).then((r) => r.ok ? r.json() : null), fetch(`${base}/api/v1/tokens/history/${encodeURIComponent(userId)}`).then((r) => r.ok ? r.json() : null), fetch(`${base}/api/v1/sync/${encodeURIComponent(userId)}`).then((r) => r.ok ? r.json() : null)]); if (balance) { $("account-total").textContent = rupees(balance.totalBalance); $("account-detail").textContent = `${rupees(balance.availableBalance)} available · ${rupees(balance.reservedAmount)} reserved on this device`; } const historyEl = $("token-history"); if (history && history.length) { historyEl.innerHTML = history.map((token) => `<article class="transaction"><div><strong>${rupees(token.maxAmount)} token</strong><small>${new Date(token.issuedAt).toLocaleString()}</small></div><span class="chip ${statusClass(token.status)}">${escapeHtml(token.status)}</span></article>`).join(""); } else if (history) { historyEl.innerHTML = '<p class="hint">No tokens issued yet.</p>'; } if (statuses && statuses.length) { const byId = new Map(statuses.map((tx) => [tx.deviceTransactionId, tx])); let reconciled = 0; for (const tx of await database.all("transactions")) { const server = byId.get(tx.deviceTransactionId); if (server && tx.state !== server.status) { tx.state = server.status; if (server.rejectionReason) tx.rejectionReason = server.rejectionReason; await database.put("transactions", tx); reconciled += 1; } } if (reconciled) message(`${reconciled} payment(s) updated to their real server status.`); } } catch { $("account-total").textContent = "Offline / unreachable"; $("account-detail").textContent = "Reconnect to load your live balance."; } }
async function render() { const token = await activeToken(); $("token-amount").textContent = token ? rupees(token.maxAmount) : "No token loaded"; $("token-detail").textContent = token ? `${rupees(await remainingOnToken(token))} left · Expires ${new Date(token.expiresAt).toLocaleString()}` : "Connect online to request one."; $("token-state").textContent = token ? "ACTIVE" : "EMPTY"; $("token-state").className = `chip ${token ? "active" : "muted"}`; const transactions = (await database.all("transactions")).sort((a, b) => b.transactedAt.localeCompare(a.transactedAt)); $("transactions").innerHTML = transactions.length ? transactions.map((tx) => `<article class="transaction"><div><strong>${rupees(tx.amount)} → ${escapeHtml(tx.merchantId)}</strong><small>${new Date(tx.transactedAt).toLocaleString()} · ${escapeHtml(tx.deviceTransactionId.slice(0, 8))}${tx.rejectionReason ? " · " + escapeHtml(tx.rejectionReason) : ""}</small></div><span class="chip ${statusClass(tx.state)}">${escapeHtml(tx.state)}</span></article>`).join("") : '<p class="hint">No transactions yet.</p>'; $("pay").disabled = !token || (!$("simulation").checked && !merchantConnection); await refreshBackend(); }
function escapeHtml(value) { return String(value).replace(/[&<>"]/g, (char) => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;" }[char])); }
function currentSettings() { return { userId: $("user-id").value.trim(), apiBase: apiBase() }; }
async function saveSettings() { await database.put("settings", currentSettings(), "current"); }
async function createDemoToken() { const amount = Number($("new-token-amount").value); if (!Number.isFinite(amount) || amount <= 0) throw new Error("Enter a valid token amount."); const token = { tokenId: uuid(), token: `demo.${uuid()}.${uuid()}`, maxAmount: amount, expiresAt: new Date(Date.now() + Number($("expiry-hours").value || 6) * 3600000).toISOString(), status: "ACTIVE", demo: true }; await database.put("tokens", token); await saveSettings(); message("Demo token saved. You can now switch to airplane mode."); await render(); }
async function requestToken() { if (!apiBase()) throw new Error("Enter the API base URL first."); const response = await fetch(`${apiBase()}/api/v1/tokens/gettoken`, { method:"POST", headers:{"Content-Type":"application/json"}, body: JSON.stringify({ userId: $("user-id").value.trim(), amount:Number($("new-token-amount").value), expiryHours:Number($("expiry-hours").value) }) }); if (!response.ok) throw new Error(`Token request failed (${response.status}).`); const token = await response.json(); token.expiresAt = new Date(token.expiresAt).toISOString(); await database.put("tokens", token); await saveSettings(); message("Token received and stored for offline use."); await render(); }

class SimulatedTransport { async send(envelope) { await new Promise((resolve) => setTimeout(resolve, 700)); return { type:"PAYMENT_ACCEPTED", deviceTransactionId: envelope.transaction.deviceTransactionId, payloadHash: envelope.payloadHash, merchantReceiptId:`sim-${uuid()}`, receivedAt:new Date().toISOString() }; } }
class BluetoothTransport { constructor(characteristic) { this.characteristic = characteristic; this.pending = null; } async send(envelope) { const bytes = encoder.encode(JSON.stringify(envelope)); const frameSize = 175; const total = Math.ceil(bytes.length / frameSize); if (total > 255) throw new Error("Payment message is too large for BLE v1."); const receipt = new Promise((resolve, reject) => { const timer = setTimeout(() => reject(new Error("Merchant did not acknowledge within 20 seconds.")), 20000); this.pending = (data) => { clearTimeout(timer); resolve(data); }; }); for (let index = 0; index < total; index += 1) { const body = bytes.slice(index * frameSize, (index + 1) * frameSize); const frame = new Uint8Array(3 + body.length); frame[0] = 0x50; frame[1] = total; frame[2] = index; frame.set(body, 3); if (this.characteristic.writeValueWithoutResponse) await this.characteristic.writeValueWithoutResponse(frame); else await this.characteristic.writeValue(frame); } const response = await receipt; this.pending = null; return response; } }
async function connectMerchant() { if ($("simulation").checked) { merchantConnection = new SimulatedTransport(); $("ble-status").textContent = "Simulator connected."; message("Simulator merchant connected."); await render(); return; } if (!navigator.bluetooth) throw new Error("Web Bluetooth is unavailable. Use Chrome on Android or enable the simulator."); const device = await navigator.bluetooth.requestDevice({ filters:[{ services:[BLE_SERVICE] }] }); const server = await device.gatt.connect(); const service = await server.getPrimaryService(BLE_SERVICE); const offer = await service.getCharacteristic(OFFER_CHARACTERISTIC); const receipt = await service.getCharacteristic(RECEIPT_CHARACTERISTIC); const transport = new BluetoothTransport(offer); await receipt.startNotifications(); receipt.addEventListener("characteristicvaluechanged", (event) => { try { const reply = JSON.parse(decoder.decode(event.target.value)); if (transport.pending) transport.pending(reply); } catch { message("Received an unreadable merchant response."); } }); device.addEventListener("gattserverdisconnected", () => { merchantConnection = null; $("ble-status").textContent = "Merchant disconnected."; render(); }); merchantConnection = transport; $("ble-status").textContent = `Connected to ${device.name || "merchant phone"}.`; message("Merchant connected. You can pay while offline."); await render(); }
async function makePayment() { const token = await activeToken(); const amount = Number($("payment-amount").value); const merchantId = $("merchant-id").value.trim(); if (!token) throw new Error("Get a token while online before paying."); if (!merchantId || !Number.isFinite(amount) || amount <= 0) throw new Error("Enter a merchant and a valid amount."); const remaining = await remainingOnToken(token); if (amount > remaining) throw new Error(`Amount exceeds remaining limit of ${rupees(remaining)}.`); if (!merchantConnection) throw new Error("Connect to a merchant first."); const transaction = { deviceTransactionId: uuid(), offlineToken: token.token, amount: amount.toFixed(2), merchantId, transactedAt: new Date().toISOString().slice(0, 19), deviceId: deviceId() };
    const canonical = canonicalString(transaction);
    const signature = await signCanonical(canonical);
    transaction.signature = signature;
    transaction.publicKey = JSON.stringify((await loadOrCreateKey()).publicKeyJwk);
    const canonical2 = JSON.stringify(transaction); const envelope = { protocolVersion:"paron-ble-v1", type:"PAYMENT_OFFER", transaction, payloadHash: await sha256(canonical2) }; const record = { ...transaction, payloadHash: envelope.payloadHash, state:"SENDING" }; await database.put("transactions", record); await render(); try { const receipt = await merchantConnection.send(envelope); if (receipt.type !== "PAYMENT_ACCEPTED" || receipt.deviceTransactionId !== transaction.deviceTransactionId || receipt.payloadHash !== envelope.payloadHash) throw new Error("Merchant receipt did not match this payment."); record.state = "ACCEPTED_OFFLINE"; record.receipt = receipt; await database.put("transactions", record); message("Payment accepted by merchant. It will settle after sync."); } catch (error) { record.state = "SEND_FAILED"; record.error = error.message; await database.put("transactions", record); throw error; } finally { await render(); } }
async function syncNow() { const base = apiBase(); const { userId } = currentSettings(); if (!base || !userId) throw new Error("Set the API base URL and customer ID before syncing."); const transactions = (await database.all("transactions")).filter((tx) => ["ACCEPTED_OFFLINE", "QUEUED_FOR_SYNC"].includes(tx.state)); if (!transactions.length) { message("There are no accepted offline payments to sync."); return; } const payload = { transactions: transactions.map(({ deviceTransactionId, offlineToken, amount, merchantId, transactedAt, deviceId, signature, publicKey }) => ({ deviceTransactionId, offlineToken, amount:Number(amount), merchantId, transactedAt, deviceId, signature, publicKey })) }; const response = await fetch(`${base}/api/v1/sync/${encodeURIComponent(userId)}`, { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify(payload) }); if (!response.ok) throw new Error(`Sync failed (${response.status}). Your payments remain queued.`); const body = await response.json().catch(() => ({})); const accepted = new Set(body.acceptedDeviceTransactionIds || []); const acceptedCount = Number.isFinite(body.acceptedCount) ? body.acceptedCount : transactions.length; for (const tx of transactions) { if (accepted.size && !accepted.has(tx.deviceTransactionId)) continue; tx.state = "SUBMITTED"; await database.put("transactions", tx); } message(`${acceptedCount} payment(s) submitted for settlement.`); await render(); }
async function adjudicate() {
  const base = apiBase();
  const txnA = $("dispute-txn-a").value.trim();
  const txnB = $("dispute-txn-b").value.trim();
  if (!base) throw new Error("Set the API base URL first.");
  if (!txnA || !txnB) throw new Error("Enter two device transaction ids under dispute.");
  const response = await fetch(`${base}/api/v1/sync/adjudicate`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ deviceTransactionIds: [txnA, txnB] }) });
  if (!response.ok) throw new Error(`Adjudication failed (${response.status}).`);
  const verdict = await response.json();
  renderDispute(verdict);
  message(`AI Judge ruled: ${verdict.ruling}`);
}
function renderDispute(verdict) {
  const ruling = verdict.ruling;
  const tone = ruling === "FORGED_RECEIPT" || ruling === "DOUBLE_SPEND" ? "failed" : ruling === "INSUFFICIENT_EVIDENCE" ? "pending" : "active";
  const winner = verdict.winnerDeviceTransactionId ? `<div><strong>Winner</strong><small>${escapeHtml(verdict.winnerDeviceTransactionId)}</small></div>` : "";
  const loser = verdict.loserDeviceTransactionId ? `<div><strong>Loser</strong><small>${escapeHtml(verdict.loserDeviceTransactionId)}</small></div>` : "";
  const evidenceRows = (verdict.evidence || []).map((e) => `<div class="evidence-row ${e.passed ? "pass" : "fail"}"><span class="dot"></span><strong>${escapeHtml(e.check)}</strong><small>${escapeHtml(e.detail || "")}</small></div>`).join("");
  const amount = verdict.amountInQuestion !== null && Number.isFinite(Number(verdict.amountInQuestion)) ? rupees(verdict.amountInQuestion) : "In question";
  $("dispute-result").innerHTML = `<article class="transaction${tone ? ` verdict-${tone}` : ""}"><div><div class="verdict-head"><strong class="verdict-ruling">${escapeHtml(ruling.replace(/_/g, " "))}</strong><small>${escapeHtml(verdict.disputeId || "")} · ${amount} · confidence ${verdict.confidence}</small></div><p class="verdict-summary">${escapeHtml(verdict.summary || "")}</p>${winner}${loser}</div><span class="chip ${tone}">${tone.toUpperCase()}</span></article>${evidenceRows ? `<div class="evidence"><p class="hint">Evidence</p>${evidenceRows}</div>` : ""}`;
}
function run(action) { return () => action().catch((error) => { console.error(error); message(error.message || "Something went wrong."); render(); }); }
async function initialise() { const settings = await database.get("settings", "current"); if (settings) { $("user-id").value = settings.userId || "demo-customer"; $("api-base").value = settings.apiBase || ""; } $("connection-state").textContent = navigator.onLine ? "Online — token and sync available" : "Offline — payment queue available"; addEventListener("online", () => { $("connection-state").textContent = "Online — token and sync available"; }); addEventListener("offline", () => { $("connection-state").textContent = "Offline — payment queue available"; }); $("simulation").addEventListener("change", () => { merchantConnection = null; $("ble-status").textContent = $("simulation").checked ? "Simulator ready. No Bluetooth hardware needed." : "Use Connect merchant to select the merchant phone."; render(); }); $("create-demo-token").addEventListener("click", run(createDemoToken)); $("request-token").addEventListener("click", run(requestToken)); $("connect-merchant").addEventListener("click", run(connectMerchant)); $("pay").addEventListener("click", run(makePayment)); $("sync-now").addEventListener("click", run(syncNow)); $("resolve-dispute").addEventListener("click", run(adjudicate)); if ("serviceWorker" in navigator) navigator.serviceWorker.register("./service-worker.js"); await render(); }
initialise().catch((error) => message(`Could not open wallet: ${error.message}`));
