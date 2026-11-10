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

  // --- 3. Homepage walkthrough: one click plays the full illustrated flow ---
  const walkthroughButton = document.getElementById('play-home-walkthrough');
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

  const walkthroughSteps = [
    { step: '1', delay: 0, customer: ['₹500', 'SET ASIDE FOR OFFLINE USE', 'Allowance ready', 'Example only · no money moved', 'ACTIVE'], merchant: ['⌂', 'Waiting for payment', 'Coffee House · example merchant', 'No payment received yet'] },
    { step: '2', delay: 1600, customer: ['₹500', 'AVAILABLE WITHOUT INTERNET', 'Spending limit saved', 'The example remains on this phone', 'OFFLINE'], merchant: ['⌂', 'Merchant is offline too', 'Coffee House · example merchant', 'Receipt can be checked later'] },
    { step: '3', delay: 3300, customer: ['₹375', 'LEFT AFTER SAMPLE PURCHASE', 'Sample receipt created', '₹125 · Coffee House · example only', 'SAMPLE'], merchant: ['✓', 'Sample payment received', 'Coffee House · example merchant', '₹125 receipt · waiting to sync'] },
    { step: '4', delay: 5100, customer: ['₹375', 'LEFT AFTER SAMPLE PURCHASE', 'Receipt checked after reconnect', 'Example flow complete · no real funds moved', 'DONE'], merchant: ['✓', 'Receipt check complete', 'Coffee House · example merchant', 'No bank transfer is shown'] }
  ];

  function animatePhoneText(element, value) {
    if (!element) return;
    element.classList.remove('phone-copy-change');
    void element.offsetWidth;
    element.textContent = value;
    element.classList.add('phone-copy-change');
  }

  function showWalkthroughStep(item) {
    if (heroCustAmount) animatePhoneText(heroCustAmount, item.customer[0]);
    if (heroCustAllowanceLabel) animatePhoneText(heroCustAllowanceLabel, item.customer[1]);
    if (heroCustPayButton) animatePhoneText(heroCustPayButton, item.customer[2]);
    if (heroCustMeterBar) heroCustMeterBar.style.width = item.step === '3' || item.step === '4' ? '75%' : '100%';
    if (heroCustStatusCard) {
      heroCustStatusCard.innerHTML = `<span class="pay-icon">${item.step === '3' || item.step === '4' ? '✓' : '↗'}</span><span><b>${item.customer[2]}</b><small>${item.customer[3]}</small></span><span class="check-pill">${item.customer[4]}</span>`;
      heroCustStatusCard.classList.remove('phone-copy-change'); void heroCustStatusCard.offsetWidth; heroCustStatusCard.classList.add('phone-copy-change');
    }
    if (heroCustNetLabel) animatePhoneText(heroCustNetLabel, item.step === '2' || item.step === '3' ? 'WALLET · OFFLINE' : 'WALLET · RECONNECTED');
    if (heroCustSignalBars) heroCustSignalBars.className = `signal-bars ${item.step === '2' || item.step === '3' ? 'signal-none' : 'signal-full'}`;
    if (heroMerchIcon) { heroMerchIcon.textContent = item.merchant[0]; heroMerchIcon.className = `merchant-icon${item.step === '3' || item.step === '4' ? ' merchant-icon-success' : ''}`; }
    if (heroMerchTitle) animatePhoneText(heroMerchTitle, item.merchant[1]);
    if (heroMerchStatus) animatePhoneText(heroMerchStatus, item.merchant[2]);
    if (heroMerchDetail) animatePhoneText(heroMerchDetail, item.merchant[3]);
    if (item.step === '3' && heroRippleWave) { heroRippleWave.classList.remove('active'); void heroRippleWave.offsetWidth; heroRippleWave.classList.add('active'); }
    activateStep(item.step);
  }

  let walkthroughTimers = [];
  if (walkthroughButton) walkthroughButton.addEventListener('click', () => {
    walkthroughTimers.forEach(clearTimeout);
    walkthroughTimers = [];
    walkthroughButton.disabled = true;
    walkthroughButton.innerHTML = 'Playing the walkthrough… <span>↻</span>';
    walkthroughSteps.forEach((item) => walkthroughTimers.push(setTimeout(() => showWalkthroughStep(item), item.delay)));
    walkthroughTimers.push(setTimeout(() => {
      walkthroughButton.disabled = false;
      walkthroughButton.innerHTML = 'Replay the walkthrough <span>↻</span>';
    }, 6800));
  });

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
  updateAllowanceSlider(500);
  activateStep('1');

  // Fade text and cards into view as the visitor scrolls, with a reduced-motion fallback.
  const revealTargets = document.querySelectorAll('.hero-copy > *, .section-heading, .about-copy > *, .flow-step-item, .bento-card, .faq-item, .preview-copy > *, .screen-placeholder');
  if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    revealTargets.forEach((element) => element.classList.add('reveal-on-scroll'));
    const revealObserver = new IntersectionObserver((entries, observer) => entries.forEach((entry) => {
      if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target); }
    }), { threshold: 0.12 });
    revealTargets.forEach((element) => revealObserver.observe(element));
  }

  // Keep the landing page and its demo assets available as an installable PWA.
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch(() => {});
  }

})();

