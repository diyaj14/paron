/*
 * Paron — Landing Page Interactive Dynamism (21st.dev inspired components)
 * Handles:
 * 1. Interactive Hero Device Simulator (Online/Offline Signal Toggle & Tap-to-Pay Handshake)
 * 2. Bento Grid Interactive Allowance Slider & Live Split Calculator
 * 3. Canonical String Copy & Cryptographic Hash Preview
 * 4. Interactive 4-Step Flow Stepper
 * 5. Bento Card Mouse-following Spotlight Glow Effect
 * 6. Interactive Smooth Accordion FAQ
 * 7. Sticky Glassmorphism Header Scroll Shadow
 */

(function () {
  'use strict';

  // --- 1. Sticky Nav Scroll Observer ---
  const header = document.querySelector('.nav');
  if (header) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 20) {
        header.classList.add('nav-scrolled');
      } else {
        header.classList.remove('nav-scrolled');
      }
    }, { passive: true });
  }

  // --- 2. Bento Card Spotlight Effect (21st.dev style) ---
  const bentoCards = document.querySelectorAll('.bento-card');
  bentoCards.forEach((card) => {
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      card.style.setProperty('--mouse-x', `${x}px`);
      card.style.setProperty('--mouse-y', `${y}px`);
    });
  });

  // --- 3. Hero Interactive Signal & Tap-to-Pay Simulator ---
  const heroToggleSignal = document.getElementById('hero-toggle-signal');
  const heroTriggerPay = document.getElementById('hero-trigger-pay');
  const heroResetSim = document.getElementById('hero-reset-sim');
  const heroSignalPill = document.getElementById('hero-signal-pill');
  const heroSignalIcon = document.getElementById('hero-signal-icon');
  const heroSignalText = document.getElementById('hero-signal-text');

  // Customer phone elements
  const heroCustSignalBars = document.getElementById('hero-cust-signal-bars');
  const heroCustNetLabel = document.getElementById('hero-cust-net-label');
  const heroCustAmount = document.getElementById('hero-cust-amount');
  const heroCustAllowanceLabel = document.getElementById('hero-cust-allowance-label');
  const heroCustMeterBar = document.getElementById('hero-cust-meter-bar');
  const heroCustPayButton = document.getElementById('hero-cust-pay-btn');
  const heroCustStatusCard = document.getElementById('hero-cust-status-card');
  const heroRippleWave = document.getElementById('hero-ripple-wave');

  // Merchant phone elements
  const heroMerchCard = document.getElementById('hero-merch-card');
  const heroMerchIcon = document.getElementById('hero-merch-icon');
  const heroMerchTitle = document.getElementById('hero-merch-title');
  const heroMerchStatus = document.getElementById('hero-merch-status');
  const heroMerchDetail = document.getElementById('hero-merch-detail');

  // Floating tags
  const heroTagOne = document.getElementById('hero-tag-one');
  const heroTagTwo = document.getElementById('hero-tag-two');

  let isOffline = false;
  let isPaid = false;

  function updateHeroState() {
    if (isPaid) {
      // Payment completed state
      if (heroCustAmount) heroCustAmount.textContent = '₹375';
      if (heroCustAllowanceLabel) heroCustAllowanceLabel.textContent = 'REMAINING ALLOWANCE (₹125 SPENT)';
      if (heroCustMeterBar) heroCustMeterBar.style.width = '75%';
      if (heroCustPayButton) {
        heroCustPayButton.textContent = '✓ Receipt Signed';
        heroCustPayButton.classList.add('btn-paid');
      }
      if (heroCustStatusCard) {
        heroCustStatusCard.innerHTML = `
          <span class="pay-icon check-icon">✓</span>
          <span><b>Receipt Signed Locally</b><small>ECDSA P-256 · #MRC-77A2</small></span>
          <span class="check-pill">QUEUED</span>
        `;
      }
      if (heroMerchIcon) {
        heroMerchIcon.innerHTML = '✓';
        heroMerchIcon.className = 'merchant-icon merchant-icon-success';
      }
      if (heroMerchTitle) heroMerchTitle.textContent = 'Payment Accepted';
      if (heroMerchStatus) heroMerchStatus.textContent = 'Receipt Verified · Pending Sync';
      if (heroMerchDetail) heroMerchDetail.textContent = '₹125.00 · Coffee House';
      if (heroTagOne) {
        heroTagOne.innerHTML = '✓ &nbsp; Signed & Verified (ECDSA P-256)';
        heroTagOne.className = 'float-tag tag-one tag-success';
      }
      if (heroTagTwo) {
        heroTagTwo.innerHTML = '⏱ &nbsp; 18ms Handshake · Offline';
        heroTagTwo.className = 'float-tag tag-two tag-active';
      }
      if (heroTriggerPay) {
        heroTriggerPay.disabled = true;
        heroTriggerPay.textContent = '✓ Payment Complete';
      }
      if (heroResetSim) heroResetSim.style.display = 'inline-flex';
    } else if (isOffline) {
      // Offline mode (Signal lost)
      if (heroSignalPill) {
        heroSignalPill.className = 'signal-pill pill-offline';
        if (heroSignalIcon) heroSignalIcon.textContent = '📴';
        if (heroSignalText) heroSignalText.textContent = 'Signal: Disconnected';
      }
      if (heroCustSignalBars) {
        heroCustSignalBars.className = 'signal-bars signal-none';
        heroCustSignalBars.innerHTML = '<span></span><span></span><span></span><span></span><i>✕</i>';
      }
      if (heroCustNetLabel) heroCustNetLabel.textContent = 'OFFLINE · SAFE MODE';
      if (heroCustAmount) heroCustAmount.textContent = '₹500';
      if (heroCustAllowanceLabel) heroCustAllowanceLabel.textContent = 'OFFLINE ALLOWANCE (LOCKED)';
      if (heroCustMeterBar) heroCustMeterBar.style.width = '100%';
      if (heroCustPayButton) {
        heroCustPayButton.textContent = 'Pay ₹125 offline';
        heroCustPayButton.classList.remove('btn-paid');
      }
      if (heroCustStatusCard) {
        heroCustStatusCard.innerHTML = `
          <span class="pay-icon">↗</span>
          <span><b>Offline Token Active</b><small>Bound to device hardware</small></span>
          <span class="check-pill">READY</span>
        `;
      }
      if (heroMerchIcon) {
        heroMerchIcon.innerHTML = '⌁';
        heroMerchIcon.className = 'merchant-icon';
      }
      if (heroMerchTitle) heroMerchTitle.textContent = 'Awaiting BLE Payment';
      if (heroMerchStatus) heroMerchStatus.textContent = 'Advertising · GATT Service Ready';
      if (heroMerchDetail) heroMerchDetail.textContent = 'Ready for nearby customer';
      if (heroTagOne) {
        heroTagOne.innerHTML = '📴 &nbsp; Offline Safe Mode Engaged';
        heroTagOne.className = 'float-tag tag-one tag-warning';
      }
      if (heroTagTwo) {
        heroTagTwo.innerHTML = '🔒 &nbsp; ₹500 Allowance Bound';
        heroTagTwo.className = 'float-tag tag-two';
      }
      if (heroTriggerPay) {
        heroTriggerPay.disabled = false;
        heroTriggerPay.textContent = '⚡ Simulate Tap-to-Pay (₹125)';
      }
      if (heroToggleSignal) {
        heroToggleSignal.textContent = '🟢 Reconnect Signal';
      }
    } else {
      // Normal online state
      if (heroSignalPill) {
        heroSignalPill.className = 'signal-pill pill-online';
        if (heroSignalIcon) heroSignalIcon.textContent = '🟢';
        if (heroSignalText) heroSignalText.textContent = 'Signal: 5G Online';
      }
      if (heroCustSignalBars) {
        heroCustSignalBars.className = 'signal-bars signal-full';
        heroCustSignalBars.innerHTML = '<span></span><span></span><span></span><span></span>';
      }
      if (heroCustNetLabel) heroCustNetLabel.textContent = 'WALLET · 5G ONLINE';
      if (heroCustAmount) heroCustAmount.textContent = '₹500';
      if (heroCustAllowanceLabel) heroCustAllowanceLabel.textContent = 'RESERVED OFFLINE ALLOWANCE';
      if (heroCustMeterBar) heroCustMeterBar.style.width = '100%';
      if (heroCustPayButton) {
        heroCustPayButton.textContent = 'Pay ₹125 (Offline Ready)';
        heroCustPayButton.classList.remove('btn-paid');
      }
      if (heroCustStatusCard) {
        heroCustStatusCard.innerHTML = `
          <span class="pay-icon">↗</span>
          <span><b>Allowance Reserved</b><small>Synchronized with Cloud Ledger</small></span>
          <span class="check-pill">ACTIVE</span>
        `;
      }
      if (heroMerchIcon) {
        heroMerchIcon.innerHTML = '⌂';
        heroMerchIcon.className = 'merchant-icon';
      }
      if (heroMerchTitle) heroMerchTitle.textContent = 'Terminal Idle';
      if (heroMerchStatus) heroMerchStatus.textContent = 'Coffee House · Terminal #1';
      if (heroMerchDetail) heroMerchDetail.textContent = 'Online & ready for offline BLE';
      if (heroTagOne) {
        heroTagOne.innerHTML = '⌁ &nbsp; Connected & Synchronized';
        heroTagOne.className = 'float-tag tag-one';
      }
      if (heroTagTwo) {
        heroTagTwo.innerHTML = '✓ &nbsp; Allowance Reserved Online';
        heroTagTwo.className = 'float-tag tag-two';
      }
      if (heroTriggerPay) {
        heroTriggerPay.disabled = false;
        heroTriggerPay.textContent = '⚡ Simulate Tap-to-Pay (₹125)';
      }
      if (heroToggleSignal) {
        heroToggleSignal.textContent = '📴 Simulate Signal Drop';
      }
      if (heroResetSim) heroResetSim.style.display = 'none';
    }
  }

  if (heroToggleSignal) {
    heroToggleSignal.addEventListener('click', () => {
      isOffline = !isOffline;
      if (!isOffline) isPaid = false;
      updateHeroState();
    });
  }

  function triggerPaymentAnimation() {
    if (isPaid) return;
    // Activate ripple wave
    if (heroRippleWave) {
      heroRippleWave.classList.remove('active');
      void heroRippleWave.offsetWidth; // trigger reflow
      heroRippleWave.classList.add('active');
    }

    if (heroCustPayButton) {
      heroCustPayButton.textContent = 'Signing ECDSA P-256...';
    }

    setTimeout(() => {
      isPaid = true;
      updateHeroState();
    }, 600);
  }

  if (heroTriggerPay) {
    heroTriggerPay.addEventListener('click', () => {
      if (!isOffline) {
        // Automatically drop signal and then pay to show the offline magic!
        isOffline = true;
        updateHeroState();
        setTimeout(triggerPaymentAnimation, 400);
      } else {
        triggerPaymentAnimation();
      }
    });
  }

  if (heroCustPayButton) {
    heroCustPayButton.addEventListener('click', () => {
      if (!isPaid) triggerPaymentAnimation();
    });
  }

  if (heroResetSim) {
    heroResetSim.addEventListener('click', () => {
      isPaid = false;
      isOffline = false;
      updateHeroState();
    });
  }

  // --- 4. Interactive Allowance Allocation Slider (Bento Grid) ---
  const allowanceSlider = document.getElementById('allowance-slider');
  const bentoLockedVal = document.getElementById('bento-locked-val');
  const bentoOnlineVal = document.getElementById('bento-online-val');
  const bentoMeterLocked = document.getElementById('bento-meter-locked');
  const bentoMeterOnline = document.getElementById('bento-meter-online');
  const bentoExposureSummary = document.getElementById('bento-exposure-summary');

  const TOTAL_BALANCE = 1000;

  function updateAllowanceSlider(val) {
    const locked = Math.max(0, Math.min(TOTAL_BALANCE, Number(val)));
    const online = TOTAL_BALANCE - locked;
    const lockedPct = (locked / TOTAL_BALANCE) * 100;
    const onlinePct = (online / TOTAL_BALANCE) * 100;

    if (bentoLockedVal) bentoLockedVal.textContent = `₹${locked.toLocaleString('en-IN')}.00`;
    if (bentoOnlineVal) bentoOnlineVal.textContent = `₹${online.toLocaleString('en-IN')}.00`;
    if (bentoMeterLocked) bentoMeterLocked.style.width = `${lockedPct}%`;
    if (bentoMeterOnline) bentoMeterOnline.style.width = `${onlinePct}%`;

    if (bentoExposureSummary) {
      bentoExposureSummary.innerHTML = `Your maximum offline liability is strictly capped at <b>₹${locked}</b>. The remaining <b>₹${online}</b> in your PayPal account cannot be touched by offline claims.`;
    }
  }

  if (allowanceSlider) {
    allowanceSlider.addEventListener('input', (e) => {
      updateAllowanceSlider(e.target.value);
    });
  }

  // --- 5. Interactive Copy Canonical String Inspector ---
  const copyCanonicalBtn = document.getElementById('btn-copy-canonical');
  const canonicalCodeText = document.getElementById('canonical-code-text');

  if (copyCanonicalBtn && canonicalCodeText) {
    copyCanonicalBtn.addEventListener('click', () => {
      const textToCopy = canonicalCodeText.textContent.trim();
      navigator.clipboard.writeText(textToCopy).then(() => {
        const originalText = copyCanonicalBtn.innerHTML;
        copyCanonicalBtn.innerHTML = '✓ Copied to clipboard!';
        copyCanonicalBtn.classList.add('copied');
        setTimeout(() => {
          copyCanonicalBtn.innerHTML = originalText;
          copyCanonicalBtn.classList.remove('copied');
        }, 2000);
      }).catch(() => {
        copyCanonicalBtn.textContent = 'Press Ctrl+C to copy';
      });
    });
  }

  // --- 6. Interactive 4-Step Payment Flow Stepper ---
  const stepItems = document.querySelectorAll('.flow-step-item');
  const stepPreviewBox = document.getElementById('step-preview-box');

  const STEP_DATA = {
    '1': {
      title: 'Step 1: Reserve Prepaid Allowance Online',
      kicker: 'PRE-OFFLINE ALLOCATION',
      stateBadge: 'STATUS: ACTIVE_RESERVATION',
      description: 'The user locks ₹500 before stepping out or boarding a flight. Ledger Service atomically reserves ₹500 under a PESSIMISTIC_WRITE account lock and issues a cryptographic TokenRecord.',
      eventCode: 'POST /api/v1/tokens/gettoken\n{\n  "userId": "demo-customer",\n  "deviceId": "dev-p256-88a4",\n  "amount": 500,\n  "expiryHours": 6\n}\n➔ 201 Created · reservationId: "res-901c"'
    },
    '2': {
      title: 'Step 2: Signal Drops — Safe Mode Engaged',
      kicker: 'LOCAL STATE ACTIVATION',
      stateBadge: 'NETWORK: DISCONNECTED',
      description: 'No cell tower or Wi-Fi needed. The PWA and Android companion activate local offline mode. The customer wallet holds the signed token in IndexedDB, completely isolated from network interruptions.',
      eventCode: 'Event: window.offline\nWalletState: OFFLINE_SAFE_MODE\nTokenAvailable: ₹500.00\nDeviceKey: Loaded from IndexedDB (P-256)'
    },
    '3': {
      title: 'Step 3: Pay Nearby — Local Signed Receipt',
      kicker: 'BLE GATT 0x50 FRAMING',
      stateBadge: 'STATUS: PENDING_SYNC (LOCAL)',
      description: 'Customer initiates a ₹125 coffee payment. The device calculates the canonical hash, signs it with its private ECDSA P-256 key, and transmits it via GATT frames. Merchant validates format and responds with signed receipt MRC-77A2.',
      eventCode: 'Payload:\ncanonicalString: "txn-104\\0paron.tok.500\\0merchant-cafe-002\\0125\\0..."\nsignature: "MEQCIDK3x... (ECDSA P-256)"\nMerchantResponse: PAYMENT_ACCEPTED\nLocalStatus: "Pending sync — paid offline"'
    },
    '4': {
      title: 'Step 4: Reconnect & Atomic Reconciliation',
      kicker: '4-STAGE DETERMINISTIC RECONCILIATION',
      stateBadge: 'LEDGER: SETTLED',
      description: 'Upon reconnecting, the receipt is uploaded to Sync Service. Signature is verified, allowance cap confirmed, idempotency validated, and ledger settled atomically without double-debiting.',
      eventCode: 'Sync Timeline:\n1. Receipt uploaded ➔ 202 Accepted\n2. Signature verified against device public key ✓\n3. Token spend state within cap (₹125 <= ₹500) ✓\n4. Ledger settled: reservation res-901c debit ₹125 ➔ SETTLED'
    }
  };

  function activateStep(stepNum) {
    stepItems.forEach((item) => {
      if (item.getAttribute('data-step') === stepNum) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });

    const data = STEP_DATA[stepNum];
    if (data && stepPreviewBox) {
      stepPreviewBox.innerHTML = `
        <div class="step-box-header">
          <div>
            <span class="step-box-kicker">${data.kicker}</span>
            <h4 class="step-box-title">${data.title}</h4>
          </div>
          <span class="step-box-badge">${data.stateBadge}</span>
        </div>
        <p class="step-box-desc">${data.description}</p>
        <pre class="step-box-code"><code>${data.eventCode}</code></pre>
      `;
    }
  }

  stepItems.forEach((item) => {
    item.addEventListener('click', () => {
      const step = item.getAttribute('data-step');
      if (step) activateStep(step);
    });
  });

  // --- 7. Interactive Accordion FAQ ---
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach((item) => {
    const trigger = item.querySelector('.faq-trigger');
    if (trigger) {
      trigger.addEventListener('click', () => {
        const isOpen = item.classList.contains('open');
        // Close other open items
        faqItems.forEach((other) => other.classList.remove('open'));
        if (!isOpen) {
          item.classList.add('open');
        }
      });
    }
  });

  // --- 8. Initial Setup ---
  updateHeroState();
  updateAllowanceSlider(500);
  activateStep('1');

})();

