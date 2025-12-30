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

import java.security.MessageDigest;
import java.util.*;

/**
 * Controller for Last.fm OAuth authentication and scrobbling management.
 * Handles connecting user's Last.fm account to enable automatic tracking.
 */
@RestController
@RequestMapping("/api/lastfm")
@CrossOrigin(origins = "*")
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
    private static final String LASTFM_AUTH_URL = "https://www.last.fm/api/auth";

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
     * Returns URL that user should visit to authorize the app.
     */
    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getAuthUrl() {
        Map<String, Object> response = new HashMap<>();
        
        if (lastFmApiKey == null || lastFmApiKey.isEmpty()) {
            response.put("error", "Last.fm API key not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        // Last.fm auth URL format: https://www.last.fm/api/auth?api_key={api_key}
        String authUrl = LASTFM_AUTH_URL + "?api_key=" + lastFmApiKey;
        
        response.put("authUrl", authUrl);
        response.put("message", "Visit this URL to authorize SoundWrapped with Last.fm");
        return ResponseEntity.ok(response);
    }

    /**
     * Complete Last.fm authentication after user authorizes.
     * This endpoint is called after user visits the auth URL and authorizes.
     * The token parameter is provided by Last.fm after authorization.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestParam(required = false) String token) {
        Map<String, Object> response = new HashMap<>();

        if (token == null || token.isEmpty()) {
            response.put("error", "Missing token parameter");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Get session key from Last.fm using the token
            String sessionKey = getSessionKey(token);
            if (sessionKey == null) {
                response.put("error", "Failed to get session key from Last.fm");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Get Last.fm username from session
            String username = getUsername(sessionKey);
            if (username == null) {
                response.put("error", "Failed to get username from Last.fm");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Get current SoundCloud user ID
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            // Save or update Last.fm token
            Optional<LastFmToken> existingToken = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);
            LastFmToken lastFmToken;
            
            if (existingToken.isPresent()) {
                lastFmToken = existingToken.get();
                lastFmToken.setLastFmUsername(username);
                lastFmToken.setSessionKey(sessionKey);
            } else {
                lastFmToken = new LastFmToken();
                lastFmToken.setSoundcloudUserId(soundcloudUserId);
                lastFmToken.setLastFmUsername(username);
                lastFmToken.setSessionKey(sessionKey);
            }

            lastFmTokenRepository.save(lastFmToken);

            // Trigger immediate sync
            lastFmScrobblingService.syncUserScrobbles(lastFmToken);

            response.put("success", true);
            response.put("message", "Last.fm account connected successfully");
            response.put("username", username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[LastFmController] Error handling callback: " + e.getMessage());
            e.printStackTrace();
            response.put("error", "Failed to connect Last.fm account: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
                @SuppressWarnings("unchecked")
                Map<String, Object> sessionObj = (Map<String, Object>) body.get("session");
                if (sessionObj != null) {
                    return (String) sessionObj.get("key");
                }
            }
        } catch (Exception e) {
            System.err.println("[LastFmController] Error getting session key: " + e.getMessage());
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
                @SuppressWarnings("unchecked")
                Map<String, Object> userObj = (Map<String, Object>) body.get("user");
                if (userObj != null) {
                    return (String) userObj.get("name");
                }
            }
        } catch (Exception e) {
            System.err.println("[LastFmController] Error getting username: " + e.getMessage());
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
            String soundcloudUserId = String.valueOf(profile.getOrDefault("id", "unknown"));

            Optional<LastFmToken> token = lastFmTokenRepository.findBySoundcloudUserId(soundcloudUserId);
            
            if (token.isPresent()) {
                response.put("connected", true);
                response.put("username", token.get().getLastFmUsername());
                response.put("lastSyncAt", token.get().getLastSyncAt());
            } else {
                response.put("connected", false);
            }

            return ResponseEntity.ok()
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "*")
                    .body(response);
        } catch (Exception e) {
            System.err.println("[LastFmController] Error checking connection status: " + e.getMessage());
            e.printStackTrace();
            response.put("connected", false);
            response.put("error", "Failed to check connection status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.OK) // Return 200 instead of 500 to avoid CORS issues
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "*")
                    .body(response);
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
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "*")
                    .body(response);
        } catch (Exception e) {
            System.err.println("[LastFmController] Error disconnecting: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to disconnect: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.OK) // Return 200 instead of 500 to avoid CORS issues
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "*")
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
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                        .header("Access-Control-Allow-Headers", "*")
                        .body(response);
            }

            lastFmScrobblingService.syncUserScrobbles(token.get());

            response.put("success", true);
            response.put("message", "Sync triggered successfully");
            return ResponseEntity.ok()
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "*")
                    .body(response);
        } catch (Exception e) {
            System.err.println("[LastFmController] Error triggering sync: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to trigger sync: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.OK) // Return 200 instead of 500 to avoid CORS issues
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .header("Access-Control-Allow-Headers", "*")
                    .body(response);
        }
    }
}

