package com.soundwrapped.service;

import com.soundwrapped.entity.LastFmToken;
import com.soundwrapped.entity.UserActivity;
import com.soundwrapped.repository.LastFmTokenRepository;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Service for syncing Last.fm scrobbles to SoundWrapped user activity.
 * Polls Last.fm API for recent tracks and creates UserActivity records.
 * 
 * Last.fm scrobbling works by:
 * 1. Users connect SoundCloud to Last.fm (via Last.fm website or Web Scrobbler extension)
 * 2. Last.fm tracks their SoundCloud plays automatically
 * 3. We poll Last.fm API to get their recent tracks
 * 4. We sync those tracks to our UserActivity database
 */
@Service
public class LastFmScrobblingService {

    private final RestTemplate restTemplate;
    private final LastFmTokenRepository lastFmTokenRepository;
    private final UserActivityRepository userActivityRepository;
    private final SoundWrappedService soundWrappedService;

    @Value("${lastfm.api-key:}")
    private String lastFmApiKey;

    @Value("${lastfm.api-secret:}")
    private String lastFmApiSecret;

    private static final String LASTFM_API_BASE_URL = "https://ws.audioscrobbler.com/2.0";

    public LastFmScrobblingService(
            RestTemplate restTemplate,
            LastFmTokenRepository lastFmTokenRepository,
            UserActivityRepository userActivityRepository,
            SoundWrappedService soundWrappedService) {
        this.restTemplate = restTemplate;
        this.lastFmTokenRepository = lastFmTokenRepository;
        this.userActivityRepository = userActivityRepository;
        this.soundWrappedService = soundWrappedService;
    }

    /**
     * Sync scrobbles from Last.fm for all connected users.
     * Runs every 15 minutes to keep data up-to-date.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void syncAllUsersScrobbles() {
        if (lastFmApiKey == null || lastFmApiKey.isEmpty()) {
            return; // Last.fm API key not configured
        }

        List<LastFmToken> allTokens = lastFmTokenRepository.findAll();
        System.out.println("[LastFmScrobbling] Syncing scrobbles for " + allTokens.size() + " users");

        for (LastFmToken token : allTokens) {
            try {
                syncUserScrobbles(token);
            } catch (Exception e) {
                System.err.println("[LastFmScrobbling] Error syncing for user " + token.getSoundcloudUserId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Sync scrobbles for a specific user from Last.fm.
     * Fetches tracks scrobbled since lastSyncAt and creates UserActivity records.
     */
    @Transactional
    public void syncUserScrobbles(LastFmToken token) {
        try {
            // Get recent tracks from Last.fm since last sync
            long fromTimestamp = token.getLastSyncAt().atZone(ZoneId.systemDefault()).toEpochSecond();
            List<Map<String, Object>> recentTracks = getRecentTracks(token.getLastFmUsername(), token.getSessionKey(), fromTimestamp);

            if (recentTracks.isEmpty()) {
                System.out.println("[LastFmScrobbling] No new scrobbles for user " + token.getSoundcloudUserId());
                return;
            }

            System.out.println("[LastFmScrobbling] Found " + recentTracks.size() + " new scrobbles for user " + token.getSoundcloudUserId());

            // For each scrobbled track, try to find matching SoundCloud track and create activity
            int syncedCount = 0;
            for (Map<String, Object> track : recentTracks) {
                try {
                    // Last.fm track format: artist can be a Map with "#text" key or a String
                    Object artistObj = track.get("artist");
                    String artist = null;
                    if (artistObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> artistMap = (Map<String, Object>) artistObj;
                        artist = (String) artistMap.get("#text");
                    } else if (artistObj instanceof String) {
                        artist = (String) artistObj;
                    }
                    
                    String title = (String) track.get("name");
                    Long timestamp = parseTimestamp(track.get("date"));

                    if (artist == null || title == null) {
                        continue;
                    }

                    // Try to find SoundCloud track by artist and title
                    String soundcloudTrackId = findSoundCloudTrackId(artist, title);
                    if (soundcloudTrackId != null) {
                        // Create UserActivity record
                        UserActivity activity = new UserActivity();
                        activity.setSoundcloudUserId(token.getSoundcloudUserId());
                        activity.setTrackId(soundcloudTrackId);
                        activity.setActivityType(UserActivity.ActivityType.PLAY);
                        
                        // Estimate duration (Last.fm doesn't provide exact duration, use average 3 minutes)
                        activity.setPlayDurationMs(180000L); // 3 minutes default
                        
                        // Set createdAt from Last.fm timestamp
                        if (timestamp != null) {
                            activity.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()));
                        } else {
                            activity.setCreatedAt(LocalDateTime.now());
                        }

                        // Check if this activity already exists (avoid duplicates)
                        boolean exists = userActivityRepository.existsBySoundcloudUserIdAndTrackIdAndActivityTypeAndCreatedAt(
                            token.getSoundcloudUserId(),
                            soundcloudTrackId,
                            UserActivity.ActivityType.PLAY,
                            activity.getCreatedAt()
                        );

                        if (!exists) {
                            userActivityRepository.save(activity);
                            syncedCount++;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[LastFmScrobbling] Error processing track: " + e.getMessage());
                }
            }

            // Update lastSyncAt to now
            token.setLastSyncAt(LocalDateTime.now());
            lastFmTokenRepository.save(token);

            System.out.println("[LastFmScrobbling] ✅ Synced " + syncedCount + " tracks for user " + token.getSoundcloudUserId());

        } catch (Exception e) {
            System.err.println("[LastFmScrobbling] Error syncing scrobbles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get recent tracks from Last.fm API.
     * Uses user.getRecentTracks method with authentication.
     */
    private List<Map<String, Object>> getRecentTracks(String username, String sessionKey, long fromTimestamp) {
        try {
            // Build request with authentication
            Map<String, String> params = new HashMap<String, String>();
            params.put("method", "user.getRecentTracks");
            params.put("user", username);
            params.put("api_key", lastFmApiKey);
            params.put("format", "json");
            params.put("limit", "200"); // Get up to 200 recent tracks
            params.put("from", String.valueOf(fromTimestamp));

            // Sign the request (Last.fm requires signed requests for authenticated methods)
            String signature = generateSignature(params, sessionKey);
            params.put("api_sig", signature);
            params.put("sk", sessionKey); // Session key

            // Build URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(LASTFM_API_BASE_URL);
            params.forEach(builder::queryParam);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<String>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );

            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> recentTracksObj = (Map<String, Object>) body.get("recenttracks");
                
                if (recentTracksObj != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tracks = (List<Map<String, Object>>) recentTracksObj.get("track");
                    return tracks != null ? tracks : new ArrayList<Map<String, Object>>();
                }
            }
        } catch (Exception e) {
            System.err.println("[LastFmScrobbling] Error fetching recent tracks: " + e.getMessage());
        }

        return new ArrayList<Map<String, Object>>();
    }

    /**
     * Generate API signature for Last.fm authenticated requests.
     */
    private String generateSignature(Map<String, String> params, String sessionKey) {
        // Sort parameters alphabetically
        List<String> sortedKeys = new ArrayList<String>(params.keySet());
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
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
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
     * Find SoundCloud track ID by searching for artist and title.
     * Uses SoundCloud API to search for matching tracks.
     */
    private String findSoundCloudTrackId(String artist, String title) {
        try {
            // Get client ID from SoundWrappedService
            String clientId = soundWrappedService.getClientId();
            if (clientId == null || clientId.isEmpty()) {
                return null;
            }

            // Search SoundCloud for the track
            String query = artist + " " + title;
            String searchUrl = "https://api.soundcloud.com/tracks?q=" + 
                              java.net.URLEncoder.encode(query, "UTF-8") + 
                              "&client_id=" + clientId + 
                              "&limit=5";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<String>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>(){}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> tracks = response.getBody();
                if (!tracks.isEmpty()) {
                    // Return first match (could be improved with better matching logic)
                    Map<String, Object> track = tracks.get(0);
                    Object id = track.get("id");
                    return id != null ? String.valueOf(id) : null;
                }
            }
        } catch (Exception e) {
            // Silently fail - track might not be on SoundCloud
        }

        return null;
    }

    /**
     * Parse timestamp from Last.fm track data.
     */
    private Long parseTimestamp(Object dateObj) {
        if (dateObj == null) {
            return null;
        }

        if (dateObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dateMap = (Map<String, Object>) dateObj;
            Object timestamp = dateMap.get("#text");
            if (timestamp instanceof String) {
                try {
                    return Long.parseLong((String) timestamp);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (timestamp instanceof Number) {
                return ((Number) timestamp).longValue();
            }
        } else if (dateObj instanceof String) {
            try {
                return Long.parseLong((String) dateObj);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (dateObj instanceof Number) {
            return ((Number) dateObj).longValue();
        }

        return null;
    }
}