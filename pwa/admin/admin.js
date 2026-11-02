/* Paron Admin — fraud review queue (human in the loop). */
const $ = (id) => document.getElementById(id);
function escapeHtml(value) { return String(value).replace(/[&<>"]/g, (char) => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;" }[char])); }
function rupees(amount) { return `₹${Number(amount).toFixed(2)}`; }
function message(text) { $("notice").textContent = text; }
function apiBase() { return $("api-base").value.trim().replace(/\/$/, ""); }
function statusTone(state) {
  const s = String(state).toUpperCase();
  if (/REJECTED|FAILED/.test(s)) return "failed";
  if (/REVIEWED|SETTLED|APPROVED/.test(s)) return "active";
  if (/PENDING|HELD|HOLD|REVIEW/.test(s)) return "review";
  return "muted";
}
function statusLabel(state) { return { PENDING: "Pending review", HOLD_FOR_REVIEW: "Held for review", REVIEWED: "Reviewed", APPROVED: "Approved", REJECTED: "Rejected" }[String(state)] || String(state).replace(/_/g, " "); }
function chip(state) { return `<span class="chip status ${statusTone(state)}"><span class="dot"></span>${escapeHtml(statusLabel(state))}</span>`; }

/* Demo data — used when no API base URL is entered, so the queue UI can be
 * shown fully without any service running. Flagged in the UI as sample data. */
function sampleAlerts() {
  const now = Date.now();
  return [
    { id: "a1b2c3d4-e5f6-4a5b-8c9d-000000000001", transactionId: "txn-replay-8f2a1c", userId: "user_demo_4821", amount: 50.00, riskScore: 0.97, riskLevel: "HIGH", triggeredRules: "TOKEN_REUSE", status: "PENDING", createdAt: new Date(now - 14 * 60000).toISOString(), reviewedAt: null, reviewerNotes: null },
    { id: "a1b2c3d4-e5f6-4a5b-8c9d-000000000002", transactionId: "txn-newdev-71b9e0", userId: "user_never_seen_901", amount: 50.00, riskScore: 0.21, riskLevel: "LOW", triggeredRules: "", status: "HOLD_FOR_REVIEW", createdAt: new Date(now - 9 * 60000).toISOString(), reviewedAt: null, reviewerNotes: null },
    { id: "a1b2c3d4-e5f6-4a5b-8c9d-000000000003", transactionId: "txn-vel-3cd47a", userId: "user_demo_4821", amount: 1200.00, riskScore: 0.88, riskLevel: "HIGH", triggeredRules: "AMOUNT_ANOMALY,VELOCITY", status: "PENDING", createdAt: new Date(now - 32 * 60000).toISOString(), reviewedAt: null, reviewerNotes: null },
    { id: "a1b2c3d4-e5f6-4a5b-8c9d-000000000004", transactionId: "txn-ok-52e308", userId: "user_control_77", amount: 200.00, riskScore: 0.04, riskLevel: "LOW", triggeredRules: "", status: "REVIEWED", createdAt: new Date(now - 70 * 60000).toISOString(), reviewedAt: new Date(now - 40 * 60000).toISOString(), reviewerNotes: "false positive - trusted merchant" }
  ];
}

function renderAlerts(alerts, demo) {
  const list = $("fraud-alerts");
  if (!alerts.length) { list.innerHTML = '<p class="hint">Queue is clear — no alerts to review.</p>'; return; }
  list.innerHTML = (demo ? `<p class="hint badge-chip">DEMO DATA — no services running</p>` : "") + alerts.map((alert) => `
    <article class="transaction">
      <div>
        <strong>${escapeHtml(alert.transactionId || alert.id)}</strong>
        <small>user ${escapeHtml(alert.userId || "?")} · ${rupees(alert.amount)} · score ${Number(alert.riskScore).toFixed(2)} · ${escapeHtml(alert.riskLevel || "")}</small>
        ${alert.triggeredRules ? `<small>rules: ${escapeHtml(alert.triggeredRules)}</small>` : ""}
        ${alert.reviewerNotes ? `<small>reviewer note: ${escapeHtml(alert.reviewerNotes)}</small>` : ""}
        <small>${new Date(alert.createdAt).toLocaleString()}${alert.reviewedAt ? ` · reviewed ${new Date(alert.reviewedAt).toLocaleString()}` : ""}</small>
      </div>
      <div class="alert-side">${chip(alert.status)}
      ${alert.status !== "REVIEWED" ? `<button class="small secondary" data-review="${escapeHtml(alert.id)}">Mark reviewed</button>` : ""}</div>
    </article>`).join("");
  list.querySelectorAll("[data-review]").forEach((button) => button.addEventListener("click", () => reviewAlert(button.dataset.review)));
}

async function loadAlerts() {
  const base = apiBase();
  if (!base) {
    renderAlerts(sampleAlerts(), true);
    message("Showing sample queue (no API base set). Enter your gateway URL for live alerts.");
    return;
  }
  const response = await fetch(`${base}/api/v1/fraud/alerts`);
  if (!response.ok) throw new Error(`Failed to load alerts (${response.status}).`);
  renderAlerts(await response.json(), false);
  message("Queue refreshed.");
}

async function reviewAlert(id) {
  const base = apiBase();
  const list = $("fraud-alerts");
  if (!base) {
    const next = sampleAlerts().map((alert) => alert.id === id ? { ...alert, status: "REVIEWED", reviewerNotes: "Reviewed by operator", reviewedAt: new Date().toISOString() } : alert);
    renderAlerts(next, true);
    message("Alert marked reviewed (demo).");
    return;
  }
  const response = await fetch(`${base}/api/v1/fraud/alerts/${encodeURIComponent(id)}/review`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status: "REVIEWED", reviewerNotes: "Reviewed by operator" }) });
  if (!response.ok) throw new Error(`Review failed (${response.status}).`);
  message("Alert marked reviewed.");
  await loadAlerts();
}

function renderDispute(verdict) {
  const ruling = verdict.ruling;
  const tone = ruling === "FORGED_RECEIPT" || ruling === "DOUBLE_SPEND" ? "failed" : ruling === "INSUFFICIENT_EVIDENCE" ? "pending" : "active";
  const winner = verdict.winnerDeviceTransactionId ? `<div><strong>Winner</strong><small>${escapeHtml(verdict.winnerDeviceTransactionId)}</small></div>` : "";
  const loser = verdict.loserDeviceTransactionId ? `<div><strong>Loser</strong><small>${escapeHtml(verdict.loserDeviceTransactionId)}</small></div>` : "";
  const evidenceRows = (verdict.evidence || []).map((e) => `<div class="evidence-row ${e.passed ? "pass" : "fail"}"><span class="dot"></span><strong>${escapeHtml(e.check)}</strong><small>${escapeHtml(e.detail || "")}</small></div>`).join("");
  const amount = verdict.amountInQuestion != null && Number.isFinite(Number(verdict.amountInQuestion)) ? rupees(verdict.amountInQuestion) : "In question";
  $("dispute-result").innerHTML = `<article class="transaction${tone ? ` verdict-${tone}` : ""}"><div><div class="verdict-head"><strong class="verdict-ruling">${escapeHtml(ruling.replace(/_/g, " "))}</strong><small>${escapeHtml(verdict.disputeId || "")} · ${amount} · confidence ${verdict.confidence}</small></div><p class="verdict-summary">${escapeHtml(verdict.summary || "")}</p>${winner}${loser}</div><span class="chip ${tone}">${tone.toUpperCase()}</span></article>${evidenceRows ? `<div class="evidence"><p class="hint">Evidence</p>${evidenceRows}</div>` : ""}`;
}

function sampleVerdict() {
  return {
    disputeId: "demo-dispute-0001",
    ruling: "DOUBLE_SPEND",
    winnerDeviceTransactionId: "txn-a-9f2b77",
    loserDeviceTransactionId: "txn-b-3c8d91",
    amountInQuestion: 400,
    confidence: 0.83,
    binding: true,
    summary: "Ruling: DOUBLE_SPEND. Receipt txn-a-9f2b77 is the earlier, valid claim; txn-b-3c8d91 spent the same reserved token a second time. The later payment is reclaimed and never settles.",
    evidence: [
      { check: "SIGNATURE_TXN_A", passed: true, detail: "txn-a-9f2b77 signature verified" },
      { check: "SIGNATURE_TXN_B", passed: true, detail: "txn-b-3c8d91 signature verified" },
      { check: "SAME_TOKEN", passed: true, detail: "both receipts reference the same offline token" },
      { check: "SETTLED_TXN_A", passed: true, detail: "status=SETTLED" },
      { check: "SETTLED_TXN_B", passed: true, detail: "status=SETTLED" },
      { check: "TOKEN_OVER_CAP", passed: true, detail: "jointly spent MORE than the reserved cap — at least one claim is dishonest" }
    ]
  };
}

async function adjudicate() {
  const base = apiBase();
  if (!base && ($("dispute-txn-a").value.trim() === "" || $("dispute-txn-b").value.trim() === "")) return loadAlerts();
  if ($("dispute-txn-a").value.trim() && $("dispute-txn-b").value.trim() && !base) {
    renderDispute(sampleVerdict());
    message("AI Judge ruled (demo): DOUBLE_SPEND");
    return;
  }
  if (!base) throw new Error("Set the API base URL first.");
  if (!$("dispute-txn-a").value.trim() || !$("dispute-txn-b").value.trim()) throw new Error("Enter two device transaction ids under dispute.");
  const response = await fetch(`${base}/api/v1/sync/adjudicate`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ deviceTransactionIds: [$("dispute-txn-a").value.trim(), $("dispute-txn-b").value.trim()] }) });
  if (!response.ok) throw new Error(`Adjudication failed (${response.status}).`);
  const verdict = await response.json();
  renderDispute(verdict);
  message(`AI Judge ruled: ${verdict.ruling}`);
}

function run(action) { return () => action().catch((error) => { console.error(error); message(error.message || "Something went wrong."); }); }

function initialise() {
  const stored = localStorage.getItem("paron-admin-api-base");
  if (stored) $("api-base").value = stored;
  $("api-base").addEventListener("change", () => localStorage.setItem("paron-admin-api-base", $("api-base").value));
  $("load-alerts").addEventListener("click", run(loadAlerts));
  $("resolve-dispute").addEventListener("click", run(adjudicate));
}
initialise();