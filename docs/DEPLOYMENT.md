# 🚀 SoundWrapped Deployment Guide

This guide covers SoundWrapped's production deployment on Render.com, as well as local development setup and alternative deployment options.

## 📋 Production Architecture

SoundWrapped is deployed on [Render](https://render.com) with three services:

| Component | Service Type | URL |
|-----------|-------------|-----|
| **Frontend** | Static Site | [soundwrapped.onrender.com](https://soundwrapped.onrender.com) |
| **Backend** | Web Service (Docker) | [soundwrapped-backend.onrender.com](https://soundwrapped-backend.onrender.com) |
| **Database** | PostgreSQL Add-on | Managed by Render (internal connection string) |

## 🏗️ Local Development Setup

### 1. Clone and Setup
```bash
git clone https://github.com/tazwarsikder/SoundWrapped.git
cd SoundWrapped
```

### 2. Environment Configuration
```bash
# Backend: create .env in backend/soundwrapped-backend/
cp backend/soundwrapped-backend/.env.example backend/soundwrapped-backend/.env
nano backend/soundwrapped-backend/.env

# Frontend: create .env in frontend/
cp frontend/env.example frontend/.env
nano frontend/.env
```

### 3. Start Development Environment
```bash
# Start all services with Docker Compose
docker-compose up --build

# Or run individually:
# Backend:
cd backend/soundwrapped-backend && mvn spring-boot:run

# Frontend (separate terminal):
cd frontend && npm install && npm run dev
```

### 4. Access Application
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Database: localhost:5432

## 🌐 Render.com Deployment (Production)

### Backend — Web Service (Docker)

1. **Create a Render Web Service**
   - Connect your GitHub repository
   - **Environment**: Docker
   - **Docker Context Directory**: `backend/soundwrapped-backend`
   - **Dockerfile Path**: `backend/soundwrapped-backend/Dockerfile`
   - Render auto-detects the multi-stage Maven build

2. **Environment Variables** (set in Render dashboard)
   ```env
   SPRING_PROFILES_ACTIVE=default
   SPRING_DATASOURCE_URL=jdbc:postgresql://<render-internal-db-host>:5432/<db-name>
   SPRING_DATASOURCE_USERNAME=<db-user>
   SPRING_DATASOURCE_PASSWORD=<db-password>
   SOUNDCLOUD_CLIENT_ID=<your-client-id>
   SOUNDCLOUD_CLIENT_SECRET=<your-client-secret>
   REDIRECT_URI=https://soundwrapped-backend.onrender.com/callback
   APP_FRONTEND_BASE_URL=https://soundwrapped.onrender.com
   LASTFM_API_KEY=<your-lastfm-key>
   LASTFM_API_SECRET=<your-lastfm-secret>
   LASTFM_CALLBACK_URL=https://soundwrapped-backend.onrender.com/api/lastfm/callback
   GROQ_API_KEY=<your-groq-key>
   GOOGLE_KNOWLEDGE_GRAPH_API_KEY=<your-google-key>
   SERPAPI_API_KEY=<your-serpapi-key>
   THEAUDIODB_API_KEY=<your-theaudiodb-key>
   ```

3. **Create PostgreSQL Add-on**
   - Add a PostgreSQL instance from the Render dashboard
   - Copy the internal connection string into `SPRING_DATASOURCE_URL`
   - Render manages backups, scaling, and access control

4. **Notes**
   - The Dockerfile `EXPOSE`s port 10000; Render sets the `PORT` environment variable accordingly
   - The app binds to `0.0.0.0:${PORT:8080}` via `application.yml`
   - JVM flags: `-Xms256m -Xmx600m -XX:+TieredCompilation -XX:TieredStopAtLevel=1`
   - Schema management: Hibernate `ddl-auto: update` (no Flyway/Liquibase migrations)

### Frontend — Static Site

1. **Create a Render Static Site**
   - Connect your GitHub repository
   - **Root Directory**: `frontend`
   - **Build Command**: `npm install && npm run build`
   - **Publish Directory**: `dist`

2. **Environment Variables** (set in Render dashboard)
   ```env
   VITE_API_BASE_URL=https://soundwrapped-backend.onrender.com/api
   VITE_BACKEND_URL=https://soundwrapped-backend.onrender.com
   ```

3. **SPA Routing**
   - `frontend/public/_redirects` contains `/* /index.html 200` for client-side routing
   - This ensures React Router handles all paths (Render respects `_redirects` files like Netlify)

### SoundCloud OAuth Redirect URI

The SoundCloud app dashboard must have the production callback URL registered:
```
https://soundwrapped-backend.onrender.com/callback
```

This is the URL SoundCloud redirects to after user authorization. The backend's `OAuthCallbackController` handles it and redirects the user to the frontend (`APP_FRONTEND_BASE_URL`).

### Last.fm Callback URL

The Last.fm callback URL must be accessible from the internet:
```
https://soundwrapped-backend.onrender.com/api/lastfm/callback
```

For local development, use [ngrok](https://ngrok.com) to expose `localhost:8080` with a public URL.

## 🔧 Configuration

### Environment Variables

#### Backend
```env
# Required
SOUNDCLOUD_CLIENT_ID=<your-client-id>
SOUNDCLOUD_CLIENT_SECRET=<your-client-secret>
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/dbname
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
REDIRECT_URI=https://soundwrapped-backend.onrender.com/callback
APP_FRONTEND_BASE_URL=https://soundwrapped.onrender.com

# Recommended
GROQ_API_KEY=<your-groq-key>
LASTFM_API_KEY=<your-lastfm-key>
LASTFM_API_SECRET=<your-lastfm-secret>
LASTFM_CALLBACK_URL=https://soundwrapped-backend.onrender.com/api/lastfm/callback

# Optional
GOOGLE_KNOWLEDGE_GRAPH_API_KEY=<your-google-key>
SERPAPI_API_KEY=<your-serpapi-key>
THEAUDIODB_API_KEY=<your-theaudiodb-key>
```

#### Frontend
```env
VITE_API_BASE_URL=https://soundwrapped-backend.onrender.com/api
VITE_BACKEND_URL=https://soundwrapped-backend.onrender.com
```

## 🔍 Monitoring and Logs

### Render Dashboard
- **Logs**: Available in real-time via the Render dashboard for each service
- **Metrics**: CPU, memory, and request metrics visible per service
- **Deploys**: Auto-deploy on push to the connected branch

### Health Checks
```bash
# Check backend API
curl https://soundwrapped-backend.onrender.com/api/soundcloud/debug/test-api

# Check frontend
curl https://soundwrapped.onrender.com
```

### Local Docker Logs
```bash
docker-compose logs -f backend
docker-compose logs -f frontend
```

## 🔒 Security Considerations

- **Environment Variables**: Never commit `.env` files to version control (the `.gitignore` already excludes them). All secrets are configured via the Render dashboard.
- **CORS**: `CorsConfig.java` allows `https://soundwrapped.onrender.com` and localhost origins for API endpoints. Extension tracking endpoints use `allowedOriginPatterns("*")`.
- **HTTPS**: Render provides free TLS certificates for all services.
- **Database**: Render PostgreSQL uses internal connection strings not accessible from the public internet.
- **Token Storage**: OAuth tokens are stored in the database, not in cookies or localStorage on the backend side.

## 🚨 Troubleshooting

### Render Backend Spins Down (Free Tier)
Render free-tier Web Services spin down after 15 minutes of inactivity. The first request after spin-down takes 30-60 seconds while the container restarts.

### SoundCloud OAuth Callback Fails
- Ensure `REDIRECT_URI` matches the URL registered in the SoundCloud app dashboard exactly
- Ensure `APP_FRONTEND_BASE_URL` is set so the backend redirects to the frontend after auth

### Last.fm Callback Fails
- Ensure `LASTFM_CALLBACK_URL` points to the deployed backend, not localhost
- For local development, use ngrok to expose `localhost:8080` with a public URL

### Frontend API Calls Fail
- Ensure `VITE_API_BASE_URL` points to the deployed backend URL (including `/api` suffix)
- Check browser console for CORS errors — the backend must include the frontend origin in `CorsConfig.java`

### Database Schema Changes
The app uses Hibernate `ddl-auto: update`. New entity fields are added automatically, but column renames or deletions require manual migration.

## 🔄 CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`) runs on push:
- **backend-test**: Maven build + JaCoCo coverage against PostgreSQL 15
- **frontend-test**: npm test (continue-on-error)

Render auto-deploys from the connected Git branch on every push.
