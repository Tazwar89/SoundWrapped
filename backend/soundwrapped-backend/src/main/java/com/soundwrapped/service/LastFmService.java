package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.MessageDigest;
import java.util.*;

/**
 * Dedicated service for all Last.fm REST API calls.
 * <p>
 * Owns: signature generation, auth.getToken, auth.getSession,
 * user.getInfo, user.getRecentTracks.
 * </p>
 * <p>
 * This keeps controller and scrobbling-service free of raw HTTP
 * concerns and eliminates the duplicate generateSignature methods.
 * </p>
 */
@Service
public class LastFmService {

    private final RestTemplate restTemplate;

    @Value("${lastfm.api-key:}")
    private String apiKey;

    @Value("${lastfm.api-secret:}")
    private String apiSecret;

    private static final String API_BASE = "https://ws.audioscrobbler.com/2.0";

    public LastFmService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ────────────────────────────────────────
    //  Public accessors
    // ────────────────────────────────────────

    public String getApiKey() {
        return apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty()
            && apiSecret != null && !apiSecret.isEmpty();
    }

    // ────────────────────────────────────────
    //  Auth flow
    // ────────────────────────────────────────

    /**
     * Step 1: Obtain a request token from Last.fm (auth.getToken).
     *
     * @return the request token string, or null on failure
     */
    public String fetchRequestToken() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "auth.getToken");
        params.put("api_key", apiKey);
        params.put("format", "json");

        String sig = generateSignature(params);
        params.put("api_sig", sig);

        Map<String, Object> body = doGet(params);
        if (body == null) return null;

        // { "token": "..." } or { "lfm": { "token": "..." } }
        String token = extractString(body, "token");
        if (token == null) {
            Map<String, Object> lfm = safeMap(body.get("lfm"));
            if (lfm != null) token = extractString(lfm, "token");
        }
        return token;
    }

    /**
     * Step 4: Exchange the authorized request token for a session key.
     * Signature: md5(api_keyVALUEmethodauth.getSessiontokenTOKENsecret)
     *
     * @return session key, or null on failure
     */
    public String exchangeTokenForSession(String token) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "auth.getSession");
        params.put("api_key", apiKey);
        params.put("token", token);
        params.put("format", "json");

        String sig = generateSignature(params);
        params.put("api_sig", sig);

        Map<String, Object> body = doGet(params);
        if (body == null) return null;

        Map<String, Object> session = safeMap(body.get("session"));
        if (session == null) {
            Map<String, Object> lfm = safeMap(body.get("lfm"));
            if (lfm != null) session = safeMap(lfm.get("session"));
        }
        return session != null ? extractString(session, "key") : null;
    }

    /**
     * Fetch the Last.fm username associated with a session key (user.getInfo).
     */
    public String fetchUsername(String sessionKey) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "user.getInfo");
        params.put("api_key", apiKey);
        params.put("sk", sessionKey);
        params.put("format", "json");

        String sig = generateSignature(params);
        params.put("api_sig", sig);

        Map<String, Object> body = doGet(params);
        if (body == null) return null;

        Map<String, Object> user = safeMap(body.get("user"));
        if (user == null) {
            Map<String, Object> lfm = safeMap(body.get("lfm"));
            if (lfm != null) user = safeMap(lfm.get("user"));
        }
        return user != null ? extractString(user, "name") : null;
    }

    // ────────────────────────────────────────
    //  Core data endpoint
    // ────────────────────────────────────────

    /**
     * Fetch recent tracks for a user since a given UNIX timestamp.
     * Returns raw Last.fm track maps from the {@code recenttracks.track} array.
     * <p>
     * Uses the <strong>user.getRecentTracks</strong> method (read-only,
     * does NOT require a session key — only the api_key and username).
     * </p>
     *
     * @param username      Last.fm username
     * @param fromTimestamp UNIX epoch seconds (exclusive lower bound)
     * @param limit         max tracks per page (max 200)
     * @return list of track maps, never null
     */
    public List<Map<String, Object>> getRecentTracks(String username, long fromTimestamp, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "user.getRecentTracks");
        params.put("user", username);
        params.put("api_key", apiKey);
        params.put("from", String.valueOf(fromTimestamp));
        params.put("limit", String.valueOf(Math.min(limit, 200)));
        params.put("format", "json");
        // user.getRecentTracks is a public/read-only method — no api_sig needed

        Map<String, Object> body = doGet(params);
        if (body == null) return Collections.emptyList();

        Map<String, Object> wrapper = safeMap(body.get("recenttracks"));
        if (wrapper == null) return Collections.emptyList();

        Object trackObj = wrapper.get("track");
        if (trackObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tracks = (List<Map<String, Object>>) trackObj;
            return tracks;
        }
        // Last.fm returns a single object instead of a list when there is only 1 track
        if (trackObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> single = (Map<String, Object>) trackObj;
            return Collections.singletonList(single);
        }
        return Collections.emptyList();
    }

    // ────────────────────────────────────────
    //  Signature / helpers
    // ────────────────────────────────────────

    /**
     * Generate the {@code api_sig} value per Last.fm spec:
     * Sort params alphabetically, concat key+value pairs (skip "format"),
     * append secret, then MD5-hex the result.
     */
    public String generateSignature(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (!"format".equals(key)) {
                sb.append(key).append(params.get(key));
            }
        }
        sb.append(apiSecret);

        return md5Hex(sb.toString());
    }

    // ── private helpers ──

    private Map<String, Object> doGet(Map<String, String> params) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE);
            params.forEach(builder::queryParam);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                req,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
        } catch (Exception e) {
            System.err.println("[LastFmService] API call failed: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> safeMap(Object obj) {
        if (!(obj instanceof Map<?, ?>))
            return null;

        Map<String, Object> out = new HashMap<>();

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet())
            if (entry.getKey() instanceof String)
                out.put((String) entry.getKey(), entry.getValue());

        return out;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);

        return val instanceof String ? (String) val : null;
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);

                if (h.length() == 1)
                    hex.append('0');

                hex.append(h);
            }

            return hex.toString();
        }

        catch (Exception e) {
            throw new RuntimeException("MD5 hashing failed", e);
        }
    }
}