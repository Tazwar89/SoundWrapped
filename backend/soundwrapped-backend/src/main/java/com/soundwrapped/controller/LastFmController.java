package com.soundwrapped.controller;

import com.soundwrapped.entity.LastFmToken;
import com.soundwrapped.repository.LastFmTokenRepository;
import com.soundwrapped.service.LastFmScrobblingService;
import com.soundwrapped.service.LastFmService;
import com.soundwrapped.service.SoundWrappedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Controller for Last.fm OAuth authentication and scrobbling management.
 * Handles connecting user's Last.fm account to enable automatic tracking.
 *
 * All raw Last.fm HTTP calls are delegated to {@link LastFmService}.
 */
@RestController
@RequestMapping("/api/lastfm")
public class LastFmController {
    private final LastFmService lastFmService;
    private final LastFmTokenRepository lastFmTokenRepository;
    private final SoundWrappedService soundWrappedService;
    private final LastFmScrobblingService lastFmScrobblingService;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final String LASTFM_AUTH_URL = "https://www.last.fm/api/auth/";

    public LastFmController(
            LastFmService lastFmService,
            LastFmTokenRepository lastFmTokenRepository,
            SoundWrappedService soundWrappedService,
            LastFmScrobblingService lastFmScrobblingService) {
        this.lastFmService = lastFmService;
        this.lastFmTokenRepository = lastFmTokenRepository;
        this.soundWrappedService = soundWrappedService;
        this.lastFmScrobblingService = lastFmScrobblingService;
    }

    /**
     * Get Last.fm authentication URL for user to authorize SoundWrapped.
     * This implements the proper Last.fm OAuth flow:
     * 1. Get a request token using auth.getToken
     * 2. Return authorization URL with the request token
     * 3. User authorizes, then callback exchanges token for session key
     */
    /**
     * Get Last.fm authentication URL for user to authorize SoundWrapped.
     * Uses Last.fm Web Auth flow (not Desktop Auth):
     *   1. Build auth URL with api_key + cb (callback URL) only
     *   2. User authorizes on Last.fm
     *   3. Last.fm redirects to cb?token=xxx
     *   4. Backend /callback exchanges token for session key
     *
     * IMPORTANT: Do NOT include a "token" query param in the auth URL.
     * Including "token" triggers Desktop Auth mode, which shows an
     * "application authenticated" page but never redirects back.
     */
    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getAuthUrl() {
        Map<String, Object> response = new HashMap<>();

        if (!lastFmService.isConfigured()) {
            response.put("error", "Last.fm API keys not configured");

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try {
            // Build callback URL
            String callbackUrl = System.getenv("LASTFM_CALLBACK_URL");

            if (callbackUrl == null || callbackUrl.isEmpty())
                callbackUrl = "http://localhost:8080/api/lastfm/callback";

            // Web Auth: only api_key + cb — Last.fm generates and passes its
            // own token to the callback URL after the user authorizes.
            String authUrl = UriComponentsBuilder.fromUriString(LASTFM_AUTH_URL)
                    .queryParam("api_key", lastFmService.getApiKey())
                    .queryParam("cb", callbackUrl)
                    .build().encode().toUriString();

            System.out.println("[LastFmController] Generated auth URL (Web Auth): " + authUrl);
            System.out.println("[LastFmController] Callback URL configured: " + callbackUrl);

            response.put("authUrl", authUrl);
            response.put("callbackUrl", callbackUrl);
            response.put("message", "Visit this URL to authorize SoundWrapped with Last.fm");

            return ResponseEntity.ok().body(response);

        }

        catch (Exception e) {
            System.err.println("[LastFmController] Error getting auth URL: " + e.getMessage());
            e.printStackTrace();
            response.put("error", "Failed to get Last.fm authorization URL: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Complete Last.fm authentication after user authorizes.
     * This endpoint is called after user visits the auth URL and authorizes.
     * The token parameter is provided by Last.fm after authorization.
     * Redirects to frontend with success/error status.
     */
    /**
     * Test endpoint to verify callback URL is accessible.
     * This helps debug if Last.fm can reach the callback endpoint.
     */
    @GetMapping("/callback/test")
    public ResponseEntity<Map<String, Object>> testCallback() {
        Map<String, Object> response = new HashMap<String, Object>();
        String callbackUrl = System.getenv("LASTFM_CALLBACK_URL");

        if (callbackUrl == null || callbackUrl.isEmpty())
            callbackUrl = "http://localhost:8080/api/lastfm/callback";

        response.put("status", "success");
        response.put("message", "Callback endpoint is accessible");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("expectedCallbackUrl", callbackUrl);
        response.put("instructions", "Ensure this exact URL is set in your Last.fm app settings at https://www.last.fm/api/account/create");
        System.out.println("[LastFmController] ✅ Test callback endpoint hit at " + java.time.LocalDateTime.now());

        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(required = false) String token) {
        System.out.println("[LastFmController] Callback hit — token " + (token != null ? "present" : "missing"));
        try {
            if (token == null || token.isEmpty())
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=missing_token")
                        .build();

            // Exchange token for session key via LastFmService
            String sessionKey = lastFmService.exchangeTokenForSession(token);

            if (sessionKey == null)
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=session_key_failed")
                        .build();

            // Get Last.fm username via LastFmService
            String username = lastFmService.fetchUsername(sessionKey);

            if (username == null)
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=username_failed")
                        .build();

            // Get current SoundCloud user ID
            Map<String, Object> profile = soundWrappedService.getUserProfile();

            if (profile == null || profile.isEmpty())
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=user_not_logged_in")
                        .build();

            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            if ("unknown".equals(soundcloudUserId))
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=invalid_user_id")
                        .build();

            // Save or update Last.fm token
            Optional<LastFmToken> existingToken = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);
            LastFmToken lastFmToken;

            if (existingToken.isPresent()) {
                lastFmToken = existingToken.get();
                lastFmToken.setLastFmUsername(username);
                lastFmToken.setSessionKey(sessionKey);
            }

            else {
                lastFmToken = new LastFmToken();
                lastFmToken.setSoundcloudUserId(soundcloudUserId);
                lastFmToken.setLastFmUsername(username);
                lastFmToken.setSessionKey(sessionKey);
            }

            lastFmTokenRepository.save(lastFmToken);

            // Trigger immediate sync
            try {
                lastFmScrobblingService.syncUserScrobbles(lastFmToken);
            }

            catch (Exception syncError) {
                System.err.println("[LastFmController] Initial sync failed: " + syncError.getMessage());
            }

            System.out.println("[LastFmController] ✅ Last.fm connected for user: " + username);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=true&username=" + java.net.URLEncoder.encode(username, "UTF-8"))
                    .build();

        }

        catch (Exception e) {
            System.err.println("[LastFmController] Error handling callback: " + e.getMessage());
            e.printStackTrace();

            try {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=" + java.net.URLEncoder.encode(e.getMessage(), "UTF-8"))
                        .build();
            }

            catch (Exception encodingError) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", frontendBaseUrl + "/lastfm/callback?lastfm_connected=false&error=unknown")
                        .build();
            }
        }
    }

    /**
     * Check if user has Last.fm connected.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        Map<String, Object> response = new HashMap<String, Object>();

        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();

            if (profile == null || profile.isEmpty()) {
                System.err.println("[LastFmController] getUserProfile returned null or empty");
                response.put("connected", false);
                response.put("error", "Unable to get user profile. Please ensure you are logged in.");

                return ResponseEntity.ok().body(response);
            }

            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            if ("unknown".equals(soundcloudUserId) || soundcloudUserId == null) {
                System.err.println("[LastFmController] Could not extract user ID from profile: " + profile);
                response.put("connected", false);
                response.put("error", "Unable to identify user. Please ensure you are logged in.");

                return ResponseEntity.ok().body(response);
            }

            Optional<LastFmToken> token = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);

            if (token.isPresent()) {
                response.put("connected", true);
                response.put("username", token.get().getLastFmUsername());
                response.put("lastSyncAt", token.get().getLastSyncAt());
            }

            else
                response.put("connected", false);

            return ResponseEntity.ok().body(response);
        }

        catch (Exception e) {
            System.err.println("[LastFmController] Error checking connection status: " + e.getMessage());
            e.printStackTrace();
            response.put("connected", false);
            response.put("error", "Failed to check connection status: " + e.getMessage());

            // Always return 200 (CORS is handled globally)
            return ResponseEntity.ok().body(response);
        }
    }

    /**
     * Disconnect Last.fm account.
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        Map<String, Object> response = new HashMap<String, Object>();

        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            lastFmTokenRepository.deleteBySoundcloudUserId(soundcloudUserId);

            response.put("success", true);
            response.put("message", "Last.fm account disconnected");

            return ResponseEntity.ok().body(response);
        }

        catch (Exception e) {
            System.err.println("[LastFmController] Error disconnecting: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to disconnect: " + e.getMessage());

            // Return 200 instead of 500 to avoid CORS issues
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    /**
     * Manually trigger sync for current user.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        Map<String, Object> response = new HashMap<String, Object>();

        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            Optional<LastFmToken> token = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);
            
            if (token.isEmpty()) {
                response.put("success", false);
                response.put("error", "Last.fm account not connected");

                return ResponseEntity.ok().body(response);
            }

            lastFmScrobblingService.syncUserScrobbles(token.get());

            response.put("success", true);
            response.put("message", "Sync triggered successfully");

            return ResponseEntity.ok().body(response);
        }

        catch (Exception e) {
            System.err.println("[LastFmController] Error triggering sync: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to trigger sync: " + e.getMessage());

            // Return 200 instead of 500 to avoid CORS issues
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }
}