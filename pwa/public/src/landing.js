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
      if (heroCustAllowanceLabel) heroCustAllowanceLabel.textContent = 'LEFT AFTER THIS SAMPLE PAYMENT';
      if (heroCustMeterBar) heroCustMeterBar.style.width = '75%';
      if (heroCustPayButton) {
        heroCustPayButton.textContent = '✓ Demo payment complete';
        heroCustPayButton.classList.add('btn-paid');
      }
      if (heroCustStatusCard) {
        heroCustStatusCard.innerHTML = `
          <span class="pay-icon check-icon">✓</span>
          <span><b>Sample receipt created</b><small>Website animation · not sent to a merchant</small></span>
          <span class="check-pill">DEMO</span>
        `;
      }
      if (heroMerchIcon) {
        heroMerchIcon.innerHTML = '✓';
        heroMerchIcon.className = 'merchant-icon merchant-icon-success';
      }
      if (heroMerchTitle) heroMerchTitle.textContent = 'Sample payment received';
      if (heroMerchStatus) heroMerchStatus.textContent = 'Website simulation · not synced';
      if (heroMerchDetail) heroMerchDetail.textContent = '₹125.00 · Coffee House';
      if (heroTagOne) {
        heroTagOne.innerHTML = '✓ &nbsp; Sample receipt created';
        heroTagOne.className = 'float-tag tag-one tag-success';
      }
      if (heroTagTwo) {
        heroTagTwo.innerHTML = 'ⓘ &nbsp; No real payment made';
        heroTagTwo.className = 'float-tag tag-two tag-active';
      }
      if (heroTriggerPay) {
        heroTriggerPay.disabled = true;
        heroTriggerPay.textContent = '✓ Demo complete';
      }
      if (heroResetSim) heroResetSim.style.display = 'inline-flex';
    } else if (isOffline) {
      // Offline mode (Signal lost)
      if (heroSignalPill) {
        heroSignalPill.className = 'signal-pill pill-offline';
        if (heroSignalIcon) heroSignalIcon.textContent = '📴';
        if (heroSignalText) heroSignalText.textContent = 'Internet: Disconnected';
      }
      if (heroCustSignalBars) {
        heroCustSignalBars.className = 'signal-bars signal-none';
        heroCustSignalBars.innerHTML = '<span></span><span></span><span></span><span></span><i>✕</i>';
      }
      if (heroCustNetLabel) heroCustNetLabel.textContent = 'INTERNET DISCONNECTED';
      if (heroCustAmount) heroCustAmount.textContent = '₹500';
      if (heroCustAllowanceLabel) heroCustAllowanceLabel.textContent = 'AMOUNT SET ASIDE FOR OFFLINE';
      if (heroCustMeterBar) heroCustMeterBar.style.width = '100%';
      if (heroCustPayButton) {
        heroCustPayButton.textContent = 'Simulate ₹125 payment';
        heroCustPayButton.classList.remove('btn-paid');
      }
      if (heroCustStatusCard) {
        heroCustStatusCard.innerHTML = `
          <span class="pay-icon">↗</span>
          <span><b>Sample allowance ready</b><small>Available in this illustration</small></span>
          <span class="check-pill">DEMO</span>
        `;
      }
      if (heroMerchIcon) {
        heroMerchIcon.innerHTML = '⌁';
        heroMerchIcon.className = 'merchant-icon';
      }
      if (heroMerchTitle) heroMerchTitle.textContent = 'Waiting for sample payment';
      if (heroMerchStatus) heroMerchStatus.textContent = 'Website simulation · no Bluetooth';
      if (heroMerchDetail) heroMerchDetail.textContent = 'A real merchant phone is not connected';
      if (heroTagOne) {
        heroTagOne.innerHTML = '📴 &nbsp; Internet disconnected';
        heroTagOne.className = 'float-tag tag-one tag-warning';
      }
      if (heroTagTwo) {
        heroTagTwo.innerHTML = '🔒 &nbsp; ₹500 sample limit';
        heroTagTwo.className = 'float-tag tag-two';
      }
      if (heroTriggerPay) {
        heroTriggerPay.disabled = false;
        heroTriggerPay.textContent = '⚡ Simulate a ₹125 payment';
      }
      if (heroToggleSignal) {
        heroToggleSignal.textContent = '🟢 Restore internet';
      }
    } else {
      // Normal online state
      if (heroSignalPill) {
        heroSignalPill.className = 'signal-pill pill-online';
        if (heroSignalIcon) heroSignalIcon.textContent = '🟢';
        if (heroSignalText) heroSignalText.textContent = 'Connection: Online';
      }
      if (heroCustSignalBars) {
        heroCustSignalBars.className = 'signal-bars signal-full';
        heroCustSignalBars.innerHTML = '<span></span><span></span><span></span><span></span>';
      }
      if (heroCustNetLabel) heroCustNetLabel.textContent = 'WALLET · ONLINE';
      if (heroCustAmount) heroCustAmount.textContent = '₹500';
      if (heroCustAllowanceLabel) heroCustAllowanceLabel.textContent = 'RESERVED OFFLINE ALLOWANCE';
      if (heroCustMeterBar) heroCustMeterBar.style.width = '100%';
      if (heroCustPayButton) {
        heroCustPayButton.textContent = 'Simulate a ₹125 payment';
        heroCustPayButton.classList.remove('btn-paid');
      }
      if (heroCustStatusCard) {
        heroCustStatusCard.innerHTML = `
          <span class="pay-icon">↗</span>
          <span><b>Sample allowance set aside</b><small>Example only · no money moved</small></span>
          <span class="check-pill">DEMO</span>
        `;
      }
      if (heroMerchIcon) {
        heroMerchIcon.innerHTML = '⌂';
        heroMerchIcon.className = 'merchant-icon';
      }
      if (heroMerchTitle) heroMerchTitle.textContent = 'Terminal Idle';
      if (heroMerchStatus) heroMerchStatus.textContent = 'Coffee House · Terminal #1';
      if (heroMerchDetail) heroMerchDetail.textContent = 'Website illustration · no Bluetooth';
      if (heroTagOne) {
        heroTagOne.innerHTML = '⌁ &nbsp; Internet connected';
        heroTagOne.className = 'float-tag tag-one';
      }
      if (heroTagTwo) {
        heroTagTwo.innerHTML = '✓ &nbsp; Example limit: ₹500';
        heroTagTwo.className = 'float-tag tag-two';
      }
      if (heroTriggerPay) {
        heroTriggerPay.disabled = false;
        heroTriggerPay.textContent = '⚡ Simulate a ₹125 payment';
      }
      if (heroToggleSignal) {
        heroToggleSignal.textContent = '📴 Simulate internet loss';
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
      heroCustPayButton.textContent = 'Creating sample receipt…';
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
      bentoExposureSummary.innerHTML = `Of the example <b>₹1,000 wallet balance</b>, <b>₹${locked.toLocaleString('en-IN')}</b> is set aside for offline purchases and <b>₹${online.toLocaleString('en-IN')}</b> remains available for online use. While offline, the customer can spend up to the amount set aside.`;
    }
  }

  if (allowanceSlider) {
    allowanceSlider.addEventListener('input', (e) => {
      updateAllowanceSlider(e.target.value);
    });
  }

  // --- 5. Copy a readable example payment receipt ---
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

  // --- 6. Interactive four-step payment walkthrough ---
  const stepItems = document.querySelectorAll('.flow-step-item');
  const stepPreviewBox = document.getElementById('step-preview-box');

  const STEP_DATA = {
    '1': {
      title: 'Step 1: Set money aside while online',
      kicker: 'BEFORE INTERNET DROPS',
      stateBadge: 'EXAMPLE ALLOWANCE READY',
      description: 'The customer chooses how much of the example ₹1,000 wallet balance to reserve. That amount becomes the maximum available for offline purchases.',
      eventCode: 'Example wallet balance: ₹1,000\nSet aside for offline: ₹500\nStill available online: ₹500\nThis website step is an illustration; it does not reserve real money.'
    },
    '2': {
      title: 'Step 2: The internet connection drops',
      kicker: 'PAYMENTS CAN USE THE RESERVED AMOUNT',
      stateBadge: 'OFFLINE',
      description: 'The app can still use the amount the customer set aside before losing internet. This website demonstrates the idea with an animation; it does not switch a real phone into offline mode.',
      eventCode: 'Internet: Disconnected\nOffline amount reserved earlier: ₹500\nWebsite animation: Example only'
    },
    '3': {
      title: 'Step 3: The merchant receives a payment receipt',
      kicker: 'PAY WHILE OFFLINE',
      stateBadge: 'EXAMPLE · WAITING TO SYNC',
      description: 'The customer pays ₹125 at Coffee House. A receipt records the amount and merchant. Its digital signature works like a seal, helping Paron’s online service detect if those details were changed. The website simulates the handoff; it does not use Bluetooth or contact a merchant phone.',
      eventCode: 'Merchant: Coffee House\nPayment amount: ₹125\nReceipt: Created in this website example\nMerchant status: Received · waiting to sync'
    },
    '4': {
      title: 'Step 4: Reconnect and check the receipt',
      kicker: 'AFTER INTERNET RETURNS',
      stateBadge: 'BACKEND CHECK REQUIRED',
      description: 'When the devices reconnect, the app can send the receipt to Paron’s online service. It checks the signature, spending limit, and repeated claims before updating the merchant’s account record. This does not show a bank transfer.',
      eventCode: '1. Send the receipt to Paron’s online service\n2. Check who signed it and whether details changed\n3. Check the spending limit and repeated payments\n4. Update the merchant’s account record\nThe actual result depends on the connected services.'
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

  // Keep the landing page and its demo assets available as an installable PWA.
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch(() => {});
  }

})();

