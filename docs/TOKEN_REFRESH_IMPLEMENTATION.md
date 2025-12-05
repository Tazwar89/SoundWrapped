# Token Refresh Implementation

## Problem
Users' SoundCloud logins were becoming inauthenticated after periods of inactivity. The browser extension showed "connected but not authenticated" and the Dashboard showed zero statistics. This happened because:

1. SoundCloud access tokens expire after a few hours
2. Token refresh only occurred reactively when a 401 error happened
3. During inactivity, no API calls were made, so tokens expired without being refreshed

## Solution
Implemented proactive token refresh to prevent expiration:

### 1. Token Expiration Tracking
- Added `expiresAt` and `createdAt` fields to `Token` entity
- Tokens now track when they expire
- Default expiration: 10 hours (refreshed proactively after 9 hours)
- If SoundCloud provides `expires_in`, uses that value minus 1 hour buffer

### 2. Scheduled Token Refresh
- Created `TokenRefreshScheduler` service
- Runs every hour to check if tokens need refresh
- Proactively refreshes tokens 1 hour before expiration
- Enabled via `@EnableScheduling` in main application class

### 3. Manual Refresh Endpoint
- Added `POST /api/soundcloud/refresh-token` endpoint
- Can be called by browser extension or frontend
- Returns success/failure status

### 4. Enhanced Browser Extension
- Extension now calls refresh endpoint every hour
- Also checks token status every 5 minutes
- If token needs refresh, automatically refreshes it
- Updates authentication status after refresh

### 5. Enhanced Token Status Endpoint
- `GET /api/soundcloud/debug/tokens` now includes:
  - `hasValidToken`: Whether token is still valid
  - `needsRefresh`: Whether token needs refresh soon
  - `expiresAt`: When token expires
  - `isExpired`: Whether token is expired
  - `isExpiringSoon`: Whether token expires within 1 hour

## Files Modified

### Backend
1. `Token.java` - Added expiration tracking fields
2. `TokenStore.java` - Added expiration-aware methods
3. `SoundWrappedService.java` - Extract and store `expires_in` from token responses
4. `TokenRefreshScheduler.java` - New scheduled service for proactive refresh
5. `SoundWrappedApplication.java` - Added `@EnableScheduling`
6. `SoundWrappedController.java` - Added refresh endpoint and enhanced debug endpoint

### Browser Extension
1. `background.js` - Added periodic token refresh calls

## How It Works

### Token Lifecycle
1. User authenticates → Token saved with expiration time
2. Scheduled task runs every hour → Checks if token needs refresh
3. If expiring soon → Proactively refreshes token
4. Browser extension also checks every hour → Calls refresh endpoint
5. Extension checks every 5 minutes → Refreshes if needed

### Multiple Layers of Protection
- **Backend scheduled task**: Refreshes tokens server-side every hour
- **Browser extension**: Also refreshes tokens client-side every hour
- **On-demand refresh**: Any API call that gets 401 will trigger refresh
- **Proactive checks**: Extension checks status every 5 minutes

## Configuration

The scheduled refresh can be disabled via application properties:
```yaml
soundwrapped:
  token-refresh:
    enabled: false  # Default: true
```

## Testing

1. **Test token expiration tracking**:
   ```bash
   curl http://localhost:8080/api/soundcloud/debug/tokens
   ```
   Should show `expiresAt`, `needsRefresh`, etc.

2. **Test manual refresh**:
   ```bash
   curl -X POST http://localhost:8080/api/soundcloud/refresh-token
   ```
   Should return `{"success": true}` if refresh token exists

3. **Test browser extension**:
   - Open browser extension popup
   - Should show "Connected & Authenticated"
   - Check console logs for refresh activity

## Benefits

1. **Users stay authenticated** even during inactivity
2. **No more zero statistics** on Dashboard
3. **Seamless experience** - tokens refresh automatically
4. **Multiple safeguards** - backend + extension both refresh
5. **Proactive approach** - refresh before expiration, not after

## Future Improvements

1. **Token expiration notifications**: Alert users if refresh fails
2. **Refresh retry logic**: Retry failed refreshes with exponential backoff
3. **Token health dashboard**: Show token status in admin panel
4. **Refresh analytics**: Track refresh success/failure rates

