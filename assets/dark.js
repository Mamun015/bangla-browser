/* বাংলা ব্রাউজার — রাতের মোডে ওয়েবপেজও কালো।
   ১) সাইটের নিজের ডার্ক থিম থাকলে সেটাই চালু করার চেষ্টা করে
      (prefers-color-scheme: dark এর নিয়মগুলো সরাসরি বসিয়ে দেয়)।
   ২) তাতেও পেজ কালো না হলে রং উল্টে দেয়, ছবি-ভিডিও আবার সত্যিকারের রঙে ফেরায়।
   জোর করে কালো চালু থাকলে ১ নম্বর বাদ, শুধু উল্টে দেওয়া হয়। */
(function () {
  if (window.__bbDarkOn) return;
  window.__bbDarkOn = 1;
  if (document.documentElement.getAttribute('data-bb-dark') === 'off') return;

  // জোর করে কালো: সব পেজ উল্টে কালো। না হলে শুধু সাইটের নিজের ডার্ক থিম চালু হয়,
  // যে সাইট সাপোর্ট করে না সেটা সাদাই থাকে।
  var FORCE = !!window.__bbForceDark;

  function lum(c) {
    if (!c) return 1;
    var m = c.match(/rgba?\(([^)]+)\)/);
    if (!m) return 1;
    var p = m[1].split(',').map(function (x) { return parseFloat(x); });
    if (p.length > 3 && p[3] === 0) return 1;
    return (0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]) / 255;
  }

  // ------------------------------------------------ সাইটের নিজের ডার্ক থিম

  /* কিছু সাইটের নিজের ডার্ক থিম আছে, কিন্তু সেটা চালু হয় নিজের সেটিংস থেকে
     (মিডিয়া কুয়েরি থেকে না)। উইকিপিডিয়ার থিম <html> এর ক্লাস দেখে ঠিক হয়,
     তাই ক্লাসটা বদলে দিলেই সাইট নিজেই কালো হয়ে যায়। */
  function siteAdapter() {
    if (FORCE) return;
    try {
      var host = location.hostname || '';
      if (/(^|\.)wikipedia\.org$/.test(host)) {
        var h = document.documentElement;
        h.classList.remove('skin-theme-clientpref-day', 'skin-theme-clientpref-os');
        h.classList.add('skin-theme-clientpref-night');
      }
    } catch (e) { /* */ }
  }

  var MEDIA_RULE = 4, SUPPORTS_RULE = 12;

  function collect(rules, out, depth) {
    if (!rules || depth > 4) return;
    for (var i = 0; i < rules.length; i++) {
      var r = rules[i];
      try {
        if (r.type === MEDIA_RULE) {
          var cond = r.conditionText || (r.media && r.media.mediaText) || '';
          if (/prefers-color-scheme\s*:\s*dark/i.test(cond)) {
            // এই নিয়মগুলো শর্ত ছাড়াই বসিয়ে দেওয়া হয়
            var inner = r.cssRules;
            for (var j = 0; inner && j < inner.length; j++) out.push(inner[j].cssText);
            continue;
          }
        }
        if (r.cssRules && (r.type === MEDIA_RULE || r.type === SUPPORTS_RULE)) {
          collect(r.cssRules, out, depth + 1);
        }
      } catch (e) { /* কিছু নিয়ম পড়া যায় না, বাদ */ }
    }
  }

  var lastNative = '', nativeEl = null;
  function applyNativeDark() {
    if (FORCE) return;
    var out = [];
    var sheets;
    try { sheets = document.styleSheets; } catch (e) { return; }
    for (var i = 0; i < sheets.length; i++) {
      var rules;
      try { rules = sheets[i].cssRules; } catch (e) { continue; } // অন্য সাইটের CSS, পড়া যায় না
      collect(rules, out, 0);
    }
    if (!out.length) return;
    var text = out.join('\n');
    if (text === lastNative) return; // আগেই বসানো হয়েছে
    lastNative = text;
    if (nativeEl && nativeEl.parentNode) nativeEl.parentNode.removeChild(nativeEl);
    var st = document.createElement('style');
    st.setAttribute('data-bb-native', '1');
    st.textContent = text;
    (document.head || document.documentElement).appendChild(st);
    nativeEl = st;
  }

  // ------------------------------------------------ রং উল্টে কালো করা

  var CSS = 'html{filter:invert(1) hue-rotate(180deg) !important;background:#fff !important}' +
    // ছবি, ভিডিও, ফ্রেম — আগের রঙে ফেরত
    'img,video,picture,canvas,iframe,embed,object,svg image,' +
    '[data-bb-back],' +
    '[style*="background-image"],[style*="background: url"]' +
    '{filter:invert(1) hue-rotate(180deg) !important}' +
    // উল্টে দেওয়া বাক্সের ভেতরের ছবি যেন দুইবার না উল্টায়
    '[data-bb-back] img,[data-bb-back] video,[data-bb-back] canvas{-webkit-filter:none !important;filter:none !important}' +
    '::-webkit-scrollbar{background:#111}';

  function check(el) {
    if (!el || el.nodeType !== 1) return;
    if (el.hasAttribute('data-bb-back')) return;
    if (el.closest && el.closest('[data-bb-back]')) return;
    var tag = el.tagName;
    if (tag === 'IMG' || tag === 'VIDEO' || tag === 'CANVAS') return;
    if ((el.textContent || '').trim().length > 8) return; // ভেতরে লেখা থাকলে রং উল্টাবে না
    var r;
    try { r = el.getBoundingClientRect(); } catch (e) { return; }
    // বড় সেকশন বাদ: ওগুলো উল্টে দিলে সাদা-কালো ব্লক দেখা যায়
    if (r.width > 180 || r.height > 180 || r.width * r.height < 16) return;
    var bg;
    try { bg = getComputedStyle(el).backgroundImage; } catch (e) { return; }
    if (bg && bg !== 'none' && bg.indexOf('url(') !== -1) el.setAttribute('data-bb-back', '1');
  }

  // ছবিওয়ালা বাক্স খোঁজা ব্যাচে ব্যাচে, যাতে পেজ আটকে না যায়
  var scanning = false;
  function scanChunked() {
    if (scanning) return;
    scanning = true;
    var list;
    try { list = document.querySelectorAll('*'); } catch (e) { scanning = false; return; }
    var i = 0, limit = Math.min(list.length, 3500);
    function step() {
      var end = Math.min(i + 250, limit);
      for (; i < end; i++) check(list[i]);
      if (i < limit) setTimeout(step, 16);
      else scanning = false;
    }
    step();
  }

  var timer = null;
  function schedule() {
    if (timer) return;
    timer = setTimeout(function () {
      timer = null;
      if (FORCE) {
        scanChunked();
      } else {
        siteAdapter();
        applyNativeDark();      // নতুন যোগ হওয়া স্টাইলশিটেও ডার্ক নিয়ম থাকতে পারে
      }
    }, 500);
  }

  function watchDom() {
    if (!window.MutationObserver) return;
    try {
      new MutationObserver(function (records) {
        for (var i = 0; i < records.length; i++) {
          if (records[i].addedNodes && records[i].addedNodes.length) {
            schedule();
            return;
          }
        }
      }).observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) { /* */ }
  }

  function dark() {
    var v = 1;
    try {
      var b = document.body ? getComputedStyle(document.body).backgroundColor : null;
      var h = getComputedStyle(document.documentElement).backgroundColor;
      v = Math.min(lum(b), lum(h));
    } catch (e) { v = 1; }

    if (v < 0.28) {
      // পেজ এখন কালো — হয় নিজের থিমে, নয়তো আগেই ছিল। উল্টানোর দরকার নেই।
      watchDom();
      return;
    }


    // সাইটের ডার্ক নিয়ম বসানোর পরও পেজ সাদা থেকে গেলে ওগুলো তুলে দিই,
    // নইলে অর্ধেক কালো অর্ধেক সাদা মিশ্রণ হয়
    if (nativeEl && nativeEl.parentNode) {
      nativeEl.parentNode.removeChild(nativeEl);
      nativeEl = null;
      lastNative = '';
    }

    // শুধু রাতের মোড: সাইট সাপোর্ট করে না মানে পেজ সাদাই থাকবে, উল্টানো হবে না।
    // উল্টানো কেবল "জোর করে কালো" চালু থাকলে।
    if (!FORCE) {
      watchDom();
      return;
    }

    var s = document.createElement('style');
    s.setAttribute('data-bb-dark', '1');
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
    document.documentElement.setAttribute('data-bb-dark', 'on');
    scanChunked();
    watchDom();
  }

  function boot() {
    siteAdapter();
    applyNativeDark();
    // সাইটের নিজের নিয়ম বসানোর পর একটু দেরি করে রং মাপা, যাতে স্টাইল প্রয়োগ হয়ে যায়
    setTimeout(dark, 60);
  }

  if (document.body) boot();
  else document.addEventListener('DOMContentLoaded', boot, false);
})();
