# Installing SoundWrapped Browser Extension

## Quick Start

1. **Open Chrome Extensions Page**
   - Navigate to `chrome://extensions/` (or `edge://extensions/` for Edge)
   - Or: Menu → More Tools → Extensions

2. **Enable Developer Mode**
   - Toggle the "Developer mode" switch in the top-right corner

3. **Load the Extension**
   - Click "Load unpacked"
   - Select the `browser-extension` folder from this repository
   - The extension should appear in your extensions list

4. **Verify Installation**
   - Click the extension icon in your browser toolbar
   - You should see the SoundWrapped popup
   - Check connection status

## Prerequisites

- **SoundWrapped Backend Running**: `http://localhost:8080`
- **SoundWrapped Web App Running**: `http://localhost:3000` (optional, for dashboard)
- **Authenticated with SoundCloud**: Log into SoundWrapped and connect your SoundCloud account

## Testing

1. **Start Backend**:
   ```bash
   cd backend/soundwrapped-backend
   ./mvnw spring-boot:run
   ```

2. **Load Extension** (follow steps above)

3. **Test Tracking**:
   - Open SoundCloud in a new tab: `https://soundcloud.com`
   - Play a track
   - Check browser console (F12) for `[SoundWrapped]` logs
   - Check backend logs for tracking events

4. **Verify Data**:
   - Open SoundWrapped dashboard: `http://localhost:3000/dashboard`
   - Check "In-App Plays" stat - should increment after playing tracks

## Troubleshooting

### Extension Not Tracking

**Check Authentication**:
- Open extension popup
- Should show "✅ Connected & Authenticated"
- If not, log into SoundWrapped first

**Check Backend**:
- Verify backend is running: `http://localhost:8080/api/tracking/health`
- Should return `{"status":"healthy"}`

**Check Console**:
- Open browser console (F12)
- Look for `[SoundWrapped]` messages
- Check for any errors

### Not Detecting Tracks

**SoundCloud DOM Changes**:
- SoundCloud may have updated their page structure
- Check `content.js` selectors - may need updating
- Look for console errors about missing elements

**Page Not Fully Loaded**:
- Wait a few seconds after page load
- Try refreshing the page
- Check that SoundCloud player is visible

### Icons Missing

**Generate Icons**:
1. Open `browser-extension/icons/generate-icons.html` in browser
2. Click "Generate Icons"
3. Download each icon (icon16.png, icon48.png, icon128.png)
4. Place in `browser-extension/icons/` folder
5. Reload extension in Chrome

## Development

### Making Changes

1. Edit files in `browser-extension/`
2. Go to `chrome://extensions/`
3. Click refresh icon on SoundWrapped extension
4. Reload SoundCloud page

### Debugging

- **Content Script**: Check browser console on SoundCloud page
- **Background Script**: Check `chrome://extensions/` → Service Worker → Inspect
- **Popup**: Right-click extension icon → Inspect popup

## Next Steps

Once working:
- ✅ Extension tracks SoundCloud playback
- ✅ Data sent to backend
- ✅ Dashboard shows real listening stats
- ✅ No need to use in-app player!

