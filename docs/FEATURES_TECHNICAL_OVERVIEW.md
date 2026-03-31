# SoundWrapped Feature Guide (Interview-Ready)

This document summarizes product features, technical implementation details, and integration
architecture — useful when explaining SoundWrapped during interviews. It consolidates details
from the Groq, Last.fm, and SerpAPI integration docs.

## Product Summary
- Goal: Generate a personalized music analytics experience on top of SoundCloud data,
  with optional Last.fm scrobbling for richer listening history.
- Core value: Users see actionable insights (taste, patterns, rankings, location trends)
  instead of raw playback lists.
- Differentiator: Combines multiple data sources (SoundCloud, Last.fm, AI) into a single
  cohesive experience with daily featured content, Wrapped summaries, and social sharing.

## Architecture Overview
- **Frontend**: React 18 + TypeScript + Vite 7.3 (client-side SPA).
- **Backend**: Spring Boot 3.5.5 REST API (Java 17).
- **Persistence**: PostgreSQL 15 via Spring Data JPA/Hibernate.
- **Caching**: Caffeine in-memory caches (6 named caches) + date-seed field caches.
- **Integrations**: SoundCloud OAuth2, Last.fm Web Auth, Groq AI, Wikipedia, Google Knowledge Graph, SerpAPI, TheAudioDB, Lyrics.ovh.

## Auth & Identity

### SoundCloud OAuth2
- Flow: Frontend redirects to SoundCloud auth → backend handles `/callback?code=` exchange.
- Tokens stored in DB via `TokenStore` for reuse and automatic refresh (`TokenRefreshScheduler`).
- Redirect URI: `http://localhost:8080/callback` (dev), configurable for production.

### Last.fm Web Auth
- Auth URL uses only `api_key` + `cb` callback URL (pure Web Auth mode — no request token).
- After user authorizes, Last.fm redirects to `GET /api/lastfm/callback?token=TOKEN`.
- Backend exchanges token for session key via `auth.getSession` with API signature (MD5 hash of sorted params + secret).
- Session key and username stored in `LastFmToken` entity.
- Dev note: Last.fm may refuse localhost callbacks; use ngrok for local dev.

## Homepage — Daily Featured Content

### Song / Artist / Genre of the Day
- Time-seed selection: `LocalDate.now().toEpochDay()` → `Random` seed ensures same pick all day.
- Artist description pipeline: Wikipedia → Google Knowledge Graph → SerpAPI → Groq AI synthesis.
- Lyrics fetched automatically for Song of the Day via Lyrics.ovh (non-blocking).
- Enhanced artist info enriched via TheAudioDB (artwork, discography).
- Cached as in-memory fields; refreshed daily.

### Buzzing Track
- Fetches all playlists from SoundCloud user `buzzing-playlists`.
- Aggregates all tracks into a single pool.
- Deterministic daily pick via date-seed (`year*10000 + month*100 + day`).
- Labels with "Artist to watch out for".

### Popular Now
- Returns tracks from US Top 50 charts playlist (URN `1714689261`) in original order.

## Dashboard (Primary UX)
- Consolidates user analytics:
  - Genre analysis (discovery count, top genres by track count and listening time, distribution)
  - Listening patterns (peak hour, peak day, persona: Early Bird / Afternoon / Evening / Night Owl)
  - Genre Constellation (interactive 3D Canvas visualization)
  - Recent activity feed
  - Last.fm connection status and sync controls
- Uses cached endpoints and React Query for deduplication.

## Wrapped Summary
- Slide-by-slide presentation with 8 shareable story card types.
- **Phase 1**: Story cards (9:16, social-ready), Underground Support %, Year in Review Poetry (AI-generated).
- **Phase 2**: Trendsetter Score (early adoption badges), Repost King/Queen, Sonic Archetype (AI persona).
- **Phase 3**: 6 color themes, 3 font sizes for story card customization.

## Analytics & Insights Features

### Music Taste Map
- Aggregates artists, genres, and affinity scores by city.
- Rate-limited: max 10 users per city, 10 API calls per city.
- Frontend renders interactive map with clustered labels.

### Music Doppelgänger
- Compares aggregated listening profiles (top artists/genres overlap).
- Normalized metrics for fair comparison; produces similarity score + explanation.

### Listening Pattern Analysis
- `ListeningPatternService`: Analyzes `UserActivity.createdAt` timestamps.
- Groups by hour (0-23) and day of week, calculates peak times and persona.

### Genre Analysis
- `GenreAnalysisService`: Extracts genres from `genre`, `genre_family`, and `tag_list` fields.
- Normalizes names (e.g., "hip-hop" → "hip hop"), aggregates by count and duration.

### Enhanced Artist Insights
- `EnhancedArtistService`: TheAudioDB for artwork, discography, biographies.
- `SimilarArtistsService`: Last.fm `artist.getSimilar` with match scores.
- Both cached via Caffeine (24h and 12h respectively).

### Lyrics & Metadata Enrichment
- `LyricsService`: Lyrics.ovh API (no auth required).
- Cleans artist names (removes "feat.", "ft."), cleans titles (removes remix info).
- Best-effort, non-blocking — 404 is expected for many tracks.
- Cached 7 days (lyrics rarely change).

---

## Groq AI Integration (Detailed)

### Why Groq
- **Free tier**: Generous limits, no credit card required.
- **Fast inference**: Ultra-fast response times with optimized models.
- **OpenAI-compatible**: Uses OpenAI chat completions format.
- Migrated from OpenAI due to quota limitations and cost.

### Model & Configuration
- **Model**: `llama-3.3-70b-versatile`
- **Base URL**: `https://api.groq.com/openai/v1`
- **Config**: `GROQ_API_KEY` env var, mapped via `groq.api-key` in `application.yml`.

### Features Using Groq

| Feature | Method | Tokens | Temperature | Purpose |
|---------|--------|--------|-------------|---------|
| Artist Descriptions | `getGroqDescription()` | 150 | 0.7 | 2-3 sentence bio from research context |
| Genre Descriptions | `getFeaturedGenreWithTracks()` | 150 | 0.7 | Concise genre description |
| Year in Review Poetry | `generateYearInReviewPoetry()` | 150 | 0.7 | 4-6 line personalized poem |
| Sonic Archetype | `generateSonicArchetype()` | 200 | 0.8 | Creative persona title + description |

### Research-First Pipeline (Artist/Genre Descriptions)
1. **Wikipedia API**: Fetch full extract paragraph from `/api/rest_v1/page/summary/{name}`.
2. **Google Knowledge Graph API**: Retrieve entity description.
3. **SerpAPI** (optional): Comprehensive web search — knowledge graph, answer box, organic results.
4. **Groq synthesis**: Single request with aggregated research context → 50-100 word description.

### Fallback Chain
1. Groq AI-generated description (primary)
2. Wikipedia extract
3. Google Knowledge Graph description
4. SoundCloud bio
5. Generic fallback text

### Error Handling
- Invalid API key: Detected and logged, returns null.
- Quota exceeded: Handled gracefully, falls back.
- Network errors: Comprehensive try-catch with fallback descriptions.

### Caching
- `groqDescriptions` Caffeine cache: 1h TTL, 1000 entries.
- Key: `{entityName}|{entityType}` (lowercase).

---

## Last.fm Integration (Detailed)

### Architecture

```
SoundCloud Play → Web Scrobbler Extension → Last.fm → SoundWrapped (every 15 min)
```

### Services
- **`LastFmService`**: Consolidated REST API client.
  - `getApiKey()`, `isConfigured()` — configuration checks.
  - `exchangeTokenForSession(token)` — session key exchange with API signature.
  - `fetchUsername(sessionKey)` — resolve username from session.
  - `getRecentTracks(username, fromTimestamp, limit)` — poll scrobbles.
  - `generateSignature(params)` — MD5 hash of sorted `key=value` pairs + secret.

- **`LastFmScrobblingService`**: Scheduled sync job.
  - Runs every 15 minutes via `@Scheduled`.
  - Polls `user.getRecentTracks` for all connected users.
  - Fuzzy-matches scrobbles to SoundCloud tracks via search API.
  - Creates `UserActivity` records with `source=LASTFM`.
  - Uses Unix timestamp (`uts` field) for accurate scrobble timing.
  - Stores unmatched tracks (with `lastFmArtist` + `lastFmTrack` fields) for analytics.

- **`SimilarArtistsService`**: `artist.getSimilar` endpoint with match scores and images.

### Data Model
- `LastFmToken` entity: `soundcloudUserId`, `lastFmUsername`, `sessionKey`, `lastSyncAt`.
- `UserActivity` entity additions: `source` enum (INAPP/LASTFM), `matchedSoundCloudTrackId`, `lastFmArtist`, `lastFmTrack`.

### REST Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/lastfm/auth-url` | Web Auth authorization URL |
| GET | `/api/lastfm/callback?token=` | OAuth callback (→ session key exchange → redirect) |
| GET | `/api/lastfm/callback/test` | Test callback accessibility |
| GET | `/api/lastfm/status` | Connection status |
| POST | `/api/lastfm/disconnect` | Remove stored session |
| POST | `/api/lastfm/sync` | Manual sync trigger |

### User Setup Flow
1. Install [Web Scrobbler](https://webscrobbler.com) browser extension.
2. Connect Last.fm account in Web Scrobbler settings (SoundCloud is a built-in connector).
3. Connect Last.fm in SoundWrapped Dashboard → click "Connect Last.fm".
4. Authorize on Last.fm → redirects back to SoundWrapped.
5. Scrobble sync begins automatically every 15 minutes.

### Benefits
- Cross-browser (Chrome, Firefox, Safari, Edge, Opera).
- Industry standard — many users already have Last.fm accounts.
- No custom browser extension to maintain.

### Limitations
- Only tracks play events (not likes/reposts).
- Estimated duration (defaults to 3 min per scrobble).
- Track matching may have false positives (artist + title search).

---

## SerpAPI Integration (Detailed)

### Purpose
Provides comprehensive web search results as additional context for Groq AI-generated descriptions.

### What It Provides (in a single call)
- **Knowledge Graph**: Same structured data as Google Knowledge Graph API.
- **Answer Box**: Quick facts and definitions.
- **Organic Results**: Top search results with snippets.
- **Related Questions**: "People also ask" data.

### Implementation
- Method: `getSerpAPIDescription(searchTerm)` in `SoundWrappedService`.
- Appends "music artist" or "music genre" to search terms for better context.
- Extracts: knowledge graph description → answer box → top 3 organic result snippets.
- Integrated as a research source alongside Wikipedia and Google Knowledge Graph.

### Configuration
```yaml
serpapi:
  api-key: ${SERPAPI_API_KEY:}
```

### Cost
- Free tier: 250 searches/month.
- For daily featured content (2 searches/day), free tier covers ~125 days.

### Value for Obscure Artists/Genres
- Web search finds information when Wikipedia/Google KG have no entries.
- Pulls from music blogs, news sites, and community sources.

---

## Performance & Caching

### Backend Caching (Caffeine)

| Cache | TTL | Max Size | Purpose |
|-------|-----|----------|---------|
| `groqDescriptions` | 1h | 1,000 | AI-generated descriptions |
| `enhancedArtists` | 24h | 500 | TheAudioDB artist info |
| `similarArtists` | 12h | 500 | Last.fm similar artists |
| `lyrics` | 7d | 2,000 | Lyrics.ovh results |
| `popularTracks` | 30m | 10 | SoundCloud popular tracks |
| `soundcloudTrackSearch` | 24h | 5,000 | Last.fm → SoundCloud track mapping |

Impact: 70-90% reduction in external API calls, 100-300x faster cached responses.

### Frontend
- Route-based code splitting with `React.lazy` + `Suspense`.
- Heavy components (GenreConstellation, charts) lazy loaded.
- React Query for request deduplication and client-side caching.
- Prefetching for critical routes (`usePrefetchWrapped`, `usePrefetchDashboard`).

### Error Handling
- `ErrorBoundary` component: catches React errors, shows user-friendly fallback.
- `useRetry` hook: exponential backoff (1s, 2s, 4s), skips 4xx errors.
- API interceptor: categorizes errors (401/403/404/429/5xx), clears token on 401.

## API Design
- REST endpoints grouped by domain:
  - `/api/soundcloud/**` — SoundCloud data, featured content, analytics
  - `/api/lastfm/**` — Last.fm auth and scrobble management
  - `/api/activity/**` — In-app activity tracking
  - `/api/tracking/**` — System-level playback tracking
- Consistent JSON payloads with error fields for UI handling.

## Data Model (High-Level)
- `Token` entity: SoundCloud access/refresh token, expiry, `soundcloudUserId`.
- `UserActivity` entity: track plays, timestamps, duration, source (INAPP/LASTFM), Last.fm metadata.
- `LastFmToken` entity: Last.fm session key, username, last sync timestamp.
- `UserLocation` entity: IP-based location (city, country, lat/lng).

## Security & Privacy
- Tokens stored server-side; frontend never sees client secrets.
- CORS restricted to trusted frontend origins.
- API signatures (Last.fm) generated server-side with secret key.
- Scrobbling/analytics features are opt-in.

## Deployment Notes
- Local dev: `localhost:8080` (backend), `localhost:3000` (frontend).
- Docker: `localhost:8081` (backend), `localhost:3000` (frontend), `localhost:5432` (Postgres).
- Production: use HTTPS and real domains for OAuth callbacks.
- Last.fm callback URL must match exactly in Last.fm app settings.
- Docker uses `eclipse-temurin:17-jre` base image, multi-stage build.

## Talking Points (How to Present It)
- "I built a data pipeline that turns raw playback data into insights users actually care about."
- "I focused on end-to-end reliability: dual OAuth flows, caching, error boundaries, and graceful fallbacks."
- "I optimized perceived performance with Caffeine caching (100-300x faster cached responses), code splitting, and React Query."
- "I designed the system so integrations (SoundCloud, Last.fm, Groq, SerpAPI) are modular — each can be independently configured or disabled."
- "The AI description pipeline uses a research-first approach: aggregating data from 3 sources before generating a synthesized description with Groq."
- "Last.fm scrobbling uses Web Auth OAuth and scheduled sync, turning third-party listening data into first-class analytics."

