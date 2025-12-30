# API Keys Setup Guide

This document explains which API keys are needed for SoundWrapped and how to obtain them.

## Required API Keys

### 1. SoundCloud API Keys (Already Configured)
- **CLIENT_ID**: Your SoundCloud app client ID
- **CLIENT_SECRET**: Your SoundCloud app client secret
- **Where to get**: https://developers.soundcloud.com/

### 2. Groq API Key (Already Configured)
- **GROQ_API_KEY**: Your Groq API key
- **Where to get**: https://console.groq.com/
- **Status**: Already in use for AI descriptions
- **Free Tier**: Yes, generous free tier available

### 3. SerpAPI Key (Optional - Already Configured if you have it)
- **SERPAPI_API_KEY**: Your SerpAPI key
- **Where to get**: https://serpapi.com/
- **Status**: Optional, used for enhanced research
- **Free Tier**: Limited free tier available

## New API Keys Needed for Phase 3 Features

### 4. TheAudioDB API Key (NEW - For Enhanced Artist Profiles)
- **THEAUDIODB_API_KEY**: Your TheAudioDB API key
- **Where to get**: https://www.theaudiodb.com/api_guide.php
- **Purpose**: Fetches enhanced artist information including:
  - High-quality album artwork
  - Artist discography
  - Professional biographies
  - Music videos
- **Free Tier**: Yes, free tier available**
- **How to get**:
  1. Visit https://www.theaudiodb.com/
  2. Click "API" in the navigation
  3. Read the API guide (no registration required for basic usage)
  4. For API key, you may need to contact them or check their documentation
  5. **Note**: TheAudioDB may not require an API key for basic usage - check their documentation

### 5. Last.fm API Key (NEW - For Similar Artists Discovery)
- **LASTFM_API_KEY**: Your Last.fm API key
- **Where to get**: https://www.last.fm/api/account/create
- **Purpose**: Fetches similar artists and recommendations
- **Free Tier**: Yes, free tier available**
- **How to get**:
  1. Visit https://www.last.fm/api/account/create
  2. Fill out the form:
     - **Application name**: SoundWrapped (or any name)
     - **Application description**: Music analytics platform
     - **Application website**: Your website URL (or http://localhost:8080 for development)
     - **Callback URL**: http://localhost:8080/callback (or your production URL)
  3. Accept the terms and conditions
  4. Click "Submit"
  5. You'll receive an **API Key** and **Shared Secret**
  6. **Note**: For our use case, we only need the **API Key** (not the Shared Secret)

## Adding API Keys to .env File

Once you have the API keys, add them to your `.env` file in the `backend/soundwrapped-backend/` directory:

```bash
# Existing keys (already configured)
SOUNDCLOUD_CLIENT_ID=your_client_id
SOUNDCLOUD_CLIENT_SECRET=your_client_secret
GROQ_API_KEY=your_groq_api_key
SERPAPI_API_KEY=your_serpapi_key  # Optional

# New keys for Phase 3
THEAUDIODB_API_KEY=your_theaudiodb_api_key  # May not be required - check TheAudioDB docs
LASTFM_API_KEY=your_lastfm_api_key
```

## Feature Availability Without API Keys

### TheAudioDB API Key
- **Without key**: Enhanced artist profiles will not be available
- **Features affected**: 
  - High-quality artwork for "Artist of the Day"
  - Artist discography display
  - Professional biographies
- **Fallback**: SoundCloud's default artist information will still be displayed

### Last.fm API Key
- **Without key**: Similar artists discovery will not be available
- **Features affected**:
  - "Similar Artists" section on Artist of the Day
  - Artist recommendations
- **Fallback**: Artist of the Day will still work, just without similar artists

### Both APIs
- **Status**: Both are optional enhancements
- **Core functionality**: All core features work without these keys
- **Recommendation**: Get both keys for the full experience, but the app works fine without them

## Quick Setup Checklist

- [ ] Get TheAudioDB API key (if required - check their docs)
- [ ] Get Last.fm API key from https://www.last.fm/api/account/create
- [ ] Add `THEAUDIODB_API_KEY=...` to `.env` file
- [ ] Add `LASTFM_API_KEY=...` to `.env` file
- [ ] Restart the backend server

## Testing API Keys

After adding the keys, you can test them:

1. **TheAudioDB**: Check the "Artist of the Day" section - you should see enhanced artwork and discography
2. **Last.fm**: Check the "Artist of the Day" section - you should see a "Similar Artists" section

## Troubleshooting

### TheAudioDB
- **Issue**: No enhanced artist info showing
- **Solution**: 
  - Verify the API key is correct
  - Check if TheAudioDB requires an API key (they may not for basic usage)
  - Check backend logs for API errors

### Last.fm
- **Issue**: No similar artists showing
- **Solution**:
  - Verify the API key is correct
  - Check backend logs for API errors
  - Ensure the artist name matches Last.fm's database

## Security Notes

- **Never commit** `.env` files to version control
- **Keep API keys secret** - don't share them publicly
- **Rotate keys** if they're accidentally exposed
- **Use environment variables** in production instead of `.env` files

