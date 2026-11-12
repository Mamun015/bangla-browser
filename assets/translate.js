/* বাংলা ব্রাউজার — পেজ অটো বাংলায় অনুবাদ।
   পেজ লোড হলে টেক্সট নোডগুলো ব্যাচ করে Google-এর অনুবাদ এন্ডপয়েন্টে পাঠায়
   আর ফলাফলটা জায়গামতো বসিয়ে দেয়। পর্যন্ত-দেখা যায়, ধাপে ধাপে। */
(function () {
  if (window.__bbTransOn) return;
  window.__bbTransOn = 1;

  var API = 'https://translate.googleapis.com/translate_a/single' +
            '?client=gtx&dt=t&sl=auto&tl=bn&q=';
  var MAX_CHARS = 1600;
  var WORKERS = 3;
  var MAX_BATCHES = 300;

  var SKIP_TAGS = {
    SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, CODE: 1, PRE: 1, KBD: 1, SAMP: 1,
    TEXTAREA: 1, INPUT: 1, SELECT: 1, OPTION: 1, IFRAME: 1, CANVAS: 1,
    SVG: 1, VIDEO: 1, AUDIO: 1
  };
  var LATIN = /[A-Za-z]/;
  var seen = 0, done = 0, stopped = 0, failures = 0;

  var pill = null;
  function showPill(text) {
    if (!pill) {
      pill = document.createElement('div');
      pill.setAttribute('data-bb-no', '1');
      pill.style.cssText = 'position:fixed;left:12px;bottom:76px;z-index:2147483647;' +
        'background:rgba(0,106,78,.92);color:#fff;font:600 12px/1.3 sans-serif;' +
        'padding:6px 11px;border-radius:999px;pointer-events:none;opacity:0;' +
        'transition:opacity .25s;box-shadow:0 4px 14px rgba(0,0,0,.35)';
      (document.body || document.documentElement).appendChild(pill);
    }
    pill.textContent = text;
    pill.style.opacity = '1';
  }
  function hidePill() {
    if (!pill) return;
    var p = pill;
    pill = null;
    p.style.opacity = '0';
    setTimeout(function () { if (p.parentNode) p.parentNode.removeChild(p); }, 400);
  }

  function usable(node) {
    var s = node.nodeValue;
    if (!s) return false;
    s = s.trim();
    if (s.length < 2 || s.length > 4000) return false;
    return LATIN.test(s);
  }

  function skipped(node) {
    var p = node.parentNode;
    if (!p || p.nodeType !== 1) return true;
    if (SKIP_TAGS[p.nodeName.toUpperCase()]) return true;
    if (p.isContentEditable) return true;
    if (p.getAttribute && p.getAttribute('data-bb-no')) return true;
    if (p.closest && p.closest('[translate="no"],[data-bb-no]')) return true;
    return false;
  }

  function collect(root, out) {
    if (!root) return out;
    var walk = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
    var n = walk.nextNode();
    while (n) {
      if (!n.__bbSeen && !skipped(n) && usable(n)) {
        n.__bbSeen = 1;
        out.push(n);
      }
      n = walk.nextNode();
    }
    return out;
  }

  function parse(body, lines) {
    var d = JSON.parse(body);
    var segs = d && d[0];
    if (!segs || !segs.length) throw new Error('shape');
    var flat = '';
    for (var i = 0; i < segs.length; i++) flat += (segs[i][0] || '');
    if (flat.split('\n').length !== lines) {
      // কোনো সময় এন্ডপয়েন্ট লাইন-ব্রেক ফেরত দেয় না, তখন আলাদা করে জোড়া লাগাই
      flat = segs.map(function (x) {
        return String(x[0] || '').replace(/\n+$/, '');
      }).join('\n');
    }
    return flat;
  }

  // সাইটের CSP fetch আটকালে অ্যাপের ভেতরকার ব্রিজ দিয়ে আনা হয়
  var bridged = false;

  function viaBridge(url, lines) {
    if (!(window.BBTranslate && window.BBTranslate.get)) throw new Error('no bridge');
    var body = window.BBTranslate.get(url);
    if (!body) throw new Error('bridge empty');
    return parse(body, lines);
  }

  function translate(text) {
    var url = API + encodeURIComponent(text);
    var lines = text.split('\n').length;
    if (bridged) {
      try {
        return Promise.resolve(viaBridge(url, lines));
      } catch (e) {
        return Promise.reject(e);
      }
    }
    return fetch(url, { credentials: 'omit' })
      .then(function (r) {
        if (!r.ok) throw new Error('http ' + r.status);
        return r.text();
      })
      .then(function (body) { return parse(body, lines); })
      .catch(function () {
        bridged = true;
        return viaBridge(url, lines);
      });
  }

  function batches(nodes) {
    var list = [], cur = [], curText = [], len = 0;
    for (var i = 0; i < nodes.length; i++) {
      var raw = nodes[i].nodeValue;
      var s = raw.replace(/\s+/g, ' ').trim();
      if (!s) continue;
      if (cur.length && len + s.length + 1 > MAX_CHARS) {
        list.push({ nodes: cur, text: curText.join('\n') });
        cur = []; curText = []; len = 0;
      }
      cur.push(nodes[i]);
      curText.push(s);
      len += s.length + 1;
    }
    if (cur.length) list.push({ nodes: cur, text: curText.join('\n') });
    return list;
  }

  function applyBatch(b) {
    return translate(b.text).then(function (res) {
      var parts = res.split('\n');
      if (parts.length !== b.nodes.length) { failures += 1; return; }
      for (var i = 0; i < b.nodes.length; i++) {
        var node = b.nodes[i];
        if (!node.parentNode) continue;
        var src = node.nodeValue;
        var lead = (/^\s*/.exec(src) || [''])[0];
        var trail = (/\s*$/.exec(src) || [''])[0];
        node.nodeValue = lead + parts[i].trim() + trail;
      }
      done += b.nodes.length;
    }).catch(function () {
      failures += 1;
      if (failures > 8) stopped = 1;
    });
  }

  function run(root) {
    var nodes = collect(root || document.body, []);
    if (!nodes.length) return Promise.resolve();
    var list = batches(nodes).slice(0, MAX_BATCHES - seen);
    if (!list.length) return Promise.resolve();
    seen += list.length;
    if (!done) showPill('বাংলায় অনুবাদ হচ্ছে…');
    var idx = 0;
    function worker() {
      if (stopped || idx >= list.length) return Promise.resolve();
      var b = list[idx++];
      return applyBatch(b).then(worker);
    }
    var all = [];
    for (var w = 0; w < WORKERS; w++) all.push(worker());
    return Promise.all(all);
  }

  var timer = null, queued = null;
  function schedule(root) {
    queued = root;
    if (timer) return;
    timer = setTimeout(function () {
      timer = null;
      run(queued);
    }, 700);
  }

  function boot() {
    if (document.documentElement.getAttribute('data-bb-translate') === 'off') return;
    var lang = (document.documentElement.lang || '').toLowerCase();
    if (lang.indexOf('bn') === 0) return;
    // যা আছে তা অনুবাদ করা হবে প্রথম ব্যাচে
    window.__bbStart = function () {
      return run(document.body).then(function () {
        hidePill();
        return { nodes: seen, batches: seen, done: done, failed: failures };
      });
    };
    window.__bbStart();
    if (window.MutationObserver) {
      try {
        new MutationObserver(function (records) {
          for (var i = 0; i < records.length; i++) {
            var r = records[i];
            if (r.target && r.target.getAttribute &&
                r.target.getAttribute('data-bb-no')) continue;
            if (r.addedNodes && r.addedNodes.length) { schedule(document.body); return; }
          }
        }).observe(document.body, { childList: true, subtree: true });
      } catch (e) { /* কিছু সাইটে body নাও থাকতে পারে */ }
    }
  }

  if (document.body) boot();
  else document.addEventListener('DOMContentLoaded', boot, false);
})();
