# SMARTSOFT Trading Enterprise — Website

A responsive multi-page company website built with plain HTML, CSS and JavaScript (no build step, no frameworks).

## Pages
| Page | File |
|------|------|
| Home | `index.html` |
| Products (with category filter) | `products.html` |
| Product detail – Vision Inspection | `vision-inspection.html` |
| Services | `services.html` |
| Automation PLC | `plc.html` |
| Our Team | `team.html` |
| Contact (form + map) | `contact.html` |
| Admin dashboard | `admin/dashboard.html` |
| Admin – manage products (add / edit / delete) | `admin/products.html` |

A chatbot widget (keyword-based replies) appears on every public page.

## Run
Open `index.html` in a browser, or serve the folder:

```bash
python3 -m http.server 8000
```

## Notes
- Contact form submissions and admin product edits are saved in the browser's `localStorage` (demo only — there is no backend). Submitted messages appear under **Recent Inquiries** on the admin dashboard.
- Illustrations in `img/` are SVG placeholders; replace them with real product and team photos.
- The admin area has no login; add server-side authentication before using it for real.
