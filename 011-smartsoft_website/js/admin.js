/* SMARTSOFT admin panel — demo data stored in localStorage (no backend). */
(function () {
  'use strict';
  var DEFAULT_PRODUCTS = [
    { name: 'PLC Controller', category: 'PLC & Control', image: 'plc.svg', status: 'Published' },
    { name: 'Vision Camera', category: 'Vision System', image: 'camera.svg', status: 'Published' },
    { name: 'Barcode Printer', category: 'Barcode & Label', image: 'printer.svg', status: 'Published' },
    { name: 'HMI Panel', category: 'PLC & Control', image: 'laptop.svg', status: 'Published' },
    { name: 'Industrial PC', category: 'Software', image: 'laptop.svg', status: 'Published' }
  ];
  var DEFAULT_INQUIRIES = [
    { name: 'Juan Dela Cruz', email: 'juan@email.com', subject: 'Product Inquiry', date: '2026-05-27' },
    { name: 'Maria Santos', email: 'maria@email.com', subject: 'Quote Request', date: '2026-05-26' },
    { name: 'Ramon Reyes', email: 'ramon@email.com', subject: 'Technical Support', date: '2026-05-25' },
    { name: 'Ana Lopez', email: 'ana@email.com', subject: 'General Inquiry', date: '2026-05-24' }
  ];
  function load(k, d) { try { var v = JSON.parse(localStorage.getItem(k)); return Array.isArray(v) ? v : d.slice(); } catch (e) { return d.slice(); } }
  function save(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* ignore */ } }
  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }

  var products = load('ss_products', DEFAULT_PRODUCTS);
  // Real submissions from the contact form come first, followed by demo rows.
  var inquiries = load('ss_inquiries', []).concat(DEFAULT_INQUIRIES);

  document.querySelectorAll('[data-year]').forEach(function (el) { el.textContent = new Date().getFullYear(); });
  var menu = document.querySelector('.menu-btn');
  if (menu) menu.addEventListener('click', function () { document.querySelector('.side').classList.toggle('open'); });

  // ---- Dashboard ----
  var today = document.getElementById('today');
  if (today) {
    today.textContent = new Date().toLocaleDateString([], { month: 'long', day: 'numeric', year: 'numeric' });
    document.getElementById('statProducts').textContent = products.length;
    document.getElementById('statInquiries').textContent = inquiries.length;
    document.getElementById('statPublished').textContent = products.filter(function (p) { return p.status === 'Published'; }).length;
    document.getElementById('inquiryRows').innerHTML = inquiries.slice(0, 10).map(function (q) {
      return '<tr><td>' + esc(q.name) + '</td><td>' + esc(q.email) + '</td><td>' + esc(q.subject) + '</td><td>' + esc(q.date) + '</td></tr>';
    }).join('') || '<tr><td colspan="4" class="empty">No inquiries yet.</td></tr>';
    document.getElementById('clearInquiries').addEventListener('click', function () {
      if (!confirm('Reset products and inquiries to the demo data?')) return;
      localStorage.removeItem('ss_products'); localStorage.removeItem('ss_inquiries'); location.reload();
    });
  }

  // ---- Products CRUD ----
  var rows = document.getElementById('productRows');
  if (!rows) return;
  var dialog = document.getElementById('productDialog');
  var form = document.getElementById('productForm');
  var editing = -1;
  var pencil = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>';
  var trash = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>';

  function render() {
    rows.innerHTML = products.map(function (p, i) {
      return '<tr><td><img src="../img/' + esc(p.image) + '" alt=""></td><td>' + esc(p.name) + '</td><td>' + esc(p.category) + '</td>' +
        '<td><span class="badge' + (p.status === 'Draft' ? ' badge--draft' : '') + '">' + esc(p.status) + '</span></td>' +
        '<td><button class="icon-btn icon-btn--edit" data-edit="' + i + '" aria-label="Edit">' + pencil + '</button>' +
        '<button class="icon-btn icon-btn--del" data-del="' + i + '" aria-label="Delete">' + trash + '</button></td></tr>';
    }).join('') || '<tr><td colspan="5" class="empty">No products. Click &ldquo;Add Product&rdquo;.</td></tr>';
  }
  function open(i) {
    editing = i;
    form.reset();
    document.getElementById('dialogTitle').textContent = i < 0 ? 'Add Product' : 'Edit Product';
    if (i >= 0) Object.keys(products[i]).forEach(function (k) { if (form.elements[k]) form.elements[k].value = products[i][k]; });
    dialog.showModal();
  }
  document.getElementById('addProduct').addEventListener('click', function () { open(-1); });
  document.getElementById('cancelDialog').addEventListener('click', function () { dialog.close(); });
  form.addEventListener('submit', function (e) {
    var data = Object.fromEntries(new FormData(form));
    if (!data.name.trim()) { e.preventDefault(); return; }
    data.name = data.name.trim();
    if (editing < 0) products.push(data); else products[editing] = data;
    save('ss_products', products); render();
  });
  rows.addEventListener('click', function (e) {
    var b = e.target.closest('button'); if (!b) return;
    if (b.dataset.edit) open(+b.dataset.edit);
    if (b.dataset.del && confirm('Delete "' + products[+b.dataset.del].name + '"?')) {
      products.splice(+b.dataset.del, 1); save('ss_products', products); render();
    }
  });
  render();
})();
