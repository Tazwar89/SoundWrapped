# Debugging Guide

## Quick Debug Steps

### 1. Check Browser Console

Open SoundCloud page → Press F12 → Console tab

**Look for**:
- `[SoundWrapped] Content script loaded` - Extension is running
- `[SoundWrapped] Auth status: true` - Authentication working
- `[SoundWrapped] 🎵 Track started` - Track detection working
- `[SoundWrapped] 📤 Sending playback event` - API call being made
- `[SoundWrapped] ✅ Playback event tracked` - Success!

**If you see errors**:
- `User not authenticated` → Log into SoundWrapped first
- `Failed to track playback` → Check backend is running
- `Error detecting playback` → SoundCloud DOM may have changed

### 2. Check Backend Logs

Look for:
```
[SystemPlayback] Received playback event: {...}
[SystemPlayback] User ID: ...
[SystemPlayback] Track: ... by ...
[SystemPlayback] ✅ Successfully tracked play event
```

### 3. Diagnostic Check (10 seconds after page load)

The extension will automatically log diagnostics:
```
[SoundWrapped] 🔍 Diagnostic check:
  - Track info: {...}
  - Is playing: true/false
  - Authenticated: true/false
  - Current URL: ...
```

### 4. Test API Endpoint Directly

```bash
curl -X POST http://localhost:8080/api/tracking/system-playback \
  -H "Content-Type: application/json" \
  -d '{
    "trackId": "test-track-123",
    "artist": "Test Artist",
    "title": "Test Track",
    "durationMs": 30000
  }'
```

Should return: `{"success":true,"message":"Playback event tracked",...}`

### 5. Check Database

If using H2 console:
```bash
# Access H2 console: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb
# Check: SELECT * FROM user_activities;
```

### Common Issues

#### Issue: "User not authenticated"
**Solution**: 
1. Open `http://localhost:3000`
2. Log in and connect SoundCloud
3. Refresh SoundCloud page

#### Issue: No track detection
**Solution**:
1. Make sure you're on a track page (not stream/search)
2. URL should be: `soundcloud.com/artist/track-name`
3. Check diagnostic logs after 10 seconds

#### Issue: "Failed to track playback" (backend error)
**Solution**:
1. Check backend logs for errors
2. Verify backend is running: `curl http://localhost:8080/api/tracking/health`
3. Check CORS settings

#### Issue: Track detected but not tracking
**Solution**:
1. Make sure you play for at least 5 seconds (MIN_PLAY_DURATION)
2. Check browser console for duration messages
3. Verify track ID is not empty

### Manual Testing

1. **Open SoundCloud track page**
2. **Open browser console** (F12)
3. **Play track for 30+ seconds**
4. **Watch console for**:
   - `🎵 Track started`
   - `✅ Track played for X seconds`
   - `📤 Sending playback event`
   - `✅ Playback event tracked successfully`
5. **Check backend logs** for confirmation
6. **Refresh dashboard** to see updated stats

### Still Not Working?

1. **Reload extension**: `chrome://extensions/` → Click refresh icon
2. **Reload SoundCloud page**: Hard refresh (Cmd+Shift+R / Ctrl+Shift+R)
3. **Check extension permissions**: Make sure it has access to SoundCloud
4. **Try different track**: Some tracks might have different DOM structure

