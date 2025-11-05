# Testing SoundWrapped Browser Extension

## Pre-Testing Checklist

- [ ] Backend is running on `http://localhost:8080`
- [ ] Frontend is running on `http://localhost:3000` (optional)
- [ ] You're authenticated with SoundCloud in SoundWrapped
- [ ] Extension is loaded in Chrome/Edge

## Step-by-Step Test

### 1. Load Extension

1. Open Chrome and go to `chrome://extensions/`
2. Enable "Developer mode" (top-right toggle)
3. Click "Load unpacked"
4. Navigate to `/Users/tazwarsikder/Documents/GitHub/SoundWrapped/browser-extension`
5. Click "Select"
6. Extension should appear in list

**Expected**: Extension icon appears in browser toolbar

### 2. Check Extension Popup

1. Click the SoundWrapped extension icon in toolbar
2. Popup should open

**Expected**:
- Shows "🎵 SoundWrapped" title
- Status shows connection status
- "Open SoundWrapped" button visible

### 3. Verify Backend Connection

1. Check popup status
2. Should show "✅ Connected to SoundWrapped" or "✅ Connected & Authenticated"

**If not connected**:
- Verify backend is running: `curl http://localhost:8080/api/tracking/health`
- Should return: `{"status":"healthy","service":"System Playback Tracking","version":"1.0.0"}`

**If not authenticated**:
- Open SoundWrapped: `http://localhost:3000`
- Log in and connect SoundCloud account
- Refresh extension popup

### 4. Test SoundCloud Tracking

1. Open new tab: `https://soundcloud.com`
2. Navigate to any track
3. Play the track
4. Open browser console (F12 → Console tab)
5. Look for `[SoundWrapped]` messages

**Expected Console Output**:
```
[SoundWrapped] Content script loaded
[SoundWrapped] Auth status: true
[SoundWrapped] Track started: {trackId: "...", title: "...", artist: "..."}
[SoundWrapped] Playback event tracked: {success: true, ...}
```

### 5. Verify Backend Receives Data

1. Check backend console/logs
2. Look for incoming POST requests to `/api/tracking/system-playback`

**Expected Backend Output**:
```
POST /api/tracking/system-playback
Request body: {trackId: "...", artist: "...", title: "...", ...}
Response: {success: true, message: "Playback event tracked"}
```

### 6. Verify Data in Dashboard

1. Open SoundWrapped dashboard: `http://localhost:3000/dashboard`
2. Check "In-App Plays" stat
3. Play multiple tracks on SoundCloud
4. Refresh dashboard

**Expected**:
- "In-App Plays" count increases
- Recent activity shows tracked tracks

### 7. Test Like Tracking

1. On SoundCloud, like a track (click heart icon)
2. Check browser console for: `[SoundWrapped] Like event tracked`
3. Check backend logs for POST to `/api/tracking/system-like`

## Common Issues

### Issue: Extension Not Loading

**Solution**:
- Check for errors in `chrome://extensions/`
- Verify `manifest.json` is valid JSON
- Check that all required files exist

### Issue: Content Script Not Running

**Solution**:
- Verify SoundCloud URL matches manifest permissions
- Check browser console for errors
- Try reloading SoundCloud page
- Check extension popup for connection status

### Issue: Not Detecting Tracks

**Solution**:
- SoundCloud may have changed DOM structure
- Check console for errors about missing selectors
- Update selectors in `content.js` if needed
- Try different SoundCloud pages (track page vs. stream)

### Issue: Authentication Failed

**Solution**:
- Verify you're logged into SoundWrapped
- Check `http://localhost:8080/api/soundcloud/debug/tokens`
- Should show `hasAccessToken: true`
- Re-authenticate if needed

## Debug Commands

### Check Extension Status
```bash
# In browser console on SoundCloud page
console.log('[SoundWrapped] Extension status check');
```

### Test Backend Endpoint
```bash
curl -X POST http://localhost:8080/api/tracking/system-playback \
  -H "Content-Type: application/json" \
  -d '{"trackId":"test123","artist":"Test","title":"Test Track","durationMs":30000}'
```

### Check Health
```bash
curl http://localhost:8080/api/tracking/health
```

## Next Steps After Testing

Once extension is working:
1. ✅ Real-time SoundCloud tracking enabled
2. ✅ Data flows to backend automatically
3. ✅ Dashboard shows accurate listening stats
4. ✅ No need for in-app player!

## Reporting Issues

If you encounter issues:
1. Check browser console for errors
2. Check backend logs
3. Verify all prerequisites are met
4. Update selectors in `content.js` if SoundCloud changed

