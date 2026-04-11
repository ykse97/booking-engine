## Booking Engine deployment

This repository is now prepared for:

- `frontend` on Render Static Site
- `backend` on Render Web Service (Docker)
- PostgreSQL on Neon

### Files added for deployment

- `render.yaml` for Render Blueprint-based setup
- `backend/Dockerfile` for the Spring Boot backend
- `backend/.env.local.example`
- `backend/.env.production.example`
- `frontend/.env.development.example`
- `frontend/.env.production.example`

For local development after removing hardcoded secrets and API URLs, you can copy:

- `.env.example` -> `.env`
- `backend/.env.local.example` -> `backend/.env.local`
- `frontend/.env.development.example` -> `frontend/.env.development`

Spring Boot and Vite are configured to read those local env files automatically when they exist.

### Security operations

- Secret rotation runbook: [docs/security-secret-rotation.md](docs/security-secret-rotation.md)
- Dependency scan profile: run `mvn verify -Psecurity-scan` inside `backend`
- The `security-scan` profile now fails on feed/update errors by default, so it behaves as a real release gate.
- Set `NVD_API_KEY` to reduce rate limiting and make CI/local feed updates more reliable.
- Keep admin bootstrap disabled in steady-state production. Enable it only for a controlled first bootstrap or an explicit password rotation window.

### Required backend environment variables

Set these in Render for the backend service:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_SECURITY_TRUSTED_PROXY_CIDRS` when the backend is deployed behind Render/Cloudflare/Nginx/another reverse proxy
- `APP_STRIPE_SECRET_KEY`
- `APP_STRIPE_WEBHOOK_SECRET`
- `APP_STRIPE_PUBLISHABLE_KEY`
- `APP_JWT_SECRET`
- `APP_AUTH_COOKIE_SECURE=true` for HTTPS production deployments
- `APP_AUTH_COOKIE_SAME_SITE=None` when the admin frontend is served from a different origin than the backend API

The remaining backend variables have defaults in `render.yaml`.
Admin bootstrap variables are intentionally optional in steady-state production
and should only be set for a controlled first bootstrap or password rotation.

### Required frontend environment variables

Set this in Render for the static site:

- `VITE_API_BASE_URL`

Example:

```env
VITE_API_BASE_URL=https://your-backend-service.onrender.com
```

For local frontend development with the Vite proxy:

```env
VITE_DEV_API_PROXY_TARGET=http://localhost:8080
VITE_API_BASE_URL=
```

### Neon connection string mapping

Neon usually gives a URL in this format:

```text
postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require
```

For this project, split it into:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DATABASE?sslmode=require&channelBinding=require
SPRING_DATASOURCE_USERNAME=USER
SPRING_DATASOURCE_PASSWORD=PASSWORD
```

Notes:

- For JDBC, use `channelBinding=require` instead of libpq-style `channel_binding=require`.
- If Neon provides a pooled host, prefer the pooled endpoint for the Spring Boot backend.
- If Neon shows additional query parameters, append them to `SPRING_DATASOURCE_URL` as well.

### Render setup

1. Push this repository to GitHub/GitLab.
2. In Render, create services from `render.yaml`.
3. Fill in all `sync: false` environment variables in the Render dashboard.
4. Keep `SPRING_PROFILES_ACTIVE=prod` for the backend service.
5. For first-time admin provisioning only, set:
   - `APP_ADMIN_BOOTSTRAP_ENABLED=true`
   - `APP_ADMIN_BOOTSTRAP_USERNAME` to the admin username you want
   - `APP_ADMIN_BOOTSTRAP_PASSWORD` to a strong password
   - leave `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=false`
6. Set `VITE_API_BASE_URL` to your backend Render URL.
7. Set `APP_CORS_ALLOWED_ORIGINS` to your frontend Render URL.
8. Deploy backend first, then deploy frontend.
9. After admin login is verified, set `APP_ADMIN_BOOTSTRAP_ENABLED=false`, keep `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=false`, and remove bootstrap username/password env vars if your platform allows it.
10. After deploy, verify:
   - `GET /actuator/health` on backend
   - public site loads services/barbers/hair salon data
   - booking page can reach backend API
   - admin login works and the browser receives an `HttpOnly` admin auth cookie

### Routing notes

The frontend build contains:

- `index.html` for the public SPA
- `admin.html` for the admin app

Render routes are configured in `render.yaml` so:

- all SPA routes rewrite to `index.html`
- `/admin` redirects to `admin.html`
- existing files such as `admin.html` and built assets are still served directly

### Browser security headers

- Frontend HTML and static assets are served by Render Static Site, so the production browser security headers live in `render.yaml` under the frontend service's `headers` block.
- Backend API responses keep Spring Security header enforcement and now also emit an API-only `Content-Security-Policy` that denies active content entirely.
- The frontend CSP is intentionally scoped to the current production dependencies: same-origin assets, Google Fonts, Unsplash marketing images, Stripe browser assets, and API calls to `https://*.onrender.com`.
- Because Render Blueprints do not support variable interpolation in header values, change the frontend `connect-src` directive in `render.yaml` if you move the backend API to a custom domain.
- The frontend `Permissions-Policy` intentionally avoids disabling the `payment` capability because the public booking flow uses Stripe-hosted payment UI and wallet features.
- The frontend HSTS policy uses `max-age=31536000` without `includeSubDomains`; add `includeSubDomains` only if every subdomain on your production frontend host is HTTPS-only.
