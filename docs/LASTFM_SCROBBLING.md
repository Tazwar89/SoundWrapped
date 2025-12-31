# Last.fm Scrobbling Integration

## Overview

SoundWrapped now uses **Last.fm scrobbling** instead of a custom browser extension to track user listening activity. This provides better cross-browser support and a more standardized approach to music tracking.

## How It Works

### 1. User Setup

1. **Install Web Scrobbler Extension**:
   - Users install the [Web Scrobbler](https://webscrobbler.com) extension
   - Works on Chrome, Firefox, Safari, Edge, and Opera
   - Free and open-source

2. **Connect Last.fm in Web Scrobbler**:
   - In Web Scrobbler settings, go to the **Accounts** section
   - Connect your **Last.fm** account (this is where scrobbles will be sent)
   - **Note**: SoundCloud is automatically supported as a built-in connector - you don't need to "connect" SoundCloud separately. It's already in the Connectors list and will work automatically when you visit SoundCloud.com

3. **Connect Last.fm to SoundWrapped**:
   - Users visit SoundWrapped Dashboard
   - Click "Connect Last.fm" button
   - Authorize SoundWrapped to access their Last.fm data
   - This allows SoundWrapped to sync the scrobbled data from Last.fm

### 2. Automatic Tracking Flow

```
SoundCloud Play → Web Scrobbler → Last.fm → SoundWrapped (every 15 min)
```

1. User plays a track on SoundCloud
2. Web Scrobbler detects the play and scrobbles to Last.fm
3. Last.fm stores the scrobble with artist, title, and timestamp
4. SoundWrapped polls Last.fm API every 15 minutes
5. SoundWrapped matches scrobbles to SoundCloud tracks
6. UserActivity records are created in the database

### 3. Backend Implementation

#### Services

- **`LastFmScrobblingService`**: 
  - Scheduled job runs every 15 minutes
  - Polls Last.fm API for recent tracks (`user.getRecentTracks`)
  - Matches scrobbles to SoundCloud tracks via search
  - Creates `UserActivity` records

- **`LastFmController`**:
  - `/api/lastfm/auth-url`: Get Last.fm authorization URL
  - `/api/lastfm/callback`: Handle OAuth callback
  - `/api/lastfm/status`: Check connection status
  - `/api/lastfm/disconnect`: Disconnect Last.fm account
  - `/api/lastfm/sync`: Manually trigger sync

#### Database

- **`LastFmToken` Entity**: Stores Last.fm session keys per user
  - `soundcloudUserId`: Links to SoundCloud user
  - `lastFmUsername`: Last.fm username
  - `sessionKey`: Last.fm API session key
  - `lastSyncAt`: Timestamp of last sync

### 4. Frontend Implementation

- **`LastFmConnection` Component**: 
  - Shows connection status
  - "Connect Last.fm" button
  - Manual sync trigger
  - Setup instructions
  - Displays last sync time

- **Dashboard Integration**:
  - Component appears at top of Dashboard
  - Clear instructions for Web Scrobbler setup
  - Visual indicators for connection status

## Benefits Over Browser Extension

### ✅ Cross-Browser Support
- Works on Chrome, Firefox, Safari, Edge, Opera
- No need to build separate extensions for each browser

### ✅ Cross-Platform
- Works on desktop, mobile, and web
- Web Scrobbler supports multiple platforms

### ✅ Industry Standard
- Last.fm is the standard for music scrobbling
- Many users already have Last.fm accounts
- Well-maintained and reliable

### ✅ Automatic Syncing
- No manual intervention needed
- Background sync every 15 minutes
- Users can trigger manual sync if needed

### ✅ Better User Experience
- Single extension (Web Scrobbler) works for all music services
- Users can track Spotify, YouTube, SoundCloud, etc. in one place
- No custom extension to maintain

## Limitations

### ⚠️ Only Tracks Plays
- Last.fm scrobbling only tracks play events
- Likes, reposts, and shares are not tracked
- This is a limitation of Last.fm, not SoundWrapped

### ⚠️ Requires User Setup
- Users must install Web Scrobbler extension
- Users must connect their Last.fm account in Web Scrobbler's Accounts section
- SoundCloud is automatically supported (no separate connection needed)
- Users must connect Last.fm account in SoundWrapped to sync the data

### ⚠️ Track Matching
- Scrobbles are matched to SoundCloud tracks via search
- May not find exact matches for all tracks
- Uses artist + title search, which may have false positives

### ⚠️ Estimated Duration
- Last.fm doesn't provide exact play duration
- Defaults to 3 minutes per scrobble
- This is less accurate than browser extension tracking

## Configuration

### Backend

Add to `application.yml`:
```yaml
lastfm:
  api-key: ${LASTFM_API_KEY:}
  api-secret: ${LASTFM_API_SECRET:}
```

Or set environment variables:
```bash
LASTFM_API_KEY=your_api_key
LASTFM_API_SECRET=your_api_secret
```

### Frontend

No configuration needed. The component automatically detects the backend API.

## API Endpoints

### GET `/api/lastfm/auth-url`
Get Last.fm authorization URL.

**Response:**
```json
{
  "authUrl": "https://www.last.fm/api/auth?api_key=...",
  "message": "Visit this URL to authorize SoundWrapped with Last.fm"
}
```

### GET `/api/lastfm/callback?token=...`
Handle Last.fm OAuth callback.

**Response:**
```json
{
  "success": true,
  "message": "Last.fm account connected successfully",
  "username": "user123"
}
```

### GET `/api/lastfm/status`
Check Last.fm connection status.

**Response:**
```json
{
  "connected": true,
  "username": "user123",
  "lastSyncAt": "2025-12-30T09:00:00"
}
```

### POST `/api/lastfm/disconnect`
Disconnect Last.fm account.

**Response:**
```json
{
  "success": true,
  "message": "Last.fm account disconnected"
}
```

### POST `/api/lastfm/sync`
Manually trigger sync.

**Response:**
```json
{
  "success": true,
  "message": "Sync triggered successfully"
}
```

## Migration from Browser Extension

If you were using the browser extension:

1. **Uninstall the old extension** (if installed)
2. **Install Web Scrobbler** from [webscrobbler.com](https://webscrobbler.com)
3. **Connect Last.fm** in Web Scrobbler's Accounts section (SoundCloud is automatically supported)
4. **Connect Last.fm** in SoundWrapped Dashboard
5. **Your listening data will start syncing automatically**

Existing `UserActivity` records from the browser extension will remain in the database, but new activity will come from Last.fm scrobbling.

## Troubleshooting

### "No new scrobbles" message
- Check that Web Scrobbler is installed and enabled
- Verify SoundCloud is connected in Web Scrobbler
- Make sure you've played tracks on SoundCloud recently
- Check Last.fm profile to see if scrobbles are being recorded

### "Failed to match track"
- Track might not be on SoundCloud
- Artist/title might not match exactly
- This is expected for some tracks

### "Connection failed"
- Verify Last.fm API key and secret are configured
- Check backend logs for detailed error messages
- Ensure Last.fm account is valid

## Future Improvements

- [ ] Better track matching algorithm (fuzzy matching, multiple search strategies)
- [ ] Track duration from Last.fm (if available)
- [ ] Support for likes/reposts via SoundCloud API (separate from scrobbling)
- [ ] Real-time sync option (WebSocket or polling on demand)
- [ ] Batch sync for historical data

