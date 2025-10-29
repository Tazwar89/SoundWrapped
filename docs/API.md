# 🎵 SoundWrapped API Documentation

This document provides comprehensive information about the SoundWrapped REST API endpoints.

## 📋 Base Information

- **Base URL**: `http://localhost:8081/api` (development)
- **Content Type**: `application/json`
- **Authentication**: Bearer Token (OAuth2)

## 🔐 Authentication

### OAuth2 Flow

#### 1. SoundCloud Authentication
```
GET /callback?code={authorization_code}
```

**Description**: Exchanges SoundCloud authorization code for access and refresh tokens.

**Parameters**:
- `code` (string, required): Authorization code from SoundCloud

**Response**:
```json
{
  "accessToken": "string",
  "refreshToken": "string"
}
```

#### 2. Token Refresh
The API automatically handles token refresh when access tokens expire.

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

**Description**: Retrieves tracks related to a specific track.

**Parameters**:
- `track_urn` (string, required): SoundCloud track URN

**Response**: Array of related track objects

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

### Application Properties
```yaml
# application.yml
soundcloud:
  client-id: your_client_id
  client-secret: your_client_secret
  api:
    base-url: https://api.soundcloud.com

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/soundwrapped
    username: postgres
    password: postgres
```

### Environment Variables
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/soundwrapped
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# SoundCloud
SOUNDCLOUD_CLIENT_ID=your_client_id
SOUNDCLOUD_CLIENT_SECRET=your_client_secret
```

## 📱 Frontend Integration

### API Client Setup
```typescript
import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8081/api',
  headers: {
    'Content-Type': 'application/json',
  }
})

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

### Example Usage
```typescript
// Get user profile
const profile = await api.get('/soundcloud/profile')

// Get wrapped data
const wrapped = await api.get('/soundcloud/wrapped/full')

// Get user tracks
const tracks = await api.get('/soundcloud/tracks')
```

## 🔒 Security Considerations

### Authentication
- OAuth2 Bearer tokens
- Automatic token refresh
- Secure token storage

### Data Protection
- Input validation
- SQL injection prevention
- XSS protection
- CORS configuration

### Rate Limiting
- Request throttling
- API quota management
- Error handling

## 📈 Performance

### Optimization Features
- Connection pooling
- Caching strategies
- Pagination support
- Async processing

### Monitoring
- Request logging
- Performance metrics
- Error tracking
- Health checks

## 🚀 Deployment

### Docker Support
```bash
# Build image
docker build -t soundwrapped-backend .

# Run container
docker run -p 8081:8081 soundwrapped-backend
```

### Environment Configuration
- Development: `application.yml`
- Production: Environment variables
- Testing: `application-test.yml`

---

**For more information, visit the [GitHub Repository](https://github.com/your-username/SoundWrapped) or contact the development team.**
