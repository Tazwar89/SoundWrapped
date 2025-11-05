# Step-by-Step Testing Guide

## Current Status

✅ Extension loads  
✅ Authentication works  
✅ Track detection works  
⚠️ Playback tracking needs verification

## Testing Steps

### 1. Reload Extension
- Go to `chrome://extensions/`
- Find "SoundWrapped Tracker"
- Click **refresh icon** (circular arrow)

### 2. Open SoundCloud Track Page
- Go to `https://soundcloud.com`
- Navigate to any track (e.g., click a track from your stream)
- **Wait for page to fully load**

### 3. Check Console
- Press **F12** to open console
- Look for:
  ```
  [SoundWrapped] 🎵 Extension loaded successfully!
  [SoundWrapped] ✅ Authenticated with SoundWrapped
  ```

### 4. Play Track
- **Click play** on the track
- **Watch console** - you should see:
  ```
  [SoundWrapped] 🎵 Track started: {...}
  [SoundWrapped] ⏱️ Play timer started at: ...
  ```

### 5. Let It Play
- **Play for at least 30 seconds** (minimum is 5 seconds)
- Every 10 seconds you should see:
  ```
  [SoundWrapped] ⏱️ Playing... 10s elapsed
  [SoundWrapped] ⏱️ Playing... 20s elapsed
  [SoundWrapped] ⏱️ Playing... 30s elapsed
  ```

### 6. Stop/Pause Track
- **Click pause** or let track end
- You should see:
  ```
  [SoundWrapped] ⏸️ Track stopped. Duration: X seconds
  [SoundWrapped] ✅ Track played for X seconds (minimum: 5 s)
  [SoundWrapped] 📤 Sending playback event...
  [SoundWrapped] ✅ Playback event tracked successfully
  ```

### 7. Check Backend Logs
Look for:
```
[SystemPlayback] Received playback event: {...}
[SystemPlayback] Track: ... by ...
[SystemPlayback] ✅ Successfully tracked play event
```

### 8. Check Dashboard
- Go to `http://localhost:3000/dashboard`
- **Refresh page**
- Check "In-App Plays" - should increase

## Troubleshooting

### If you DON'T see "Track started"
- **Reload the SoundCloud page** (hard refresh: Cmd+Shift+R)
- Make sure you're on a **track page** (not stream/search)
- Check console for errors

### If you see "Track started" but not "Track stopped"
- **Pause the track** manually (click pause button)
- Or wait for track to end
- The extension detects when playback stops

### If you see "Track stopped" but not "Playback event tracked"
- Check backend logs for errors
- Verify backend is running: `curl http://localhost:8080/api/tracking/health`
- Check console for error messages

### If tracking works but dashboard doesn't update
- Dashboard needs to be refreshed to see new data
- Check backend database to verify data is stored
- API endpoint: `GET /api/soundcloud/dashboard/analytics`

## Expected Console Flow

```
✅ Extension loaded
✅ Authenticated
🎵 Track started
⏱️ Playing... 10s elapsed
⏱️ Playing... 20s elapsed
⏱️ Playing... 30s elapsed
⏸️ Track stopped. Duration: 30 seconds
✅ Track played for 30 seconds
📤 Sending playback event
✅ Playback event tracked successfully
```

## Quick Test Command

```bash
# Check if backend received events
curl http://localhost:8080/api/tracking/health
```

Should return: `{"status":"healthy",...}`

