/* Judge-facing Paron walkthrough. Browser simulation and backend mode are kept distinct. */
const $ = (id) => document.getElementById(id);
const dbKey = "paron-judge-demo-v2";
const state = { backend: false, online: true, connected: false, merchantConnected: false, session: "", token: null, transactions: [], total: 1000, backendMerchantBalance: null, paypalLoaded: false, pendingReserve: null };
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
  if (!apiBase()) throw new Error("Enter your backend URL first.");
  const response = await fetch(`${apiBase()}${path}`, { ...init, headers: headers(init.headers || {}) });
  const text = await response.text();
  let data = null; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) throw new Error(data?.message || data?.detail || `The online service could not complete that request (${response.status}). Try again later.`);
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
    ? "<b>CONNECTED BACKEND MODE</b><span>Online actions use the backend URL below. The nearby handoff is still simulated; a payment is called complete only after the backend confirms it.</span>"
    : "<b>SAMPLE WALKTHROUGH</b><span>Balances and payments are examples stored in this browser. No backend receives them and no real money moves.</span>";
  description.textContent = state.backend ? "Uses backend services · their responses set payment status" : "Example data only · no backend records or real money";
  $("mode-description").style.color = state.backend ? "#287453" : "";
  $("paypal-status").textContent = state.backend ? "PayPal test checkout needs the funding service and public client ID configured on your backend." : "PayPal test checkout is available only when the connected backend is configured.";
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
  $("token-state").textContent = token ? "SET ASIDE" : "NONE SET ASIDE";
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
  document.querySelector(".merchant-metrics > div:first-child small").textContent = state.backend ? "RECEIVED OFFLINE" : "SAMPLE RECEIPTS";
  document.querySelector(".merchant-metrics > div:nth-child(2) small").textContent = state.backend ? "ADDED TO ACCOUNT" : "SAMPLE TOTAL";
  $("merchant-display-name").textContent = $("merchant-picker").selectedOptions[0]?.textContent || "Merchant";
  $("merchant-display-id").textContent = merchantId;
  $("receipt-count").textContent = String(merchantTx.length);
  $("merchant-inbox").innerHTML = merchantTx.length ? merchantTx.slice().reverse().map(t => `<div class="inbox-receipt"><span class="receipt-icon">✓</span><div><strong>${escapeHtml(t.statusLabel || (t.state === "SETTLED" ? "Added to merchant account" : "Received offline"))}</strong><small>${escapeHtml(t.id.slice(0,8))} · ${t.state === "SETTLED" ? "Confirmed online" : "Receipt saved in this browser"}</small></div><span class="receipt-amount">${money(t.amount)}</span></div>`).join("") : '<div class="empty-inbox"><span>⌁</span><p>Payment receipts will appear here.</p></div>';
  $("transactions").innerHTML = state.transactions.length ? state.transactions.slice().reverse().map(t => `<article class="transaction"><div><strong>${money(t.amount)} → ${escapeHtml(t.merchantName)}</strong><small>${new Date(t.createdAt).toLocaleString()} · ${escapeHtml(t.id.slice(0,8))}${t.reason ? ` · ${escapeHtml(t.reason)}` : ""}</small></div><span class="tx-badge-group ${t.state === "SETTLED" ? "settled" : ""}">${escapeHtml(t.stateLabel || t.state)}</span></article>`).join("") : '<p class="empty-inbox">No transactions yet.</p>';
  $("request-token").disabled = (state.backend && !state.session) || Boolean(state.pendingReserve && !state.pendingReserve.funded);
  $("new-token-amount").disabled = Boolean(state.pendingReserve?.funded);
  $("expiry-hours").disabled = Boolean(state.pendingReserve?.funded);
  $("sync-now").disabled = !pending.length || !state.online || (state.backend && !state.session);
}
function run(action) { return async () => { try { await action(); } catch (e) { console.error(e); notify(e.message || "Action failed."); render(); } }; }
function normalizeToken(data, amount, hours) {
  const t = data?.tokenData || data?.token || data;
  const tokenValue = typeof t === "string" ? t : (t?.token || t?.tokenValue || t?.tokenString || t?.offlineToken);
  const tokenId = t?.tokenId || t?.id || data?.tokenId || crypto.randomUUID();
  if (!tokenValue) throw new Error("Paron did not return an offline spending limit. Please try again later.");
  return { tokenId, token: tokenValue, maxAmount: Number(t?.maxAmount ?? t?.amount ?? amount), expiresAt: t?.expiresAt || new Date(Date.now() + hours*3600000).toISOString(), status: t?.status || "ACTIVE" };
}
async function signIn() {
  if (!state.backend) { state.connected = true; notify("Sample walkthrough ready. Choose an amount to set aside."); render(); return; }
  const username = $("demo-username").value.trim(), password = $("demo-password").value;
  const { data } = await api("/api/v1/auth/demo/session", { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({username,password}) });
  state.session = data.accessToken || data.token || "";
  if (!state.session) throw new Error("Sign-in response did not include a session token.");
  if (data.userId) $("user-id").value = data.userId;
  state.connected = true;
  try {
    const { response } = await api("/api/v1/ledger/accounts/create-test-account", { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({userId:userId(),initialBalance:1000}) });
    if (response.ok) notify(`Connected to the demo account for ${userId()}.`);
  } catch (e) {
    // Existing account is expected to reject duplicate creation; verify the actual balance next.
    try { const {data: balance} = await api(`/api/v1/ledger/balance/${encodeURIComponent(userId())}`); state.total=Number(balance.totalBalance ?? balance.balance ?? state.total); notify(`Connected. Example account balance: ${money(state.total)}.`); }
    catch { notify(`Signed in, but account setup/balance lookup failed: ${e.message}`); }
  }
  render();
}
async function reserve() {
  const amount = Number($("new-token-amount").value || 500), hours = Number($("expiry-hours").value || 6);
  if (!(amount > 0)) throw new Error("Enter a valid allowance amount.");
  if (state.backend) {
    if (!state.session) throw new Error("Sign in to the demo account before setting an offline limit.");
    if (state.pendingReserve?.funded && state.pendingReserve.amount === amount && state.pendingReserve.hours === hours) {
      await issueReservation(amount, hours);
      return;
    }
    const {data:config}=await api("/api/funding/paypal/config");
    if (!config.clientId) throw new Error("PayPal Sandbox is not configured on the funding service.");
    const rate=Number(config.walletCreditRate), maximum=Number(config.maximumAmount);
    if (!(rate > 0)) throw new Error("Funding service did not provide its wallet conversion rate.");
    const usdAmount=Math.ceil((amount/rate)*100)/100;
    if (Number.isFinite(maximum) && usdAmount > maximum) throw new Error(`This allowance needs $${usdAmount.toFixed(2)} in PayPal Sandbox funding, above the service limit of $${maximum.toFixed(2)}.`);
    if (state.pendingReserve && (state.pendingReserve.amount !== amount || state.pendingReserve.hours !== hours)) throw new Error("Finish or cancel the current PayPal checkout before changing the allowance.");
    if (!state.pendingReserve) state.pendingReserve={amount,hours,usdAmount,funded:false,orderId:null};
    await ensurePayPalSdk(config);
    const box=$("paypal-button-container");
    box.innerHTML="";
    $("paypal-status").textContent=`Approve a $${usdAmount.toFixed(2)} PayPal Sandbox payment (about ${money(usdAmount*rate)}). After capture, Paron will set aside ${money(amount)}.`;
    await window.paypal.Buttons({
      createOrder:async()=>{
        if (state.pendingReserve.orderId) return state.pendingReserve.orderId;
        const {data}=await api("/api/funding/paypal/orders",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({amount:usdAmount})});
        state.pendingReserve.orderId=data.paypalOrderId;
        return state.pendingReserve.orderId;
      },
      onApprove:async(data)=>{
        $("paypal-status").textContent="PayPal approved. Confirming the test payment and setting the allowance aside…";
        const {data:captured}=await api(`/api/funding/paypal/orders/${encodeURIComponent(data.orderID)}/capture`,{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"});
        if (String(captured.status||"").toUpperCase() !== "CREDITED") throw new Error("PayPal captured the payment, but Paron has not confirmed the wallet credit. Retry after checking the funding service.");
        state.pendingReserve.funded=true;
        try { const {data:balance}=await api(`/api/v1/ledger/balance/${encodeURIComponent(userId())}`); state.total=Number(balance.totalBalance??state.total); } catch {}
        try { await issueReservation(amount,hours); $("paypal-status").textContent="PayPal Sandbox payment confirmed and offline allowance reserved."; }
        catch (error) { render(); $("paypal-status").textContent=`Wallet funding succeeded, but allowance reservation failed: ${error.message} Click “Set offline spending limit” again to retry reservation without charging PayPal again.`; notify($("paypal-status").textContent); throw error; }
      },
      onCancel:()=>{ state.pendingReserve=null; $("paypal-status").textContent="PayPal checkout cancelled. No allowance was reserved."; render(); },
      onError:error=>{ console.error("PayPal reserve checkout failed",error); if (!state.pendingReserve?.orderId) state.pendingReserve=null; $("paypal-status").textContent="PayPal checkout could not finish. If PayPal already approved it, press the reserve button again to retry safely."; render(); }
    }).render(box);
    box.scrollIntoView({behavior:"smooth",block:"center"});
    notify("PayPal Sandbox checkout is ready. Approve the test payment to fund and reserve this allowance.");
  } else {
    state.connected = true;
    state.token={tokenId:crypto.randomUUID(),token:`paron-sim-${crypto.randomUUID()}`,maxAmount:amount,expiresAt:new Date(Date.now()+hours*3600000).toISOString(),status:"ACTIVE"};
    notify(`Sample limit set to ${money(amount)}. No backend record was created.`);
  }
  save(); render();
}
async function issueReservation(amount,hours) {
  const {data}=await api("/api/v1/tokens/gettoken",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({userId:userId(),deviceId:`web-demo-${getDeviceId()}`,amount,expiryHours:hours})});
  state.token=normalizeToken(data,amount,hours);
  state.total=Number(data.totalBalance??state.total);
  state.pendingReserve=null;
  $("paypal-button-container").innerHTML="";
  $("paypal-status").textContent=state.backend ? "PayPal Sandbox funding confirmed and offline allowance reserved." : "";
  notify(`PayPal Sandbox funding confirmed. Backend set aside ${money(amount)} for offline spending.`);
  save();render();
}
function getDeviceId(){let id=localStorage.getItem("paron-judge-device");if(!id){id=crypto.randomUUID();localStorage.setItem("paron-judge-device",id)}return id}
async function connectMerchant(){state.merchantConnected=true;notify("Merchant view is ready. This website simulates the nearby handoff.");$("ble-status").textContent="Website simulation · no Bluetooth connection or Android secure hardware is used.";}
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
  if(!token || !(amount>0) || amount>remaining()) throw new Error("Set an offline spending limit large enough for this payment first.");
  if(!state.merchantConnected) await connectMerchant();
  const tx={id:crypto.randomUUID(),deviceTransactionId:crypto.randomUUID(),offlineToken:token.token,amount,merchantId:id,merchantName:$("merchant-picker").selectedOptions[0]?.textContent||id,transactedAt:new Date().toISOString().slice(0,19),createdAt:new Date().toISOString(),deviceId:getDeviceId(),sourceMode:state.backend?"backend":"simulation",state:"PENDING_SYNC",stateLabel:state.backend?"Received offline · pending sync":"Simulated · pending sync"};
  if(state.backend){await signReceipt(tx);tx.userId=userId();}
  else {tx.signature="SIMULATED_SIGNATURE";tx.publicKey="SIMULATED_BROWSER_KEY";}
  state.transactions.push(tx);save();notify(state.backend?"Merchant received a sample website receipt. It is not settled until the backend confirms it.":"Sample receipt shown to the merchant. No real money moved.");render();
}
async function syncSimulation(pending){
  for(const tx of pending){setStatus(tx,"SIMULATED");tx.stateLabel="SAMPLE CHECK COMPLETE";tx.statusLabel="Sample check complete · no real account update";}
  save();$("sync-timeline-container").hidden=false;
  $("sync-timeline-container").querySelector(".timeline-label").textContent="LOCAL SIMULATION · NO BACKEND";
  $("step-settle").textContent="Simulated result";
  ["step-upload","step-sig","step-cap","step-settle"].forEach(id=>$(id).classList.add("done"));
  $("sync-timeline-status").textContent="Sample walkthrough complete. No backend received these receipts and no bank transfer occurred.";
  notify("Sample walkthrough complete. No payment was sent or recorded.");render();
}
async function syncBackend(pending){
  if(!state.session) throw new Error("Sign in to the demo account before sending receipts.");
  $("sync-timeline-container").hidden=false;$("sync-timeline-container").querySelector(".timeline-label").textContent="BACKEND RECEIPT CHECK";$("step-settle").textContent="Result";$("sync-timeline-status").textContent="Sending receipts to the backend…";
  ["step-upload","step-sig","step-cap","step-settle"].forEach(id=>$(id).classList.remove("done"));
  for(const [merchantId, merchantName] of new Map(pending.map(tx=>[tx.merchantId,tx.merchantName]))) {
    await api("/api/v1/ledger/merchants/register",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({merchantId,merchantName})});
  }
  const payload={transactions:pending.map(tx=>({deviceTransactionId:tx.deviceTransactionId,offlineToken:tx.offlineToken,amount:Number(tx.amount),merchantId:tx.merchantId,transactedAt:tx.transactedAt,deviceId:tx.deviceId,signature:tx.signature,publicKey:tx.publicKey}))};
  const {data}=await api(`/api/v1/sync/${encodeURIComponent(userId())}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
  $("step-upload").classList.add("done");
  if (Number(data.acceptedCount) === 0) throw new Error("Paron could not accept these receipts. They remain in the merchant inbox; please try syncing later.");
  const accepted=new Set(data.acceptedDeviceTransactionIds||[]);
  for(const tx of pending){if(accepted.has(tx.deviceTransactionId)){tx.state="RECEIVED";tx.stateLabel="Received by Paron · waiting for check";}else if(data.acceptedCount===undefined){tx.state="RECEIVED";tx.stateLabel="Sent · waiting for result";}}
  save();$("sync-timeline-status").textContent=`Paron received ${data.acceptedCount ?? accepted.size} receipt(s). Waiting for the online check.`;render();
  // Pull server status. HTTP 202 means queued, not settled.
  for(let attempt=0;attempt<8;attempt++){
    await new Promise(r=>setTimeout(r,1800));
    const {data:rows}=await api(`/api/v1/sync/${encodeURIComponent(userId())}`);
    const list=Array.isArray(rows)?rows:(rows?.transactions||[]), byId=new Map(list.map(x=>[x.deviceTransactionId,x]));
    for(const tx of pending){const remote=byId.get(tx.deviceTransactionId);if(!remote)continue;const status=String(remote.status||"").toUpperCase();tx.state=status||tx.state;tx.reason=remote.rejectionReason||tx.reason;tx.stateLabel=status==="SETTLED"?"Added to merchant account":status==="REJECTED"?"Receipt declined":status==="HELD_FOR_REVIEW"?"Needs review":status==="FAILED"?"Could not send receipt":status==="RECEIVED"||status==="PROCESSING"?"Receipt is being checked":"Result: "+status;tx.statusLabel=tx.stateLabel;}
    save();render();
    if(pending.every(tx=>["SETTLED","REJECTED","FAILED","HELD_FOR_REVIEW"].includes(tx.state)))break;
  }
  const statuses=new Set(pending.map(tx=>tx.state));
  if(statuses.has("SETTLED")){$("step-sig").classList.add("done");$("step-cap").classList.add("done");$("step-settle").classList.add("done");$("sync-timeline-status").textContent="The backend confirmed the payment in the merchant’s account record. This is not a bank transfer.";try{const merchantId=pending.find(tx=>tx.state==="SETTLED")?.merchantId;const {data:m}=await api(`/api/v1/ledger/merchants/${encodeURIComponent(merchantId)}`);state.backendMerchantBalance=Number(m.collectedBalance||0);const {data:b}=await api(`/api/v1/ledger/balance/${encodeURIComponent(userId())}`);state.total=Number(b.totalBalance??state.total);}catch(e){console.warn("Ledger balance refresh failed",e);}}
  else {$("sync-timeline-status").textContent="Receipt sent. The backend has not confirmed a result yet, so it remains pending.";}
  notify($("sync-timeline-status").textContent);render();
}
async function sync(){if(!state.online||!navigator.onLine)throw new Error("Reconnect before syncing receipts.");const pending=state.transactions.filter(t=>t.sourceMode===(state.backend?"backend":"simulation")&&!["SETTLED","SIMULATED","REJECTED","FAILED"].includes(t.state));if(!pending.length)throw new Error("No pending receipts to sync in the selected mode.");if(state.backend)await syncBackend(pending);else await syncSimulation(pending);}
async function ensurePayPalSdk(config){
  if(window.paypal)return;
  if(state.paypalLoaded)throw new Error("PayPal checkout is still loading. Try again in a moment.");
  state.paypalLoaded=true;
  try { await new Promise((resolve,reject)=>{const s=document.createElement("script");s.src=`https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(config.clientId)}&currency=${encodeURIComponent(config.currency||"USD")}&intent=capture`;s.onload=resolve;s.onerror=()=>reject(new Error("Could not load PayPal Sandbox checkout."));document.head.appendChild(s);}); }
  catch(error){state.paypalLoaded=false;throw error;}
}
function restore(){try{const d=JSON.parse(localStorage.getItem(dbKey)||"{}");state.token=d.token||null;state.transactions=d.transactions||[];state.total=d.total??1000;state.merchantSettled=d.merchantSettled||0;}catch{}}
function init(){restore();$("backend-mode").addEventListener("change",e=>{state.backend=e.target.checked;state.session="";state.token=null;state.total=1000;state.pendingReserve=null;$("paypal-button-container").innerHTML="";$("session-token").value="";save();updateMode();notify(state.backend?"Backend mode selected. Enter the API gateway URL, connect the account, then reserve a backend token.":"Simulation mode selected. Reserve a sample allowance to continue.");});$("demo-sign-in").addEventListener("click",run(signIn));$("request-token").addEventListener("click",run(reserve));$("connect-merchant").addEventListener("click",run(connectMerchant));$("pay").addEventListener("click",run(payOffline));$("sync-now").addEventListener("click",run(sync));$("payment-amount").addEventListener("input",render);$("merchant-picker").addEventListener("change",e=>{$("merchant-id").value=e.target.value;render();});$("demo-network-toggle").addEventListener("click",()=>{state.online=!state.online;notify(state.online?"Network restored. Ready to sync pending receipts.":"Offline simulation active. Online services are unavailable.");render();});window.addEventListener("online",()=>{state.online=true;render();});window.addEventListener("offline",()=>{state.online=false;render();});if("serviceWorker"in navigator)navigator.serviceWorker.register("./service-worker.js").catch(()=>{});updateMode();render();}
window.addEventListener("DOMContentLoaded",init);
