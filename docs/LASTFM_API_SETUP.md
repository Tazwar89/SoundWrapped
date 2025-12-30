# Last.fm API Key Setup Guide

## Information for Last.fm API Application Form

When creating a Last.fm API application, use the following information:

### Application Name
```
SoundWrapped
```

### Application Description
```
Music analytics platform that provides personalized insights from SoundCloud, including similar artist recommendations and music taste analysis.
```

### Application Homepage
For **development/local**:
```
http://localhost:8080
```

For **production** (if you have a deployed URL):
```
https://your-domain.com
```

### Callback URL
For **development/local**:
```
http://localhost:8080/callback
```

For **production** (if you have a deployed URL):
```
https://your-domain.com/callback
```

**Note**: Last.fm uses OAuth for user authentication, but for our use case (similar artists API), we only need the API key, not the full OAuth flow. The callback URL is required by their form but won't be actively used for our read-only API calls.

## Steps to Get Your API Key

1. Go to: https://www.last.fm/api/account/create
2. Fill in the form with the information above
3. Accept the terms and conditions
4. Click "Submit"
5. You'll receive:
   - **API Key** (this is what you need - add to `.env` as `LASTFM_API_KEY`)
   - **Shared Secret** (not needed for our use case)

## Adding to .env File

Once you have the API key, add it to `backend/soundwrapped-backend/.env`:

```bash
LASTFM_API_KEY=your_actual_api_key_here
```

## Testing

After adding the key and restarting the backend:
1. Check "Artist of the Day" on the homepage
2. You should see a "Similar Artists" section below the artist information
3. Check backend logs for any API errors

## Troubleshooting

- **No similar artists showing**: Check backend logs for Last.fm API errors
- **API key invalid**: Verify the key is correct and hasn't expired
- **Rate limiting**: Last.fm has rate limits on free tier - if you hit them, wait a bit and try again

