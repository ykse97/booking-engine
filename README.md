## Booking Engine deployment

This repository is now prepared for:

- `frontend` on Render Static Site
- `backend` on Render Web Service (Docker)
- PostgreSQL on Neon

### Files added for deployment

- `render.yaml` for Render Blueprint-based setup
- `backend/Dockerfile` for the Spring Boot backend
- `backend/.env.production.example`
- `frontend/.env.production.example`

For local backend runs after removing hardcoded secrets, you can copy:

- `backend/.env.production.example` -> `backend/.env.production`

Spring Boot is configured to import that local file automatically if it exists.

### Required backend environment variables

Set these in Render for the backend service:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_STRIPE_SECRET_KEY`
- `APP_STRIPE_WEBHOOK_SECRET`
- `APP_STRIPE_PUBLISHABLE_KEY`
- `APP_ADMIN_BOOTSTRAP_PASSWORD`
- `APP_JWT_SECRET`

The remaining backend variables have defaults in `render.yaml`.
`APP_ADMIN_BOOTSTRAP_ENABLED=true` is enabled there on purpose so a clean cloud
database does not keep the insecure seeded `admin / password` credentials.

### Required frontend environment variables

Set this in Render for the static site:

- `VITE_API_BASE_URL`

Example:

```env
VITE_API_BASE_URL=https://your-backend-service.onrender.com
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
4. Set `APP_ADMIN_BOOTSTRAP_PASSWORD` to a strong password for the `admin` user.
5. Set `VITE_API_BASE_URL` to your backend Render URL.
6. Set `APP_CORS_ALLOWED_ORIGINS` to your frontend Render URL.
7. Deploy backend first, then deploy frontend.
8. After deploy, verify:
   - `GET /actuator/health` on backend
   - public site loads services/barbers/hair salon data
   - booking page can reach backend API
   - admin login works

### Routing notes

The frontend build contains:

- `index.html` for the public SPA
- `admin.html` for the admin app

Render routes are configured in `render.yaml` so:

- all SPA routes rewrite to `index.html`
- `/admin` redirects to `admin.html`
- existing files such as `admin.html` and built assets are still served directly
