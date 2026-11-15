/* SMARTSOFT Trading Enterprise — public site scripts */
(function () {
  'use strict';

  // ---- Mobile navigation ----
  var toggle = document.querySelector('.nav-toggle');
  var nav = document.querySelector('.nav');
  if (toggle && nav) {
    toggle.addEventListener('click', function () {
      var open = nav.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
    });
  }

  // ---- Footer year ----
  document.querySelectorAll('[data-year]').forEach(function (el) {
    el.textContent = new Date().getFullYear();
  });

  // ---- Product filter ----
  var chips = document.querySelectorAll('.chip[data-filter]');
  chips.forEach(function (chip) {
    chip.addEventListener('click', function () {
      chips.forEach(function (c) { c.classList.remove('active'); });
      chip.classList.add('active');
      var f = chip.dataset.filter;
      document.querySelectorAll('#productGrid .card').forEach(function (card) {
        card.classList.toggle('hidden', f !== 'all' && card.dataset.category !== f);
      });
    });
  });

  // ---- Shared storage helpers (also used by the admin pages) ----
  function load(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) || fallback; } catch (e) { return fallback; }
  }
  function save(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* storage unavailable */ }
  }

  // ---- Contact form ----
  var form = document.getElementById('contactForm');
  if (form) {
    var msg = form.querySelector('.form-msg');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var data = Object.fromEntries(new FormData(form));
      var emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email || '');
      if (!data.name.trim() || !emailOk || !data.message.trim()) {
        msg.textContent = 'Please fill in your name, a valid email and a message.';
        msg.style.background = '#fdecea'; msg.style.color = '#b42318';
        msg.classList.add('show');
        return;
      }
      var inquiries = load('ss_inquiries', []);
      inquiries.unshift({ name: data.name.trim(), email: data.email.trim(), subject: data.subject, message: data.message.trim(), date: new Date().toISOString().slice(0, 10) });
      save('ss_inquiries', inquiries);
      msg.textContent = 'Thank you, ' + data.name.trim() + '! Your message has been sent. We’ll get back to you soon.';
      msg.style.background = ''; msg.style.color = '';
      msg.classList.add('show');
      form.reset();
    });
  }

  // ---- Chatbot ----
  var replies = [
    { k: ['price', 'cost', 'how much', 'quotation', 'quote'], a: 'Our PLC systems start at ₱50,000 depending on the brand and configuration. Would you like a quotation or more details? You can request one on our <a href="contact.html#quote">contact page</a>.' },
    { k: ['plc', 'omron', 'allen', 'control'], a: 'A PLC (Programmable Logic Controller) is an industrial computer that controls machines and processes. We supply Omron, Allen-Bradley and more, plus programming, HMI and SCADA integration. <a href="plc.html">Learn more</a>.' },
    { k: ['vision', 'camera', 'inspection', 'defect'], a: 'Our AI-powered vision inspection systems detect defects in real time and integrate with your existing line. <a href="vision-inspection.html">See details</a>.' },
    { k: ['barcode', 'qr', 'label', 'printer', 'scanner'], a: 'We offer industrial barcode scanners, QR readers and label printers for tracking and traceability.' },
    { k: ['software', 'web', 'app', 'mobile', 'desktop'], a: 'We build custom web, desktop, mobile and cloud-based applications tailored to your business.' },
    { k: ['product', 'offer', 'service', 'what do'], a: 'We offer industrial automation, PLC systems, vision inspection, barcode &amp; QR solutions, and custom software development. Which product are you interested in?' },
    { k: ['contact', 'phone', 'email', 'address', 'location', 'where'], a: 'You can reach us at 5710598 or info@smartsoft.com.ph. We’re in Barangay San Vicente, Angono, Rizal.' },
    { k: ['hour', 'open', 'schedule'], a: 'We’re open Monday to Friday, 8:00 AM – 5:00 PM. Closed on weekends.' },
    { k: ['hi', 'hello', 'hey', 'good'], a: 'Hello! 👋 How can I help you today?' }
  ];
  function answer(text) {
    var t = text.toLowerCase();
    for (var i = 0; i < replies.length; i++) {
      if (replies[i].k.some(function (k) { return t.indexOf(k) !== -1; })) return replies[i].a;
    }
    return 'Thanks for your message! A member of our team can help with that. Please leave your details on our <a href="contact.html">contact page</a> or call 5710598.';
  }
  function time() { return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); }
  function escapeHtml(s) { return s.replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }

  var widget = document.createElement('div');
  widget.innerHTML =
    '<button class="chat-fab" aria-label="Open chat"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16v11H9l-5 4z"/></svg></button>' +
    '<section class="chat" aria-label="SMARTSOFT Chatbot">' +
      '<div class="chat__head"><img src="img/logo.svg" alt=""><div><strong>SMARTSOFT Chatbot</strong><small>Online</small></div><button class="chat__close" aria-label="Close chat">&times;</button></div>' +
      '<div class="chat__body" aria-live="polite"></div>' +
      '<div class="chat__quick"><button>What are your products?</button><button>How much is a PLC system?</button><button>Business hours</button></div>' +
      '<form class="chat__form"><input placeholder="Type a message..." aria-label="Message"><button aria-label="Send"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg></button></form>' +
    '</section>';
  document.body.appendChild(widget);

  var fab = widget.querySelector('.chat-fab');
  var chat = widget.querySelector('.chat');
  var body = widget.querySelector('.chat__body');
  var input = widget.querySelector('.chat__form input');

  function add(html, who) {
    var m = document.createElement('div');
    m.className = 'msg msg--' + who;
    m.innerHTML = html + '<time>' + time() + '</time>';
    body.appendChild(m);
    body.scrollTop = body.scrollHeight;
  }
  function send(text) {
    if (!text.trim()) return;
    add(escapeHtml(text), 'user');
    setTimeout(function () { add(answer(text), 'bot'); }, 450);
  }

  var greeted = false;
  fab.addEventListener('click', function () {
    chat.classList.toggle('open');
    if (!greeted) { add('Hi! I’m the SMARTSOFT assistant. Ask me about our products, PLC systems, pricing or business hours.', 'bot'); greeted = true; }
    if (chat.classList.contains('open')) input.focus();
  });
  widget.querySelector('.chat__close').addEventListener('click', function () { chat.classList.remove('open'); });
  widget.querySelector('.chat__form').addEventListener('submit', function (e) { e.preventDefault(); send(input.value); input.value = ''; });
  widget.querySelectorAll('.chat__quick button').forEach(function (b) { b.addEventListener('click', function () { send(b.textContent); }); });
})();
