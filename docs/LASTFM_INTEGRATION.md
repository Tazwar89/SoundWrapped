# Last.fm Scrobbling Integration

## Overview

Since SoundCloud API doesn't provide listening history, we can integrate with **Last.fm's scrobbling API** to track user listening activity across multiple platforms, including SoundCloud.

## How Last.fm Scrobbling Works

1. **User connects Last.fm account** - Users authorize SoundWrapped to access their Last.fm data
2. **Last.fm tracks listening** - Last.fm aggregates listening data from:
   - SoundCloud (if user has Last.fm scrobbling enabled)
   - Spotify (if connected)
   - Other music platforms
   - Desktop apps with Last.fm scrobbler
3. **We fetch the data** - SoundWrapped can query Last.fm API to get:
   - Recent tracks
   - Top tracks (all time, week, month)
   - Top artists
   - Listening history
   - Total scrobbles

## Implementation Steps

### 1. Last.fm API Setup

```java
// Last.fm API Configuration
lastfm:
  api-key: "YOUR_LASTFM_API_KEY"
  api-secret: "YOUR_LASTFM_API_SECRET"
  base-url: "https://ws.audioscrobbler.com/2.0"
```

### 2. OAuth Flow

```java
// Last.fm Authentication
GET https://www.last.fm/api/auth?api_key=YOUR_API_KEY&cb=YOUR_CALLBACK_URL

// User authorizes, then:
GET https://ws.audioscrobbler.com/2.0/?method=auth.getSession
  &api_key=YOUR_API_KEY
  &token=TOKEN_FROM_AUTH
  &api_sig=GENERATED_SIGNATURE
```

### 3. Fetch Listening Data

```java
// Get recent tracks
GET /2.0/?method=user.getrecenttracks
  &user=USERNAME
  &api_key=API_KEY
  &format=json

// Get top tracks
GET /2.0/?method=user.gettoptracks
  &user=USERNAME
  &period=7day|1month|3month|6month|12month|overall
  &api_key=API_KEY
  &format=json

// Get top artists
GET /2.0/?method=user.gettopartists
  &user=USERNAME
  &period=7day|1month|3month|6month|12month|overall
  &api_key=API_KEY
  &format=json
```

## Benefits

- ✅ **Cross-platform tracking** - Works across SoundCloud, Spotify, and other platforms
- ✅ **Historical data** - Access to user's complete listening history
- ✅ **Real-time updates** - Last.fm updates as user listens
- ✅ **Rich analytics** - Top tracks, artists, listening patterns
- ✅ **No need for in-app player** - Tracks activity on SoundCloud platform itself

## Limitations

- ⚠️ **User must have Last.fm account** - Requires additional signup
- ⚠️ **User must enable scrobbling** - Must connect Last.fm to SoundCloud
- ⚠️ **Not all users have Last.fm** - Adoption may be limited
- ⚠️ **Dependency on third-party** - Relies on Last.fm service

## User Flow

1. User connects SoundCloud account (already done)
2. User connects Last.fm account (new step)
3. User enables Last.fm scrobbling in SoundCloud settings (if not already enabled)
4. SoundWrapped fetches listening data from Last.fm API
5. Display comprehensive analytics from Last.fm data

## Implementation Priority

**Recommended**: Implement Last.fm integration as an optional enhancement
- Primary: In-app tracking (already implemented)
- Secondary: Last.fm integration (for users who have it)
- Fallback: Show what's available from SoundCloud API only

