# 🎵 SoundWrapped API Documentation

This document provides comprehensive information about the SoundWrapped REST API endpoints.

## 📋 Base Information

- **Base URL**: `http://localhost:8080/api` (development), `http://localhost:8081/api` (Docker)
- **Content Type**: `application/json`
- **Authentication**: Bearer Token (SoundCloud OAuth2 access token)

## 🔐 Authentication

### SoundCloud OAuth2 Flow

#### 1. SoundCloud Authorization Callback
```
GET /callback?code={authorization_code}
```

Exchanges SoundCloud authorization code for access and refresh tokens. Redirects to frontend with access token.

#### 2. Proactive Token Refresh
```
POST /api/soundcloud/refresh-token
```

Manually triggers a token refresh. Backend also auto-refreshes via `TokenRefreshScheduler`.

### Last.fm Web Auth Flow

#### Get Authorization URL
```
GET /api/lastfm/auth-url
```

Returns the Last.fm Web Auth URL using only `api_key` + `cb` (no request token).

**Response**:
```json
{
  "authUrl": "https://www.last.fm/api/auth?api_key=...&cb=http://localhost:8080/api/lastfm/callback",
  "message": "Visit this URL to authorize SoundWrapped with Last.fm"
}
```

#### OAuth Callback
```
GET /api/lastfm/callback?token={token}
```

Handles Last.fm callback — exchanges token for session key via `auth.getSession`, redirects to frontend.

#### Test Callback Accessibility
```
GET /api/lastfm/callback/test
```

#### Connection Status
```
GET /api/lastfm/status
```

**Response**:
```json
{
  "connected": true,
  "username": "user123",
  "lastSyncAt": "2025-01-15T09:00:00"
}
```

#### Disconnect Last.fm
```
POST /api/lastfm/disconnect
```

#### Manual Scrobble Sync
```
POST /api/lastfm/sync
```

Triggers an immediate sync. Default automatic sync occurs every 15 minutes.

## 📊 User Data Endpoints

### Get User Profile
```
GET /soundcloud/profile
```

**Description**: Retrieves the authenticated user's SoundCloud profile information.

**Headers**:
- `Authorization: Bearer {access_token}`

**Response**:
```json
{
  "id": 123456789,
  "username": "johndoe",
  "full_name": "John Doe",
  "followers_count": 1500,
  "followings_count": 300,
  "public_favorites_count": 250,
  "reposts_count": 45,
  "track_count": 25,
  "playlist_count": 8,
  "comments_count": 120,
  "upload_seconds_left": 3600,
  "created_at": "2013/03/23 14:58:27 +0000",
  "avatar_url": "https://i1.sndcdn.com/avatars-000123456789-abc123-t500x500.jpg"
}
```

### Get User Tracks
```
GET /soundcloud/tracks
```

**Description**: Retrieves the user's uploaded tracks with pagination.

**Response**:
```json
[
  {
    "id": 123456789,
    "title": "My Amazing Track",
    "user": {
      "username": "johndoe"
    },
    "duration": 180000,
    "playback_count": 1500,
    "likes_count": 45,
    "reposts_count": 12,
    "created_at": "2024/01/15 10:30:00 +0000",
    "artwork_url": "https://i1.sndcdn.com/artworks-000123456789-abc123-t500x500.jpg"
  }
]
```

### Get User Likes
```
GET /soundcloud/likes
```

**Description**: Retrieves the user's liked tracks.

**Response**: Array of track objects (same format as tracks endpoint)

### Get User Playlists
```
GET /soundcloud/playlists
```

**Description**: Retrieves the user's playlists.

**Response**:
```json
[
  {
    "id": 123456789,
    "title": "My Favorite Songs",
    "track_count": 25,
    "likes_count": 15,
    "created_at": "2024/01/10 15:20:00 +0000",
    "artwork_url": "https://i1.sndcdn.com/artworks-000123456789-abc123-t500x500.jpg"
  }
]
```

### Get User Followers
```
GET /soundcloud/followers
```

**Description**: Retrieves the user's followers.

**Response**:
```json
[
  {
    "id": 987654321,
    "username": "follower1",
    "full_name": "Follower One",
    "created_at": "2020/05/15 12:00:00 +0000",
    "avatar_url": "https://i1.sndcdn.com/avatars-000987654321-def456-t500x500.jpg"
  }
]
```

## 🎯 Wrapped Analytics

### Get Full Wrapped Summary
```
GET /soundcloud/wrapped/full
```

**Description**: Generates a comprehensive "SoundCloud Wrapped"-style summary with analytics and insights.

**Response**:
```json
{
  "profile": {
    "username": "johndoe",
    "accountAgeYears": 5,
    "followers": 1500,
    "tracksUploaded": 25,
    "playlistsCreated": 8
  },
  "topTracks": [
    {
      "rank": 1,
      "title": "My Best Track",
      "artist": "johndoe",
      "playCount": 2500
    }
  ],
  "topArtists": [
    {
      "rank": 1,
      "artist": "Favorite Artist"
    }
  ],
  "topRepostedTracks": [
    {
      "title": "Viral Track",
      "reposts": 45
    }
  ],
  "stats": {
    "totalListeningHours": 120.5,
    "likesGiven": 250,
    "tracksUploaded": 25,
    "commentsPosted": 120,
    "booksYouCouldHaveRead": 8
  },
  "funFact": "You're pretty famous! 🎉",
  "peakYear": "2024 (150 likes)",
  "globalTasteComparison": "Listeners in New York, Berlin and Tokyo share your musical taste! 🎧",
  "stories": [
    "🎶 Your #1 track this year was \"My Best Track\" by johndoe. You just couldn't get enough of it!",
    "🔥 You vibed most with Favorite Artist — clearly your top artist of the year.",
    "⌛ You spent 120.5 hours listening — enough to binge whole seasons of your favorite shows!"
  ]
}
```

## 🔍 Related Tracks

### Get Related Tracks
```
GET /soundcloud/tracks/{track_urn}/related
```

**Parameters**:
- `track_urn` (string, required): SoundCloud track URN

**Response**: Array of related track objects

## 🎯 Featured Content

### Song of the Day
```
GET /api/soundcloud/featured/track
```

Returns the daily featured track with lyrics (if available via Lyrics.ovh). Includes embedded SoundCloud player.

### Artist of the Day
```
GET /api/soundcloud/featured/artist?forceRefresh={boolean}
```

Returns daily artist with AI-generated description (Groq), enhanced info (TheAudioDB), and top tracks.

### Genre of the Day
```
GET /api/soundcloud/featured/genre
```

Returns daily genre with AI-generated description and sample tracks.

### Popular Now
```
GET /api/soundcloud/popular/tracks?limit={limit}
```

Returns tracks from the US Top 50 charts playlist in original order. Default limit: 4.

### Buzzing Track
```
GET /api/soundcloud/buzzing
```

Returns a daily buzzing track from SoundCloud's buzzing playlists, deterministically selected via date-based seed. Includes `buzzing_label: "Artist to watch out for"`.

### Clear Featured Cache
```
POST /api/soundcloud/featured/clear-cache
```

### Similar Artists
```
GET /api/soundcloud/similar-artists?artist={name}&limit={limit}
```

Returns similar artists via Last.fm API with match scores and percentages.

## 📈 Analytics

### Full Wrapped Summary
```
GET /api/soundcloud/wrapped/full
```

Comprehensive Wrapped summary including: profile, top tracks/artists, stats, stories, underground support %, year-in-review poetry, trendsetter score, repost score, and sonic archetype.

### Dashboard Analytics
```
GET /api/soundcloud/dashboard/analytics
```

Combined analytics including genre analysis (discovery count, top genres, distribution) and listening patterns (peak hour/day, persona, distributions).

### Music Doppelgänger
```
GET /api/soundcloud/music-doppelganger
```

### Artist Analytics
```
GET /api/soundcloud/artist/analytics
```

### Artist Recommendations
```
GET /api/soundcloud/artist/recommendations?trackId={trackId}
```

### Music Taste Map
```
GET /api/soundcloud/music-taste-map
```

### Recent Activity
```
GET /api/soundcloud/recent-activity?limit={limit}
```

### Online Users
```
GET /api/soundcloud/online-users
```

## 🎮 Activity Tracking

### Track Play
```
POST /api/activity/track/play?trackId={trackId}&durationMs={durationMs}
```

### Track Like
```
POST /api/activity/track/like?trackId={trackId}
```

### Track Repost
```
POST /api/activity/track/repost?trackId={trackId}
```

### System Playback (Desktop/Extension)
```
POST /api/tracking/system-playback
Body: { "trackId": "...", "title": "...", "artist": "...", "durationMs": 180000 }
```

### System Like
```
POST /api/tracking/system-like?trackId={trackId}
```

### Update Location
```
POST /api/tracking/update-location
```

## 🐛 Debug Endpoints

```
GET /api/soundcloud/debug/test-api     — Test SoundCloud API connection
GET /api/soundcloud/debug/tokens       — Token status
GET /api/soundcloud/debug/oauth-url    — Generate OAuth URL
```

## 📊 Caching

### Caffeine In-Memory Caches

| Cache | TTL | Max Size | Purpose |
|-------|-----|----------|---------|
| `groqDescriptions` | 1 hour | 1,000 | AI-generated descriptions |
| `enhancedArtists` | 24 hours | 500 | TheAudioDB artist info |
| `similarArtists` | 12 hours | 500 | Last.fm similar artists |
| `lyrics` | 7 days | 2,000 | Lyrics.ovh results |
| `popularTracks` | 30 min | 10 | SoundCloud popular tracks |
| `soundcloudTrackSearch` | 24 hours | 5,000 | Last.fm → SoundCloud track mapping |

Daily featured content (Song, Artist, Genre, Buzzing) uses in-memory field caching with date-based seeds.

## ⚠️ Error Handling

### Error Response Format
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "path": "/api/soundcloud/profile",
  "stackTrace": "Full stack trace..."
}
```

### Common Error Codes

| Status Code | Error | Description |
|-------------|-------|-------------|
| 400 | Bad Request | Invalid request parameters |
| 401 | Unauthorized | Invalid or expired token |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

### Specific Error Types

#### TokenExchangeException
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Token exchange failed",
  "message": "Authorization code must not be empty.",
  "path": "/callback"
}
```

#### TokenRefreshException
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 401,
  "error": "Token refresh failed",
  "message": "Missing refresh token; cannot refresh access token.",
  "path": "/api/soundcloud/profile"
}
```

#### ApiRequestException
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 502,
  "error": "API request failed",
  "message": "GET request failed: 401 Unauthorized",
  "path": "/api/soundcloud/tracks"
}
```

## 🔄 Pagination

### Paginated Endpoints
- `/soundcloud/tracks`
- `/soundcloud/likes`
- `/soundcloud/playlists`
- `/soundcloud/followers`

### Pagination Response
```json
{
  "collection": [
    // Array of items
  ],
  "next_href": "https://api.soundcloud.com/me/tracks?linked_partitioning=1&limit=50&offset=50"
}
```

## 📊 Rate Limiting

### SoundCloud API Limits
- **Requests per hour**: 15,000
- **Requests per day**: 200,000

### Implementation
- Automatic retry with exponential backoff
- Token refresh on 401 errors
- Request queuing for rate limit compliance

## 🧪 Testing

### Test Endpoints
The API includes comprehensive test coverage:

#### Unit Tests
- Service layer testing with mocks
- Token management testing
- Data processing validation

#### Integration Tests
- Database integration testing
- API endpoint testing
- End-to-end workflow testing

#### Test Data
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SoundWrappedServiceTests

# Run integration tests
mvn test -Dtest=SoundWrappedServiceIntegrationTest
```

## 🔧 Configuration

### Application Profiles

| Profile | Database | Port | Use |
|---------|----------|------|-----|
| `default` | PostgreSQL (localhost:5432) | 8080 | Local development |
| `test` | Testcontainers | — | Automated tests |
| `docker` | PostgreSQL (db:5432) | 8081 | Docker deployment |

### Required Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `SOUNDCLOUD_CLIENT_ID` | Yes | SoundCloud OAuth |
| `SOUNDCLOUD_CLIENT_SECRET` | Yes | SoundCloud OAuth |
| `GROQ_API_KEY` | Recommended | AI descriptions, poetry, archetypes |
| `LASTFM_API_KEY` | Recommended | Similar artists, scrobbling |
| `LASTFM_API_SECRET` | Recommended | Last.fm session auth signatures |
| `GOOGLE_KNOWLEDGE_GRAPH_API_KEY` | Optional | Entity descriptions |
| `SERPAPI_API_KEY` | Optional | Web search context |
| `THEAUDIODB_API_KEY` | Optional | Enhanced artist profiles |

## 📱 Frontend Integration

```typescript
import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

## 🔒 Security

- OAuth2 Bearer tokens stored server-side; frontend never sees client secrets
- Automatic token refresh on 401 responses
- CORS restricted to trusted frontend origins
- Input validation and SQL injection prevention via JPA parameterized queries
