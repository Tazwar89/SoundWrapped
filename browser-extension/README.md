# SoundWrapped Browser Extension

Browser extension that tracks SoundCloud listening activity and sends it to SoundWrapped backend for analytics.

## Features

- ✅ Automatically detects SoundCloud playback
- ✅ Tracks play duration
- ✅ Tracks likes
- ✅ Sends data to SoundWrapped backend in real-time
- ✅ Works with SoundCloud web player

## Installation

### Development Mode

1. Open Chrome/Edge and navigate to `chrome://extensions/`
2. Enable "Developer mode" (toggle in top right)
3. Click "Load unpacked"
4. Select the `browser-extension` folder
5. The extension will be installed

### Production Mode

1. Package the extension (coming soon)
2. Install from Chrome Web Store (coming soon)

## Configuration

The extension connects to:
- **Backend API**: `http://localhost:8080/api/tracking`
- **SoundWrapped Web**: `http://localhost:3000`

To change these, edit `content.js` and update the `API_BASE_URL` constant.

## How It Works

1. **Content Script** (`content.js`):
   - Injected into SoundCloud pages
   - Monitors DOM for playback state
   - Extracts track metadata
   - Detects play/like events

2. **Background Service** (`background.js`):
   - Keeps extension alive
   - Handles authentication checks
   - Manages extension lifecycle

3. **Popup** (`popup.html/js`):
   - Shows connection status
   - Quick link to SoundWrapped

## Requirements

- SoundWrapped backend must be running on `http://localhost:8080`
- User must be authenticated with SoundCloud in SoundWrapped
- SoundCloud must be open in browser

## Troubleshooting

**Extension not tracking?**
- Make sure SoundWrapped backend is running
- Check that you're authenticated (see popup)
- Verify SoundCloud is playing (check browser console for logs)

**Not detecting tracks?**
- SoundCloud may have changed their DOM structure
- Check browser console for errors
- Update selectors in `content.js` if needed

## Development

To modify the extension:
1. Edit files in `browser-extension/`
2. Go to `chrome://extensions/`
3. Click the refresh icon on the extension card
4. Reload SoundCloud page

## Privacy

- Extension only tracks when you're on SoundCloud
- Data is sent only to your SoundWrapped instance
- No data is sent to third parties
- All tracking is opt-in (you install the extension)

