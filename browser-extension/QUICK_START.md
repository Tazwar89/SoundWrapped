# Quick Start Guide

## 🚀 Get Started in 5 Minutes

### 1. Start Backend
```bash
cd backend/soundwrapped-backend
./mvnw spring-boot:run
```
Wait for: `Started SoundWrappedApplication`

### 2. Start Frontend (Optional)
```bash
cd frontend
npm run dev
```

### 3. Load Extension
1. Open Chrome → `chrome://extensions/`
2. Enable "Developer mode" (top-right)
3. Click "Load unpacked"
4. Select: `/Users/tazwarsikder/Documents/GitHub/SoundWrapped/browser-extension`
5. ✅ Extension loaded!

### 4. Authenticate
1. Open `http://localhost:3000`
2. Click "Connect to SoundCloud"
3. Authorize SoundWrapped
4. ✅ Authenticated!

### 5. Test Tracking
1. Open `https://soundcloud.com`
2. Play any track
3. Check browser console (F12) → Look for `[SoundWrapped]` messages
4. Check backend logs → Should see POST requests
5. Check dashboard → "In-App Plays" should increment

## ✅ Success Indicators

- Extension popup shows: "✅ Connected & Authenticated"
- Browser console shows: `[SoundWrapped] Track started`
- Backend logs show: `POST /api/tracking/system-playback`
- Dashboard shows increasing play counts

## 📝 Need Help?

See `TESTING.md` for detailed troubleshooting.

