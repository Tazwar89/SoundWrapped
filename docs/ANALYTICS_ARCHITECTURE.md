# Analytics Architecture

## Overview

SoundCloud API **does not provide listening history or platform-wide analytics**. Unlike Spotify (which Discord and PlayStation Network use), SoundCloud doesn't offer:
- ❌ "Currently Playing" endpoint
- ❌ Listening history endpoint
- ❌ Activity stream/feed
- ❌ Real-time listening status

**Why Discord/PlayStation Network work with Spotify:**
- Spotify provides `/v1/me/player/currently-playing` endpoint
- These platforms poll this endpoint periodically to see what's playing NOW
- This gives real-time status (but not historical data)

**Why this doesn't work with SoundCloud:**
- SoundCloud API has no equivalent endpoints
- SoundCloud representatives confirmed: "We do not offer an API to get listening history"
- This is a fundamental limitation of the SoundCloud API

To build comprehensive analytics (similar to Spotify Wrapped), we need alternative approaches.

## Available from SoundCloud API

The following data **is available** directly from SoundCloud API:

- User profile information (username, followers, following, etc.)
- User's uploaded tracks (metadata: title, artist, duration, playback_count)
- User's liked tracks list
- User's playlists
- User's followers/following lists
- **Playback counts** (global plays on tracks, NOT user-specific)

## What We Track via Browser Extension

Since SoundCloud API doesn't provide user-specific listening history, we track activity **on SoundCloud.com** using a browser extension:

### Tracked Events

1. **Play Events** - When a user plays a track on SoundCloud.com (detected by browser extension)
   - Track ID
   - Play duration (milliseconds)
   - Timestamp

2. **Like Events** - When a user likes a track on SoundCloud.com (detected by browser extension)
   - Track ID
   - Timestamp

3. **Repost Events** - When a user reposts a track on SoundCloud.com (detected by browser extension)
   - Track ID
   - Timestamp

4. **Share Events** - When a user shares a track on SoundCloud.com (detected by browser extension)
   - Track ID
   - Timestamp

## Database Schema

### UserActivity Entity

Stores all user activity within the app:

```java
- id: Long (primary key)
- soundcloudUserId: String (SoundCloud user ID)
- trackId: String (SoundCloud track ID)
- activityType: Enum (PLAY, LIKE, REPOST, SHARE)
- playDurationMs: Long (for PLAY events)
- createdAt: LocalDateTime
```

## Services

### ActivityTrackingService

Handles logging user activity:

- `trackPlay(userId, trackId, durationMs)` - Log a play event
- `trackLike(userId, trackId)` - Log a like event
- `trackRepost(userId, trackId)` - Log a repost event
- `trackShare(userId, trackId)` - Log a share event

### AnalyticsService

Aggregates analytics from both sources:

- `getDashboardAnalytics()` - Combines API data + tracked activity
- Returns:
  - **API Stats**: Profile data, track counts from SoundCloud
  - **Tracked Stats**: In-app plays, listening hours, likes
  - **Available Metrics**: Combined view with limitations noted

## API Endpoints

### Track Activity

- `POST /api/activity/track/play?trackId=...&durationMs=...`
- `POST /api/activity/track/like?trackId=...`
- `POST /api/activity/track/repost?trackId=...`

### Get Analytics

- `GET /api/soundcloud/dashboard/analytics` - Returns combined analytics

## Dashboard Display

The Dashboard now clearly distinguishes between:

1. **API-Available Metrics** (from SoundCloud):
   - Available Tracks
   - Profile Likes
   - Followers/Following counts

2. **In-App Tracked Metrics** (only tracks activity within SoundWrapped):
   - In-App Listening Hours
   - In-App Plays
   - In-App Likes

### Limitations Notice

The Dashboard displays an informational banner explaining:
- SoundCloud API doesn't provide listening history
- In-app stats only reflect activity within SoundWrapped
- Platform-wide listening data is not available
- To build comprehensive analytics, users should use SoundWrapped's player

## Alternative Solutions

### Option 1: Last.fm Integration (Recommended)

**Best for**: Cross-platform tracking, comprehensive historical data

**How it works**:
- Users connect their Last.fm account
- Last.fm tracks listening across SoundCloud, Spotify, and other platforms
- We fetch listening data from Last.fm API
- Provides complete listening history, top tracks, top artists

**Pros**:
- ✅ Tracks platform-wide activity (not just in-app)
- ✅ Historical data available
- ✅ Works across multiple platforms
- ✅ No need for custom player

**Cons**:
- ⚠️ Requires Last.fm account
- ⚠️ User must enable scrobbling
- ⚠️ Adoption may be limited

See [LASTFM_INTEGRATION.md](./LASTFM_INTEGRATION.md) for implementation details.

### Option 2: Browser Extension

**Best for**: Desktop users who want automatic tracking

**How it works**:
- Install browser extension that monitors SoundCloud activity
- Extension tracks plays, likes, reposts
- Sends data to SoundWrapped backend

**Pros**:
- ✅ Tracks actual SoundCloud platform activity
- ✅ Automatic (no user action needed)
- ✅ Works across all SoundCloud pages

**Cons**:
- ⚠️ Only works on desktop browsers
- ⚠️ Requires extension installation
- ⚠️ Privacy concerns (monitoring web activity)

### Option 3: Desktop App Integration

**Best for**: Power users who want comprehensive tracking

**How it works**:
- Native desktop app integrates with SoundCloud
- Monitors system audio or SoundCloud app
- Sends listening data to SoundWrapped

**Pros**:
- ✅ Tracks all SoundCloud activity
- ✅ Works even when using SoundCloud app
- ✅ Can integrate with system-level audio

**Cons**:
- ⚠️ Requires desktop app development
- ⚠️ Platform-specific (Windows/Mac/Linux)
- ⚠️ Higher development complexity

### Option 4: System-Level Media Detection (Recommended - Like Music Presence)

**Best for**: Real-time tracking of actual SoundCloud platform activity

**How it works**:
- Desktop app or browser extension monitors system media APIs
- Detects when SoundCloud is playing (desktop app or web)
- Extracts track metadata in real-time
- Sends playback events to SoundWrapped backend

**Pros**:
- ✅ Tracks actual SoundCloud platform activity (not just in-app)
- ✅ Works with SoundCloud desktop app AND web player
- ✅ Real-time detection
- ✅ No SoundCloud API dependency
- ✅ Similar to how Music Presence works

**Cons**:
- ⚠️ Requires desktop app or browser extension development
- ⚠️ Users must install app/extension
- ⚠️ Platform-specific code needed (Windows/macOS/Linux)

**Implementation**: See [SYSTEM_LEVEL_TRACKING.md](./SYSTEM_LEVEL_TRACKING.md) for details.

### Option 5: In-App Player (Current Implementation)

**Best for**: Users who want to use SoundWrapped as their primary player

**How it works**:
- Integrate SoundCloud SDK for in-app playback
- Track all plays within SoundWrapped app
- Store activity in our database

**Pros**:
- ✅ Full control over tracking
- ✅ No external dependencies
- ✅ Works immediately

**Cons**:
- ⚠️ Only tracks in-app activity
- ⚠️ Doesn't track SoundCloud platform activity
- ⚠️ Requires users to use SoundWrapped player

## Future Enhancements

To build a complete "Wrapped" experience:

1. **Implement Last.fm Integration** - Add optional Last.fm connection for users who have it
2. **Build Custom Player**: Integrate SoundCloud SDK to stream tracks in-app
3. **Track All Plays**: Log every play event with duration
4. **Track Over Time**: Store data for a year to generate annual summary
5. **Calculate Statistics**: 
   - Total listening hours
   - Top tracks (by play count)
   - Top artists (by listening time)
   - Listening patterns (time of day, day of week)

## Implementation Notes

- Activity tracking is **opt-in** based on user engagement with the app
- Only tracks activity within SoundWrapped, not on SoundCloud platform
- Data accumulates over time - new users will have minimal tracked stats initially
- For comprehensive analytics, users need to actively use the SoundWrapped player

