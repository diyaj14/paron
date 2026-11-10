/* Judge-facing Paron walkthrough. Browser simulation and backend mode are kept distinct. */
const $ = (id) => document.getElementById(id);
const dbKey = "paron-judge-demo-v2";
const state = { backend: false, online: true, connected: false, merchantConnected: false, session: "", token: null, transactions: [], total: 1000, backendMerchantBalance: null, paypalLoaded: false };
const apiBase = () => $("api-base").value.trim().replace(/\/$/, "");
const userId = () => $("user-id").value.trim() || "demo-customer";
const money = (n) => `₹${Number(n || 0).toFixed(2)}`;
const escapeHtml = (s) => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const activeToken = () => state.token && state.token.status === "ACTIVE" ? state.token : null;
function notify(text) { $("notice").textContent = text; }
function save() { localStorage.setItem(dbKey, JSON.stringify({ token: state.token, transactions: state.transactions, total: state.total, merchantSettled: state.merchantSettled })); }
function headers(extra = {}) { return { ...(state.session ? { Authorization: `Bearer ${state.session}` } : {}), ...extra }; }
async function api(path, init = {}) {
  if (!state.online || !navigator.onLine) throw new Error("This action needs a network connection. Reconnect and try again.");
  if (!apiBase()) throw new Error("Enter your deployed API gateway URL first.");
  const response = await fetch(`${apiBase()}${path}`, { ...init, headers: headers(init.headers || {}) });
  const text = await response.text();
  let data = null; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) throw new Error(data?.message || data?.detail || `API request failed (${response.status}).`);
  return { response, data };
}
function updateNetwork() {
  const online = state.online && navigator.onLine;
  const pill = $("network-indicator");
  pill.classList.toggle("offline", !online);
  $("connection-state").textContent = online ? "Online" : "Offline (simulated)";
  $("connection-state-copy").textContent = online ? "Connected · ready to sync" : "Offline · local allowance available";
  $("demo-network-toggle").textContent = online ? "Simulate offline" : "Reconnect";
  $("demo-network-toggle").disabled = !state.backend && false;
}
function updateMode() {
  const banner = $("truth-banner"), description = $("mode-description");
  banner.className = `truth-banner ${state.backend ? "backend-banner" : "simulation-banner"}`;
  banner.innerHTML = state.backend
    ? "<b>CONNECTED BACKEND MODE</b><span>Online actions call the configured gateway. Offline receipt handoff is still browser-simulated; settlement is shown only after backend status confirms it.</span>"
    : "<b>SIMULATED WALKTHROUGH</b><span>This browser simulation uses sample balances, a local receipt, and local reconciliation. No real funds move.</span>";
  description.textContent = state.backend ? "Gateway mode · API responses determine status" : "Local simulation · no backend records or real funds";
  $("mode-description").style.color = state.backend ? "#287453" : "";
  $("paypal-status").textContent = state.backend ? "PayPal Sandbox needs a configured funding service and client ID on your gateway." : "PayPal Sandbox funding is available only in connected backend mode after the funding service is configured.";
  render();
}
function spent() { return state.transactions.filter(t => t.state !== "REJECTED" && t.state !== "FAILED").reduce((sum,t) => sum + Number(t.amount), 0); }
function remaining() { const token = activeToken(); return token ? Math.max(0, Number(token.maxAmount || 0) - spent()) : 0; }
function setStatus(tx, status, reason) { tx.state = status; if (reason) tx.reason = reason; if (status === "SETTLED") tx.settledAt = new Date().toISOString(); }
function render() {
  updateNetwork();
  const token = activeToken(), locked = token ? remaining() : 0;
  $("account-total").textContent = money(state.total);
  $("locked-offline-amount").textContent = money(locked);
  $("available-online-amount").textContent = money(Math.max(0, state.total - Number(token?.maxAmount || 0)));
  $("token-state").textContent = token ? "ACTIVE" : "NOT RESERVED";
  $("offline-allowance-display").textContent = money(locked);
  const amount = Number($("payment-amount").value || 0);
  $("remaining-limit-display").textContent = money(Math.max(0, locked - amount));
  $("pay").disabled = !token || locked < amount || (state.backend && !state.session);
  $("pay").innerHTML = `Pay offline · ${money(amount)} <span>→</span>`;
  const pending = state.transactions.filter(t => t.sourceMode === (state.backend ? "backend" : "simulation") && !["SETTLED","SIMULATED","REJECTED","FAILED"].includes(t.state));
  const merchantId = $("merchant-id").value;
  const merchantTx = state.transactions.filter(t => t.merchantId === merchantId);
  const received = merchantTx.reduce((s,t) => s + Number(t.amount), 0);
  const settled = merchantTx.filter(t => t.state === "SETTLED").reduce((s,t) => s + Number(t.amount), 0);
  const simulatedSettled = merchantTx.filter(t => t.state === "SIMULATED").reduce((s,t) => s + Number(t.amount), 0);
  $("merchant-pending-total").textContent = money(received - (state.backend ? settled : simulatedSettled));
  $("merchant-settled-total").textContent = state.backend ? (state.backendMerchantBalance == null ? "—" : money(state.backendMerchantBalance)) : money(simulatedSettled);
  document.querySelector(".merchant-metrics > div:first-child small").textContent = state.backend ? "RECEIVED OFFLINE" : "SIMULATED RECEIPTS";
  document.querySelector(".merchant-metrics > div:nth-child(2) small").textContent = state.backend ? "LEDGER SETTLED" : "SIMULATED TOTAL";
  $("merchant-display-name").textContent = $("merchant-picker").selectedOptions[0]?.textContent || "Merchant";
  $("merchant-display-id").textContent = merchantId;
  $("receipt-count").textContent = String(merchantTx.length);
  $("merchant-inbox").innerHTML = merchantTx.length ? merchantTx.slice().reverse().map(t => `<div class="inbox-receipt"><span class="receipt-icon">✓</span><div><strong>${escapeHtml(t.statusLabel || (t.state === "SETTLED" ? "Ledger settled" : "Received offline"))}</strong><small>${escapeHtml(t.id.slice(0,8))} · ${t.state === "SETTLED" ? "Backend ledger" : "Local browser receipt"}</small></div><span class="receipt-amount">${money(t.amount)}</span></div>`).join("") : '<div class="empty-inbox"><span>⌁</span><p>The merchant’s offline receipts will appear here.</p></div>';
  $("transactions").innerHTML = state.transactions.length ? state.transactions.slice().reverse().map(t => `<article class="transaction"><div><strong>${money(t.amount)} → ${escapeHtml(t.merchantName)}</strong><small>${new Date(t.createdAt).toLocaleString()} · ${escapeHtml(t.id.slice(0,8))}${t.reason ? ` · ${escapeHtml(t.reason)}` : ""}</small></div><span class="tx-badge-group ${t.state === "SETTLED" ? "settled" : ""}">${escapeHtml(t.stateLabel || t.state)}</span></article>`).join("") : '<p class="empty-inbox">No transactions yet.</p>';
  $("request-token").disabled = state.backend && !state.session;
  $("sync-now").disabled = !pending.length || !state.online || (state.backend && !state.session);
}
function run(action) { return async () => { try { await action(); } catch (e) { console.error(e); notify(e.message || "Action failed."); render(); } }; }
function normalizeToken(data, amount, hours) {
  const t = data?.tokenData || data?.token || data;
  const tokenValue = typeof t === "string" ? t : (t?.token || t?.tokenValue || t?.tokenString || t?.offlineToken);
  const tokenId = t?.tokenId || t?.id || data?.tokenId || crypto.randomUUID();
  if (!tokenValue) throw new Error("Token endpoint returned no token value. Check token-service response format.");
  return { tokenId, token: tokenValue, maxAmount: Number(t?.maxAmount ?? t?.amount ?? amount), expiresAt: t?.expiresAt || new Date(Date.now() + hours*3600000).toISOString(), status: t?.status || "ACTIVE" };
}
async function signIn() {
  if (!state.backend) { state.connected = true; notify("Simulation ready. Choose an allowance to reserve."); render(); return; }
  const username = $("demo-username").value.trim(), password = $("demo-password").value;
  const { data } = await api("/api/v1/auth/demo/session", { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({username,password}) });
  state.session = data.accessToken || data.token || "";
  if (!state.session) throw new Error("Sign-in response did not include a session token.");
  if (data.userId) $("user-id").value = data.userId;
  state.connected = true;
  try {
    const { response } = await api("/api/v1/ledger/accounts/create-test-account", { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({userId:userId(),initialBalance:1000}) });
    if (response.ok) notify(`Backend session active for ${userId()}; demo account checked.`);
  } catch (e) {
    // Existing account is expected to reject duplicate creation; verify the actual balance next.
    try { const {data: balance} = await api(`/api/v1/ledger/balance/${encodeURIComponent(userId())}`); state.total=Number(balance.totalBalance ?? balance.balance ?? state.total); notify(`Backend session active. Account balance: ${money(state.total)}.`); }
    catch { notify(`Signed in, but account setup/balance lookup failed: ${e.message}`); }
  }
  render();
}
async function reserve() {
  const amount = Number($("new-token-amount").value || 500), hours = Number($("expiry-hours").value || 6);
  if (!(amount > 0)) throw new Error("Enter a valid allowance amount.");
  if (state.backend) {
    if (!state.session) throw new Error("Connect the backend demo account before reserving.");
    const {data} = await api("/api/v1/tokens/gettoken", { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({userId:userId(),deviceId:`web-demo-${getDeviceId()}`,amount,expiryHours:hours}) });
    state.token = normalizeToken(data,amount,hours);
    state.total=Number(data.totalBalance ?? state.total);
    notify(`Backend issued an offline allowance of ${money(amount)}.`);
  } else {
    state.connected = true;
    state.token={tokenId:crypto.randomUUID(),token:`paron-sim-${crypto.randomUUID()}`,maxAmount:amount,expiresAt:new Date(Date.now()+hours*3600000).toISOString(),status:"ACTIVE"};
    notify(`Simulation allowance reserved: ${money(amount)}. No backend token was created.`);
  }
  save(); render();
}
function getDeviceId(){let id=localStorage.getItem("paron-judge-device");if(!id){id=crypto.randomUUID();localStorage.setItem("paron-judge-device",id)}return id}
async function connectMerchant(){state.merchantConnected=true;notify("Merchant view is ready. Browser handoff is simulated; no BLE terminal is connected.");$("ble-status").textContent="Browser transport simulation · not physical BLE or Android hardware-backed signing.";}
async function signReceipt(tx) {
  if (!crypto.subtle) throw new Error("This browser needs HTTPS to sign a demo receipt.");
  const pair=await crypto.subtle.generateKey({name:"ECDSA",namedCurve:"P-256"},true,["sign","verify"]);
  const canonical=[tx.deviceTransactionId,tx.offlineToken,tx.merchantId,String(Number(tx.amount)),tx.transactedAt,tx.deviceId].join("\u0000");
  const signature=await crypto.subtle.sign({name:"ECDSA",hash:"SHA-256"},pair.privateKey,new TextEncoder().encode(canonical));
  const publicJwk=await crypto.subtle.exportKey("jwk",pair.publicKey);
  tx.signature=btoa(String.fromCharCode(...new Uint8Array(signature))).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/g,"");tx.publicKey=JSON.stringify(publicJwk);
}
async function payOffline(){
  const token=activeToken(), amount=Number($("payment-amount").value), id=$("merchant-id").value;
  if(!token || !(amount>0) || amount>remaining()) throw new Error("Reserve an allowance large enough for this payment first.");
  if(!state.merchantConnected) await connectMerchant();
  const tx={id:crypto.randomUUID(),deviceTransactionId:crypto.randomUUID(),offlineToken:token.token,amount,merchantId:id,merchantName:$("merchant-picker").selectedOptions[0]?.textContent||id,transactedAt:new Date().toISOString().slice(0,19),createdAt:new Date().toISOString(),deviceId:getDeviceId(),sourceMode:state.backend?"backend":"simulation",state:"PENDING_SYNC",stateLabel:state.backend?"Received offline · pending sync":"Simulated · pending sync"};
  if(state.backend){await signReceipt(tx);tx.userId=userId();}
  else {tx.signature="SIMULATED_SIGNATURE";tx.publicKey="SIMULATED_BROWSER_KEY";}
  state.transactions.push(tx);save();notify(state.backend?"Merchant received a browser-simulated receipt. It is not settled until backend sync confirms it.":"Simulated receipt handed to merchant. No real money moved.");render();
}
async function syncSimulation(pending){
  for(const tx of pending){setStatus(tx,"SIMULATED");tx.stateLabel="SIMULATED RECONCILIATION";tx.statusLabel="Simulated reconciliation · not a ledger entry";}
  save();$("sync-timeline-container").hidden=false;
  $("sync-timeline-container").querySelector(".timeline-label").textContent="LOCAL SIMULATION · NO BACKEND";
  $("step-settle").textContent="Simulated result";
  ["step-upload","step-sig","step-cap","step-settle"].forEach(id=>$(id).classList.add("done"));
  $("sync-timeline-status").textContent="Simulation complete. No backend received these receipts and no bank transfer occurred.";
  notify("Simulation finished. The merchant ledger number is illustrative only.");render();
}
async function syncBackend(pending){
  if(!state.session) throw new Error("Connect the backend demo account before syncing.");
  $("sync-timeline-container").hidden=false;$("sync-timeline-container").querySelector(".timeline-label").textContent="BACKEND RECONCILIATION";$("step-settle").textContent="Ledger status";$("sync-timeline-status").textContent="Uploading receipts to the sync service…";
  ["step-upload","step-sig","step-cap","step-settle"].forEach(id=>$(id).classList.remove("done"));
  for(const [merchantId, merchantName] of new Map(pending.map(tx=>[tx.merchantId,tx.merchantName]))) {
    await api("/api/v1/ledger/merchants/register",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({merchantId,merchantName})});
  }
  const payload={transactions:pending.map(tx=>({deviceTransactionId:tx.deviceTransactionId,offlineToken:tx.offlineToken,amount:Number(tx.amount),merchantId:tx.merchantId,transactedAt:tx.transactedAt,deviceId:tx.deviceId,signature:tx.signature,publicKey:tx.publicKey}))};
  const {data}=await api(`/api/v1/sync/${encodeURIComponent(userId())}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
  $("step-upload").classList.add("done");
  if (Number(data.acceptedCount) === 0) throw new Error(data.message || "The sync service accepted no receipts. They remain pending in the merchant inbox.");
  const accepted=new Set(data.acceptedDeviceTransactionIds||[]);
  for(const tx of pending){if(accepted.has(tx.deviceTransactionId)){tx.state="RECEIVED";tx.stateLabel="QUEUED FOR RECONCILIATION";}else if(data.acceptedCount===undefined){tx.state="RECEIVED";tx.stateLabel="Submitted · awaiting status";}}
  save();$("sync-timeline-status").textContent=data.message||`${data.acceptedCount ?? accepted.size} receipt(s) accepted by sync service; waiting for settlement status.`;render();
  // Pull server status. HTTP 202 means queued, not settled.
  for(let attempt=0;attempt<8;attempt++){
    await new Promise(r=>setTimeout(r,1800));
    const {data:rows}=await api(`/api/v1/sync/${encodeURIComponent(userId())}`);
    const list=Array.isArray(rows)?rows:(rows?.transactions||[]), byId=new Map(list.map(x=>[x.deviceTransactionId,x]));
    for(const tx of pending){const remote=byId.get(tx.deviceTransactionId);if(!remote)continue;const status=String(remote.status||"").toUpperCase();tx.state=status||tx.state;tx.reason=remote.rejectionReason||tx.reason;tx.stateLabel=status==="SETTLED"?"SETTLED · BACKEND LEDGER":status==="REJECTED"?"REJECTED BY BACKEND":status==="HELD_FOR_REVIEW"?"HELD FOR REVIEW":status==="FAILED"?"SYNC FAILED":status==="RECEIVED"||status==="PROCESSING"?"PROCESSING · PENDING":"BACKEND STATUS: "+status;tx.statusLabel=tx.stateLabel;}
    save();render();
    if(pending.every(tx=>["SETTLED","REJECTED","FAILED","HELD_FOR_REVIEW"].includes(tx.state)))break;
  }
  const statuses=new Set(pending.map(tx=>tx.state));
  if(statuses.has("SETTLED")){$("step-sig").classList.add("done");$("step-cap").classList.add("done");$("step-settle").classList.add("done");$("sync-timeline-status").textContent="Backend reports SETTLED. The merchant ledger was updated; this does not mean a bank transfer occurred.";try{const merchantId=pending.find(tx=>tx.state==="SETTLED")?.merchantId;const {data:m}=await api(`/api/v1/ledger/merchants/${encodeURIComponent(merchantId)}`);state.backendMerchantBalance=Number(m.collectedBalance||0);const {data:b}=await api(`/api/v1/ledger/balance/${encodeURIComponent(userId())}`);state.total=Number(b.totalBalance??state.total);}catch(e){console.warn("Ledger balance refresh failed",e);}}
  else {$("sync-timeline-status").textContent="Sync submitted. Backend has not reported settlement yet; the merchant receipt remains pending.";}
  notify($("sync-timeline-status").textContent);render();
}
async function sync(){if(!state.online||!navigator.onLine)throw new Error("Reconnect before syncing receipts.");const pending=state.transactions.filter(t=>t.sourceMode===(state.backend?"backend":"simulation")&&!["SETTLED","SIMULATED","REJECTED","FAILED"].includes(t.state));if(!pending.length)throw new Error("No pending receipts to sync in the selected mode.");if(state.backend)await syncBackend(pending);else await syncSimulation(pending);}
async function loadPayPal(){
  if(!state.backend)throw new Error("Switch to connected backend mode before using PayPal Sandbox.");if(!state.session)throw new Error("Connect the backend demo account first.");
  const {data:config}=await api("/api/funding/paypal/config");if(!config.clientId)throw new Error("PayPal client ID is not configured on the backend.");
  if(!window.paypal){await new Promise((resolve,reject)=>{const s=document.createElement("script");s.src=`https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(config.clientId)}&currency=${encodeURIComponent(config.currency||"USD")}&intent=capture`;s.onload=resolve;s.onerror=()=>reject(new Error("Could not load PayPal Sandbox checkout."));document.head.appendChild(s);});}
  const box=$("paypal-button-container");box.innerHTML="";
  window.paypal.Buttons({createOrder:async()=>{const amount=Number($("paypal-funding-amount").value||10);const {data}=await api("/api/funding/paypal/orders",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({amount})});return data.paypalOrderId;},onApprove:async(data)=>{await api(`/api/funding/paypal/orders/${encodeURIComponent(data.orderID)}/capture`,{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"});$("paypal-status").textContent="PayPal Sandbox capture confirmed by funding backend. Refresh account balance to see ledger changes.";try{const {data:b}=await api(`/api/v1/ledger/balance/${encodeURIComponent(userId())}`);state.total=Number(b.totalBalance??state.total);render();}catch{}notify("PayPal Sandbox capture returned successfully.");},onError:err=>{console.error(err);$("paypal-status").textContent="PayPal did not complete. Check funding service logs.";}}).render(box);
  $("paypal-status").textContent="PayPal Sandbox checkout loaded. Approve with a Sandbox buyer account.";
}
function restore(){try{const d=JSON.parse(localStorage.getItem(dbKey)||"{}");state.token=d.token||null;state.transactions=d.transactions||[];state.total=d.total??1000;state.merchantSettled=d.merchantSettled||0;}catch{}}
function init(){restore();$("backend-mode").addEventListener("change",e=>{state.backend=e.target.checked;state.session="";state.token=null;state.total=1000;$("session-token").value="";save();updateMode();notify(state.backend?"Backend mode selected. Enter the API gateway URL, connect the account, then reserve a backend token.":"Simulation mode selected. Reserve a sample allowance to continue.");});$("demo-sign-in").addEventListener("click",run(signIn));$("request-token").addEventListener("click",run(reserve));$("connect-merchant").addEventListener("click",run(connectMerchant));$("pay").addEventListener("click",run(payOffline));$("sync-now").addEventListener("click",run(sync));$("load-paypal").addEventListener("click",run(loadPayPal));$("payment-amount").addEventListener("input",render);$("merchant-picker").addEventListener("change",e=>{$("merchant-id").value=e.target.value;render();});$("demo-network-toggle").addEventListener("click",()=>{state.online=!state.online;notify(state.online?"Network restored. Ready to sync pending receipts.":"Offline simulation active. Online services are unavailable.");render();});window.addEventListener("online",()=>{state.online=true;render();});window.addEventListener("offline",()=>{state.online=false;render();});if("serviceWorker"in navigator)navigator.serviceWorker.register("./service-worker.js").catch(()=>{});updateMode();render();}
window.addEventListener("DOMContentLoaded",init);
