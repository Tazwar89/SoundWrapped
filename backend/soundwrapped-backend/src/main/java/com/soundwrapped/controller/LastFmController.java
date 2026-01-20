package com.soundwrapped.controller;

import com.soundwrapped.entity.LastFmToken;
import com.soundwrapped.repository.LastFmTokenRepository;
import com.soundwrapped.service.LastFmScrobblingService;
import com.soundwrapped.service.SoundWrappedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.servlet.http.HttpServletRequest;

import java.security.MessageDigest;
import java.util.*;

/**
 * Controller for Last.fm OAuth authentication and scrobbling management.
 * Handles connecting user's Last.fm account to enable automatic tracking.
 */
@RestController
@RequestMapping("/api/lastfm")
// CORS is handled globally by CorsConfig for /api/** endpoints
public class LastFmController {

    private final RestTemplate restTemplate;
    private final LastFmTokenRepository lastFmTokenRepository;
    private final SoundWrappedService soundWrappedService;
    private final LastFmScrobblingService lastFmScrobblingService;

    @Value("${lastfm.api-key:}")
    private String lastFmApiKey;

    @Value("${lastfm.api-secret:}")
    private String lastFmApiSecret;

    private static final String LASTFM_API_BASE_URL = "https://ws.audioscrobbler.com/2.0";
    private static final String LASTFM_AUTH_URL = "https://www.last.fm/api/auth/"; // Note: trailing slash per Last.fm docs

    public LastFmController(
            RestTemplate restTemplate,
            LastFmTokenRepository lastFmTokenRepository,
            SoundWrappedService soundWrappedService,
            LastFmScrobblingService lastFmScrobblingService) {
        this.restTemplate = restTemplate;
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
    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getAuthUrl() {
        Map<String, Object> response = new HashMap<>();
        
        if (lastFmApiKey == null || lastFmApiKey.isEmpty() || lastFmApiSecret == null || lastFmApiSecret.isEmpty()) {
            response.put("error", "Last.fm API keys not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(response);
        }

        try {
            // Step 1: Get a request token from Last.fm
            Map<String, String> params = new HashMap<>();
            params.put("method", "auth.getToken");
            params.put("api_key", lastFmApiKey);
            params.put("format", "json");

            // Generate signature for auth.getToken
            String signature = generateSignature(params);
            params.put("api_sig", signature);

            // Build URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(LASTFM_API_BASE_URL);
            params.forEach(builder::queryParam);

            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(requestHeaders);

            ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                String errorDetails = "HTTP " + tokenResponse.getStatusCode();
                if (tokenResponse.getBody() != null) {
                    errorDetails += " - " + tokenResponse.getBody().toString();
                }
                System.err.println("[LastFmController] Failed to get request token from Last.fm: " + errorDetails);
                response.put("error", "Failed to get request token from Last.fm. Please check your API keys and ensure the callback URL (http://localhost:8080/api/lastfm/callback) is configured in your Last.fm app settings.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(response);
            }

            // Extract token from response
            // Last.fm API returns: { "token": "..." } or { "lfm": { "token": "..." } }
            Map<String, Object> body = tokenResponse.getBody();
            String requestToken = null;
            
            // Try nested format first: { "lfm": { "token": "..." } }
            @SuppressWarnings("unchecked")
            Map<String, Object> lfmObj = (Map<String, Object>) body.get("lfm");
            if (lfmObj != null) {
                requestToken = (String) lfmObj.get("token");
            }
            
            // If not found, try direct format: { "token": "..." }
            if (requestToken == null) {
                requestToken = (String) body.get("token");
            }
            
            if (requestToken == null || requestToken.isEmpty()) {
                System.err.println("[LastFmController] Failed to extract token from Last.fm response. Response body: " + body);
                // Check if there's an error in the response
                Object errorObj = body.get("error");
                String errorMessage = "Failed to get request token from Last.fm";
                if (errorObj != null) {
                    errorMessage += ". Last.fm error: " + errorObj.toString();
                } else {
                    errorMessage += ". Unexpected response format: " + body;
                }
                response.put("error", errorMessage + " Please check your API keys and ensure the callback URL (http://localhost:8080/api/lastfm/callback) is configured in your Last.fm app settings.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(response);
            }

            // Step 2: Build authorization URL with request token and callback URL
            // Last.fm requires the 'cb' parameter to know where to redirect after authorization
            // Format per Last.fm docs: http://www.last.fm/api/auth/?api_key=xxx&cb=http://example.com
            // IMPORTANT: The callback URL in the 'cb' parameter MUST match exactly what's configured in Last.fm app settings
            // NOTE: Last.fm doesn't redirect to localhost URLs. For development, use ngrok or similar tunnel service.
            // Set this to your ngrok URL if using a tunnel, otherwise use localhost for testing (may not work)
            String callbackUrl = System.getenv("LASTFM_CALLBACK_URL");
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                callbackUrl = "http://localhost:8080/api/lastfm/callback"; // Default to localhost
            }
            // Use UriComponentsBuilder to properly construct the URL
            // Note: UriComponentsBuilder will automatically URL-encode the callback URL
            UriComponentsBuilder authUrlBuilder = UriComponentsBuilder.fromHttpUrl(LASTFM_AUTH_URL)
                    .queryParam("api_key", lastFmApiKey)
                    .queryParam("token", requestToken)
                    .queryParam("cb", callbackUrl);
            String authUrl = authUrlBuilder.toUriString();
            System.out.println("[LastFmController] ========================================");
            System.out.println("[LastFmController] Generated auth URL: " + authUrl);
            System.out.println("[LastFmController] Callback URL configured: " + callbackUrl);
            System.out.println("[LastFmController] ⚠️ IMPORTANT: Ensure this callback URL matches EXACTLY in Last.fm app settings:");
            System.out.println("[LastFmController]    " + callbackUrl);
            System.out.println("[LastFmController] ⚠️ NOTE: Last.fm may not redirect to localhost URLs.");
            System.out.println("[LastFmController]    If redirect fails, consider using a tunnel service (ngrok, localtunnel)");
            System.out.println("[LastFmController]    or deploy to a publicly accessible URL for testing.");
            System.out.println("[LastFmController] ========================================");
            response.put("authUrl", authUrl);
            response.put("requestToken", requestToken); // Store for callback
            response.put("callbackUrl", callbackUrl); // Include callback URL in response for debugging
            response.put("message", "Visit this URL to authorize SoundWrapped with Last.fm");
            return ResponseEntity.ok()
                    .body(response);

        } catch (Exception e) {
            System.err.println("[LastFmController] Error getting auth URL: " + e.getMessage());
            e.printStackTrace();
            response.put("error", "Failed to get Last.fm authorization URL: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
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
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Callback endpoint is accessible");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("expectedCallbackUrl", "http://localhost:8080/api/lastfm/callback");
        response.put("instructions", "Ensure this exact URL is set in your Last.fm app settings at https://www.last.fm/api/account/create");
        System.out.println("[LastFmController] ✅ Test callback endpoint hit at " + java.time.LocalDateTime.now());
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(required = false) String token,
            HttpServletRequest request) {
        System.out.println("[LastFmController] ========================================");
        System.out.println("[LastFmController] 🔔 Callback endpoint hit!");
        System.out.println("[LastFmController] Request URL: " + request.getRequestURL() + "?" + request.getQueryString());
        System.out.println("[LastFmController] Request method: " + request.getMethod());
        System.out.println("[LastFmController] Token parameter: " + (token != null && !token.isEmpty() ? "present (length: " + token.length() + ")" : "null or empty"));
        System.out.println("[LastFmController] All query parameters: " + request.getQueryString());
        System.out.println("[LastFmController] Request received at: " + java.time.LocalDateTime.now());
        System.out.println("[LastFmController] ========================================");
        try {
            if (token == null || token.isEmpty()) {
                System.err.println("[LastFmController] Callback missing token parameter");
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=missing_token")
                        .build();
            }

            // Get session key from Last.fm using the token
            String sessionKey = getSessionKey(token);
            if (sessionKey == null) {
                System.err.println("[LastFmController] Failed to get session key from Last.fm for token: " + token);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=session_key_failed")
                        .build();
            }

            // Get Last.fm username from session
            String username = getUsername(sessionKey);
            if (username == null) {
                System.err.println("[LastFmController] Failed to get username from Last.fm session key");
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=username_failed")
                        .build();
            }

            // Get current SoundCloud user ID
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            if (profile == null || profile.isEmpty()) {
                System.err.println("[LastFmController] getUserProfile returned null or empty");
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=user_not_logged_in")
                        .build();
            }
            
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));
            if ("unknown".equals(soundcloudUserId) || soundcloudUserId == null) {
                System.err.println("[LastFmController] Could not extract user ID from profile: " + profile);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=invalid_user_id")
                        .build();
            }

            System.out.println("[LastFmController] Saving Last.fm token for SoundCloud user: " + soundcloudUserId);
            
            // Save or update Last.fm token
            Optional<LastFmToken> existingToken = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);
            LastFmToken lastFmToken;
            
            if (existingToken.isPresent()) {
                lastFmToken = existingToken.get();
                lastFmToken.setLastFmUsername(username);
                lastFmToken.setSessionKey(sessionKey);
                System.out.println("[LastFmController] Updating existing Last.fm token");
            } else {
                lastFmToken = new LastFmToken();
                lastFmToken.setSoundcloudUserId(soundcloudUserId);
                lastFmToken.setLastFmUsername(username);
                lastFmToken.setSessionKey(sessionKey);
                System.out.println("[LastFmController] Creating new Last.fm token");
            }

            lastFmTokenRepository.save(lastFmToken);
            System.out.println("[LastFmController] ✅ Last.fm token saved successfully. ID: " + lastFmToken.getId());

            // Trigger immediate sync (async, don't wait)
            try {
                lastFmScrobblingService.syncUserScrobbles(lastFmToken);
            } catch (Exception syncError) {
                System.err.println("[LastFmController] Warning: Failed to trigger initial sync: " + syncError.getMessage());
                // Don't fail the connection if sync fails
            }

            System.out.println("[LastFmController] ✅ Last.fm account connected successfully for user: " + username);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=true&username=" + java.net.URLEncoder.encode(username, "UTF-8"))
                    .build();

        } catch (Exception e) {
            System.err.println("[LastFmController] Error handling callback: " + e.getMessage());
            e.printStackTrace();
            try {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=" + java.net.URLEncoder.encode(e.getMessage(), "UTF-8"))
                        .build();
            } catch (Exception encodingError) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://localhost:3000/lastfm/callback?lastfm_connected=false&error=unknown")
                        .build();
            }
        }
    }

    /**
     * Get Last.fm session key using the token from authorization.
     */
    private String getSessionKey(String token) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("method", "auth.getSession");
            params.put("api_key", lastFmApiKey);
            params.put("token", token);
            params.put("format", "json");

            // Generate signature
            String signature = generateSignature(params);
            params.put("api_sig", signature);

            // Build URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(LASTFM_API_BASE_URL);
            params.forEach(builder::queryParam);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );

            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                // Last.fm API returns: { "session": { "key": "...", "name": "..." } } or { "lfm": { "session": { ... } } }
                @SuppressWarnings("unchecked")
                Map<String, Object> sessionObj = (Map<String, Object>) body.get("session");
                
                // Try nested format: { "lfm": { "session": { ... } } }
                if (sessionObj == null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> lfmObj = (Map<String, Object>) body.get("lfm");
                    if (lfmObj != null) {
                        sessionObj = (Map<String, Object>) lfmObj.get("session");
                    }
                }
                
                if (sessionObj != null) {
                    String sessionKey = (String) sessionObj.get("key");
                    if (sessionKey != null && !sessionKey.isEmpty()) {
                        return sessionKey;
                    }
                }
                
                System.err.println("[LastFmController] Failed to extract session key from Last.fm response. Response body: " + body);
            } else {
                System.err.println("[LastFmController] Last.fm API returned non-2xx status: " + response.getStatusCode() + ", body: " + body);
            }
        } catch (Exception e) {
            System.err.println("[LastFmController] Error getting session key: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get Last.fm username from session key.
     */
    private String getUsername(String sessionKey) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("method", "user.getInfo");
            params.put("api_key", lastFmApiKey);
            params.put("sk", sessionKey);
            params.put("format", "json");

            // Generate signature
            String signature = generateSignature(params);
            params.put("api_sig", signature);

            // Build URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(LASTFM_API_BASE_URL);
            params.forEach(builder::queryParam);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );

            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                // Last.fm API returns: { "user": { "name": "..." } } or { "lfm": { "user": { ... } } }
                @SuppressWarnings("unchecked")
                Map<String, Object> userObj = (Map<String, Object>) body.get("user");
                
                // Try nested format: { "lfm": { "user": { ... } } }
                if (userObj == null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> lfmObj = (Map<String, Object>) body.get("lfm");
                    if (lfmObj != null) {
                        userObj = (Map<String, Object>) lfmObj.get("user");
                    }
                }
                
                if (userObj != null) {
                    String username = (String) userObj.get("name");
                    if (username != null && !username.isEmpty()) {
                        return username;
                    }
                }
                
                System.err.println("[LastFmController] Failed to extract username from Last.fm response. Response body: " + body);
            } else {
                System.err.println("[LastFmController] Last.fm API returned non-2xx status: " + response.getStatusCode() + ", body: " + body);
            }
        } catch (Exception e) {
            System.err.println("[LastFmController] Error getting username: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Generate API signature for Last.fm requests.
     */
    private String generateSignature(Map<String, String> params) {
        // Sort parameters alphabetically
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        // Build signature string
        StringBuilder sig = new StringBuilder();
        for (String key : sortedKeys) {
            if (!key.equals("format")) { // format is not included in signature
                sig.append(key).append(params.get(key));
            }
        }
        sig.append(lastFmApiSecret);

        // MD5 hash
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sig.toString().getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating signature", e);
        }
    }

    /**
     * Check if user has Last.fm connected.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        Map<String, Object> response = new HashMap<>();

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
            } else {
                response.put("connected", false);
            }

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
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
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            lastFmTokenRepository.deleteBySoundcloudUserId(soundcloudUserId);

            response.put("success", true);
            response.put("message", "Last.fm account disconnected");
            return ResponseEntity.ok()
                    .body(response);
        } catch (Exception e) {
            System.err.println("[LastFmController] Error disconnecting: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to disconnect: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.OK) // Return 200 instead of 500 to avoid CORS issues
                    .body(response);
        }
    }

    /**
     * Manually trigger sync for current user.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            Optional<LastFmToken> token = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);
            
            if (token.isEmpty()) {
                response.put("success", false);
                response.put("error", "Last.fm account not connected");
                return ResponseEntity.ok()
                        .body(response);
            }

            lastFmScrobblingService.syncUserScrobbles(token.get());

            response.put("success", true);
            response.put("message", "Sync triggered successfully");
            return ResponseEntity.ok()
                    .body(response);
        } catch (Exception e) {
            System.err.println("[LastFmController] Error triggering sync: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to trigger sync: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.OK) // Return 200 instead of 500 to avoid CORS issues
                    .body(response);
        }
    }
}

