# Troubleshooting: Extension Not Tracking

## Important: Check the RIGHT Console!

The extension runs on **SoundCloud pages**, not SoundWrapped pages.

### ✅ Correct Steps:

1. **Open SoundCloud in a NEW TAB**: `https://soundcloud.com`
2. **Navigate to a track page**: Click any track
3. **Open Console** (F12) on the **SoundCloud page**
4. **Look for** `[SoundWrapped]` messages

### ❌ Wrong Place:

- Don't check console on SoundWrapped page (`localhost:3000`)
- Extension doesn't run there!

## Quick Test

### Step 1: Verify Extension is Loaded

1. Go to `chrome://extensions/`
2. Find "SoundWrapped Tracker"
3. Make sure it's **Enabled**
4. Check for any errors (red text)

### Step 2: Check Visual Indicator

1. Open `https://soundcloud.com`
2. Navigate to any track page
3. **Look for orange badge** in top-right corner
4. Should say: "🎵 SoundWrapped Tracking"

**If you see the badge** → Extension is running!
**If you don't see it** → Extension not injecting

### Step 3: Check Console on SoundCloud Page

1. On SoundCloud track page, press **F12**
2. Go to **Console** tab
3. Look for: `[SoundWrapped] 🎵 Extension loaded successfully!`

**Expected output**:
```
[SoundWrapped] 🎵 Extension loaded successfully!
[SoundWrapped] Page URL: https://soundcloud.com/...
[SoundWrapped] Extension version: 1.0.0
[SoundWrapped] Auth status: true/false
```

### Step 4: Test Playback

1. **Play a track** on SoundCloud
2. **Watch console** (keep it open)
3. **Play for 30+ seconds**

**Expected console messages**:
```
[SoundWrapped] 🎵 Track started: {...}
[SoundWrapped] ✅ Track played for 30 seconds
[SoundWrapped] 📤 Sending playback event: {...}
[SoundWrapped] ✅ Playback event tracked successfully
```

## Common Issues

### Issue: No `[SoundWrapped]` messages in console

**Possible causes**:
1. Extension not loaded → Check `chrome://extensions/`
2. Extension disabled → Enable it
3. Wrong console → Make sure you're on SoundCloud page console
4. Content script not injecting → Reload extension

**Solution**:
1. Go to `chrome://extensions/`
2. Find SoundWrapped extension
3. Click **reload icon** (circular arrow)
4. Refresh SoundCloud page
5. Check console again

### Issue: "Auth status: false"

**Solution**:
1. Open `http://localhost:3000`
2. Make sure you're logged in
3. Connect SoundCloud account
4. Refresh SoundCloud page
5. Check console - should show `Auth status: true`

### Issue: No visual indicator badge

**Solution**:
1. Reload extension: `chrome://extensions/` → Reload
2. Hard refresh SoundCloud: Cmd+Shift+R (Mac) or Ctrl+Shift+R (Windows)
3. Check if badge appears after 2-3 seconds

### Issue: Track detected but not tracking

**Check console for**:
- `🎵 Track started` → Good, detection working
- `✅ Track played for X seconds` → Good, duration tracked
- `📤 Sending playback event` → Good, API call being made
- `❌ Failed to track playback` → Check backend

**If you see "Failed to track"**:
1. Check backend is running: `curl http://localhost:8080/api/tracking/health`
2. Check backend logs for errors
3. Verify CORS settings

## Diagnostic Checklist

- [ ] Extension is enabled in `chrome://extensions/`
- [ ] Visual indicator appears on SoundCloud page
- [ ] Console shows `[SoundWrapped] Extension loaded`
- [ ] Console shows `Auth status: true`
- [ ] Playing track shows `🎵 Track started`
- [ ] After 30s shows `✅ Track played for 30 seconds`
- [ ] Shows `📤 Sending playback event`
- [ ] Shows `✅ Playback event tracked successfully`
- [ ] Backend logs show `[SystemPlayback] Received playback event`

## Still Not Working?

1. **Check extension permissions**:
   - Should have access to `soundcloud.com`
   - Check `chrome://extensions/` → SoundWrapped → Details → Site access

2. **Try incognito mode**:
   - Extensions might be blocked
   - Test in normal window

3. **Check browser console for errors**:
   - Red errors might indicate issues
   - Share error messages for debugging

4. **Verify manifest.json**:
   - Make sure `content_scripts` matches SoundCloud URLs
   - Check `host_permissions` includes SoundCloud

## Get Help

If still not working, share:
1. Console output from **SoundCloud page** (not SoundWrapped)
2. Backend logs
3. Extension status from `chrome://extensions/`
4. Whether visual indicator appears

