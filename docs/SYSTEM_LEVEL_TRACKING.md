# System-Level Media Tracking (Inspired by Music Presence)

## Overview

Instead of relying on SoundCloud API (which doesn't provide listening history), we can track SoundCloud activity using **system-level media detection APIs**, similar to how [Music Presence](https://github.com/ungive/discord-music-presence) works.

## How It Works

### System-Level APIs

1. **Windows - Media Session API**
   - Detects any media playing on Windows
   - Provides: artist, title, album, artwork, playback state, position
   - Works with: SoundCloud desktop app, web browsers, any media player

2. **macOS - Now Playing API**
   - Detects system-wide media playback
   - Provides: metadata, artwork, playback state
   - Works with: SoundCloud app, Safari, Chrome, any media player

3. **Linux - MPRIS (Media Player Remote Interfacing Specification)**
   - Standard protocol for media player control
   - Provides: metadata, playback state, position
   - Works with: Most Linux media players

### Browser Extension (Future)

Music Presence plans browser extension support (October 2025), which would:
- Monitor SoundCloud web player directly
- Track plays, likes, reposts
- Send data to SoundWrapped backend

## Implementation Approaches

### Option 1: Desktop App (Recommended)

**Build a native desktop application** that:
- Monitors system media playback
- Detects when SoundCloud is playing
- Extracts track metadata
- Sends data to SoundWrapped backend in real-time

**Technologies:**
- **Windows**: C++/C# using Windows Media Session API
- **macOS**: Swift/Objective-C using Now Playing API
- **Linux**: C++/Python using MPRIS

**Architecture:**
```
Desktop App → System API → Detect SoundCloud → Extract Metadata → POST to SoundWrapped API
```

**Pros:**
- ✅ Tracks actual SoundCloud platform activity
- ✅ Works with desktop app AND web player (via system APIs)
- ✅ Real-time detection
- ✅ No user action needed after installation

**Cons:**
- ⚠️ Requires native app development (3 platforms)
- ⚠️ Users must install desktop app
- ⚠️ More complex than browser extension

### Option 2: Browser Extension

**Build a browser extension** that:
- Monitors SoundCloud web player
- Detects track changes, plays, likes
- Sends data to SoundWrapped backend

**Technologies:**
- Chrome Extension API
- Firefox WebExtensions API
- Content scripts to monitor SoundCloud DOM

**Architecture:**
```
Browser Extension → Content Script → Monitor SoundCloud DOM → Extract Data → POST to SoundWrapped API
```

**Pros:**
- ✅ Easier to develop (single codebase)
- ✅ Works with SoundCloud web player
- ✅ No desktop app installation needed

**Cons:**
- ⚠️ Only works in browser (not desktop app)
- ⚠️ Requires extension installation
- ⚠️ May break if SoundCloud changes DOM structure

### Option 3: Integrate with Music Presence

**If Music Presence exposes an API** for third-party integration:
- Connect to Music Presence's data stream
- Receive real-time playback events
- Filter for SoundCloud activity
- Store in SoundWrapped database

**Note:** Music Presence mentions "Integrating with Music Presence for third-parties" in their documentation, but API details aren't public yet.

## Recommended Implementation Plan

### Phase 1: Desktop App (Windows First)

1. **Build Windows desktop app**
   - Use Windows Media Session API
   - Detect SoundCloud playback
   - Extract track metadata
   - Send to SoundWrapped backend

2. **Backend Integration**
   - Create endpoint: `POST /api/tracking/system-playback`
   - Store playback events in `UserActivity` table
   - Match tracks to SoundCloud track IDs

### Phase 2: Browser Extension

1. **Build Chrome extension**
   - Monitor SoundCloud web player
   - Track plays, likes, reposts
   - Send to backend

### Phase 3: Cross-Platform

1. **macOS app** using Now Playing API
2. **Linux app** using MPRIS
3. **Unified backend** for all platforms

## Technical Details

### Windows Media Session API Example

```cpp
// C++ example (simplified)
#include <windows.h>
#include <mmdeviceapi.h>
#include <audioclient.h>

// Get media session manager
// Enumerate sessions
// Detect SoundCloud app
// Extract metadata
```

### macOS Now Playing API Example

```swift
// Swift example
import MediaPlayer

let nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo
if let artist = nowPlayingInfo?[MPMediaItemPropertyArtist] as? String,
   let title = nowPlayingInfo?[MPMediaItemPropertyTitle] as? String {
    // Send to SoundWrapped backend
}
```

### Backend Endpoint

```java
@PostMapping("/api/tracking/system-playback")
public ResponseEntity<Map<String, Object>> trackSystemPlayback(
    @RequestBody SystemPlaybackEvent event) {
    
    // Extract SoundCloud track ID from metadata
    String trackId = extractTrackId(event);
    
    // Store in database
    activityTrackingService.trackPlay(
        userId, 
        trackId, 
        event.getDurationMs()
    );
    
    return ResponseEntity.ok(Map.of("success", true));
}
```

## Data Flow

```
SoundCloud Desktop/Web Player
    ↓
System Media API (Windows/macOS/Linux)
    ↓
Desktop App / Browser Extension
    ↓
HTTP POST to SoundWrapped Backend
    ↓
ActivityTrackingService
    ↓
UserActivity Database
    ↓
Analytics Dashboard
```

## Benefits Over Current Approach

| Feature | Current (In-App Only) | System-Level Tracking |
|---------|----------------------|----------------------|
| Tracks SoundCloud platform | ❌ | ✅ |
| Tracks desktop app | ❌ | ✅ |
| Tracks web player | ❌ | ✅ |
| Real-time | ✅ | ✅ |
| Requires user action | ✅ (must use our player) | ❌ (automatic) |

## References

- [Music Presence GitHub](https://github.com/ungive/discord-music-presence)
- [Music Presence Website](https://musicpresence.app/)
- [Windows Media Session API Documentation](https://docs.microsoft.com/en-us/windows/win32/medfound/media-session-api)
- [macOS Now Playing API](https://developer.apple.com/documentation/mediaplayer/mpnowplayinginfocenter)
- [MPRIS Specification](https://specifications.freedesktop.org/mpris-spec/latest/)

