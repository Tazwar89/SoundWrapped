# API Keys Setup Guide

This document explains which API keys are needed for SoundWrapped and how to obtain them.

## Quick Reference

| API Key | Required | Free Tier | Features Affected |
|---------|----------|-----------|-------------------|
| SoundCloud Client ID/Secret | **Yes** | N/A | Core app (OAuth, all data) |
| Groq API Key | Recommended | Yes (generous) | AI descriptions, poetry, sonic archetype |
| Last.fm API Key + Secret | Recommended | Yes | Similar artists, scrobbling sync |
| Google Knowledge Graph API Key | Optional | Yes (limited) | Entity descriptions for artists/genres |
| SerpAPI Key | Optional | 250/month | Enhanced web search context |
| TheAudioDB API Key | Optional | Yes | Enhanced artist profiles, artwork |

## Required API Keys

### 1. SoundCloud API Keys
- **Variables**: `SOUNDCLOUD_CLIENT_ID`, `SOUNDCLOUD_CLIENT_SECRET`
- **Where to get**: https://developers.soundcloud.com/
- **Purpose**: Core OAuth2 authentication and all SoundCloud data access

### 2. Groq API Key (Recommended)
- **Variable**: `GROQ_API_KEY`
- **Where to get**: https://console.groq.com/
- **Purpose**: AI-powered descriptions (artists, genres), Year in Review Poetry, Sonic Archetype generation
- **Model**: `llama-3.3-70b-versatile`
- **Free Tier**: Yes — generous free tier, no credit card required
- **Fallback**: Without Groq, descriptions fall back to Wikipedia/Google KG extracts or generic text

### 3. Last.fm API Key + Secret (Recommended)
- **Variables**: `LASTFM_API_KEY`, `LASTFM_API_SECRET`
- **Where to get**: https://www.last.fm/api/account/create
- **Purpose**: Similar artists discovery, scrobble syncing (listening history), Web Auth OAuth
- **Free Tier**: Yes
- **Both key and secret are needed**: The secret is used to generate API signatures for `auth.getSession` during the OAuth flow
- **How to get**:
  1. Visit https://www.last.fm/api/account/create
  2. Fill out the form:
     - **Application name**: SoundWrapped
     - **Application description**: Music analytics platform
     - **Application homepage**: `http://localhost:8080` (or your production URL)
     - **Callback URL**: `http://localhost:8080/api/lastfm/callback`
  3. Accept terms and click "Submit"
  4. You'll receive an **API Key** and **Shared Secret** — you need both
- **⚠️ Callback URL**: Must be set correctly in Last.fm app settings for OAuth to work. Last.fm will redirect users to this URL after authorization.
- **Localhost Note**: Last.fm may not redirect to localhost URLs. Use [ngrok](https://ngrok.com) for local development:
  ```bash
  ngrok http 8080
  # Use the HTTPS URL as your callback, e.g.: https://abc123.ngrok.io/api/lastfm/callback
  ```

## Optional API Keys

### 4. Google Knowledge Graph API Key
- **Variable**: `GOOGLE_KNOWLEDGE_GRAPH_API_KEY`
- **Where to get**: https://console.cloud.google.com/ (enable Knowledge Graph Search API)
- **Purpose**: Fetches structured entity descriptions for artists and genres
- **Free Tier**: Yes (limited daily quota)
- **Fallback**: Wikipedia API is used as primary fallback

### 5. SerpAPI Key
- **Variable**: `SERPAPI_API_KEY`
- **Where to get**: https://serpapi.com/
- **Purpose**: Comprehensive web search for additional artist/genre context. Provides knowledge graph, answer boxes, and organic results in one call.
- **Free Tier**: 250 searches/month
- **Fallback**: Descriptions use Wikipedia + Google KG without SerpAPI

### 6. TheAudioDB API Key
- **Variable**: `THEAUDIODB_API_KEY`
- **Where to get**: https://www.theaudiodb.com/api_guide.php
- **Purpose**: Enhanced artist profiles — high-quality artwork, discography, biographies, music videos
- **Free Tier**: Yes (basic usage may not require a key — check their docs)
- **Fallback**: Artist of the Day uses SoundCloud's default artist information

## Adding Keys to `.env` File

Create or update `backend/soundwrapped-backend/.env`:

```bash
# Required
SOUNDCLOUD_CLIENT_ID=your_soundcloud_client_id
SOUNDCLOUD_CLIENT_SECRET=your_soundcloud_client_secret

# Recommended
GROQ_API_KEY=your_groq_api_key
LASTFM_API_KEY=your_lastfm_api_key
LASTFM_API_SECRET=your_lastfm_shared_secret

# Optional
GOOGLE_KNOWLEDGE_GRAPH_API_KEY=your_google_api_key
SERPAPI_API_KEY=your_serpapi_key
THEAUDIODB_API_KEY=your_theaudiodb_key

# Last.fm callback configuration
LASTFM_CALLBACK_URL=http://localhost:8080/api/lastfm/callback
APP_FRONTEND_BASE_URL=http://localhost:3000

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/soundwrapped_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

## Feature Availability Without Keys

| Missing Key | Impact | Fallback |
|-------------|--------|----------|
| Groq | No AI descriptions, poetry, or archetypes | Wikipedia/Google KG extracts, generic text |
| Last.fm | No similar artists, no scrobble sync | Artist of the Day still works; no listening history beyond in-app tracking |
| Google KG | Reduced description quality | Wikipedia API used instead |
| SerpAPI | Slightly less research context for AI | Wikipedia + Google KG provide sufficient context |
| TheAudioDB | No enhanced artwork or discography | SoundCloud's default artist info displayed |

All core features (OAuth, featured content, analytics, Wrapped) work with only SoundCloud keys. The optional keys enhance the experience but are not required.

## Testing Your Keys

After adding keys and restarting the backend:

1. **SoundCloud**: Click "Connect SoundCloud" — should redirect to SoundCloud auth
2. **Groq**: Check "Artist of the Day" description — should be AI-generated (2-3 sentences)
3. **Last.fm**: Click "Connect Last.fm" on Dashboard — should redirect to Last.fm auth
4. **TheAudioDB**: Check "Artist of the Day" — should show enhanced artwork below description
5. **SerpAPI**: Check backend logs for `SerpAPI search` entries during artist/genre description generation

## Security Notes

- **Never commit** `.env` files to version control (already in `.gitignore`)
- **Rotate keys** if accidentally exposed
- **Use environment variables** in production — do not embed keys in `application.yml`
- Keys in `application.yml` use `${ENV_VAR:}` syntax — empty default means the feature is disabled when the env var is unset

