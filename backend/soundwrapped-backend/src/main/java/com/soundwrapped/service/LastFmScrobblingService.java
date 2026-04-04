package com.soundwrapped.service;

import com.soundwrapped.entity.LastFmToken;
import com.soundwrapped.entity.UserActivity;
import com.soundwrapped.repository.LastFmTokenRepository;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls Last.fm for linked users' recent scrobbles and persists them as
 * {@link UserActivity} records with {@code source = LASTFM}.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Poll {@code user.getRecentTracks} every ~15 min</li>
 *   <li>For each scrobble, try to map artist+title → SoundCloud track ID</li>
 *   <li>Store the activity (even if SC match fails — keeps Last.fm metadata)</li>
 *   <li>Run analytics on the combined activity table</li>
 * </ol>
 *
 * All raw Last.fm HTTP calls are delegated to {@link LastFmService}.
 */
@Service
public class LastFmScrobblingService {
    private final LastFmService lastFmService;
    private final LastFmTokenRepository lastFmTokenRepository;
    private final UserActivityRepository userActivityRepository;
    private final SoundWrappedService soundWrappedService;
    private final RestTemplate restTemplate;

    /**
     * In-memory cache: "artist|title" → SoundCloud track ID (or empty string for miss).
     * Prevents re-searching for the same track on every poll cycle.
     */
    private final Map<String, String> scTrackCache = new ConcurrentHashMap<>();

    /** Minimum millis between consecutive Last.fm API calls (≈ 5 req/sec max). */
    private static final long RATE_LIMIT_MS = 220;

    public LastFmScrobblingService(
            LastFmService lastFmService,
            LastFmTokenRepository lastFmTokenRepository,
            UserActivityRepository userActivityRepository,
            SoundWrappedService soundWrappedService,
            RestTemplate restTemplate) {
        this.lastFmService = lastFmService;
        this.lastFmTokenRepository = lastFmTokenRepository;
        this.userActivityRepository = userActivityRepository;
        this.soundWrappedService = soundWrappedService;
        this.restTemplate = restTemplate;
    }

    // ────────────────────────────────────────
    //  Scheduled sync
    // ────────────────────────────────────────

    /**
     * Sync recent scrobbles for every connected Last.fm account.
     * Runs every 15 minutes.  Requests are staggered per-user so we
     * stay well within Last.fm's ~5 req/sec rate limit.
     */
    @Scheduled(fixedRate = 900_000) // 15 minutes
    @Transactional
    public void syncAllUsersScrobbles() {
        if (!lastFmService.isConfigured()) return;

        List<LastFmToken> tokens = lastFmTokenRepository.findAll();
        System.out.println("[LastFmScrobbling] Syncing scrobbles for " + tokens.size() + " user(s)");

        for (LastFmToken token : tokens) {
            try {
                syncUserScrobbles(token);
                Thread.sleep(RATE_LIMIT_MS); // stagger between users
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("[LastFmScrobbling] Error syncing user "
                    + token.getSoundcloudUserId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Sync scrobbles for a single user.  Called from scheduler and also
     * from the controller on initial connect / manual sync.
     */
    @Transactional
    public void syncUserScrobbles(LastFmToken token) {
        long fromEpoch = token.getLastSyncAt()
            .atZone(ZoneId.systemDefault())
            .toEpochSecond();

        List<Map<String, Object>> tracks =
            lastFmService.getRecentTracks(token.getLastFmUsername(), fromEpoch, 200);

        if (tracks.isEmpty()) {
            System.out.println("[LastFmScrobbling] No new scrobbles for user " + token.getSoundcloudUserId());
            return;
        }

        System.out.println("[LastFmScrobbling] Found " + tracks.size()
            + " scrobble(s) for user " + token.getSoundcloudUserId());

        int synced = 0;
        for (Map<String, Object> raw : tracks) {
            try {
                // Skip "now playing" entries (no date / @attr.nowplaying)
                if (isNowPlaying(raw)) continue;

                String artist = extractArtistName(raw);
                String title  = extractString(raw, "name");
                Long   epoch  = extractScrobbleTimestamp(raw);

                if (artist == null || title == null || epoch == null) continue;

                LocalDateTime playedAt = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(epoch), ZoneId.systemDefault());

                // Try to find SoundCloud track. Use artist|title as DB trackId for unmatched.
                String scTrackId = findSoundCloudTrackId(artist, title);
                String trackIdForDb = scTrackId != null ? scTrackId : (artist + "|" + title);

                // Duplicate guard: exact user + track + type + timestamp
                boolean exists = userActivityRepository
                    .existsBySoundcloudUserIdAndTrackIdAndActivityTypeAndCreatedAt(
                        token.getSoundcloudUserId(),
                        trackIdForDb,
                        UserActivity.ActivityType.PLAY,
                        playedAt);

                if (exists) continue;

                UserActivity activity = new UserActivity();
                activity.setSoundcloudUserId(token.getSoundcloudUserId());
                activity.setTrackId(trackIdForDb);
                activity.setActivityType(UserActivity.ActivityType.PLAY);
                activity.setSource(UserActivity.ActivitySource.LASTFM);
                activity.setPlayDurationMs(180_000L); // Last.fm doesn't expose duration
                activity.setCreatedAt(playedAt);
                activity.setLastFmArtist(artist);
                activity.setLastFmTrack(title);
                activity.setMatchedSoundCloudTrackId(scTrackId); // null if unmatched

                userActivityRepository.save(activity);
                synced++;
            } catch (Exception e) {
                System.err.println("[LastFmScrobbling] Error processing track: " + e.getMessage());
            }
        }

        token.setLastSyncAt(LocalDateTime.now());
        lastFmTokenRepository.save(token);

        System.out.println("[LastFmScrobbling] ✅ Synced " + synced
            + " new track(s) for user " + token.getSoundcloudUserId());
    }

    // ────────────────────────────────────────
    //  SoundCloud track matching
    // ────────────────────────────────────────

    /**
     * Search SoundCloud for a track matching the given artist + title.
     * Results are cached in-memory so repeated polls don't re-search.
     * Uses fuzzy name normalization for comparison.
     *
     * @return SoundCloud track ID as a String, or null if no confident match
     */
    private String findSoundCloudTrackId(String artist, String title) {
        String cacheKey = normalize(artist) + "|" + normalize(title);
        if (scTrackCache.containsKey(cacheKey)) {
            String cached = scTrackCache.get(cacheKey);
            return cached.isEmpty() ? null : cached;
        }

        try {
            String clientId = soundWrappedService.getClientId();
            if (clientId == null || clientId.isEmpty()) return null;

            String query = artist + " " + title;
            String url = "https://api.soundcloud.com/tracks?q="
                + java.net.URLEncoder.encode(query, "UTF-8")
                + "&client_id=" + clientId
                + "&limit=5";

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> respBody = resp.getBody();

            if (resp.getStatusCode().is2xxSuccessful() && respBody != null) {
                for (Map<String, Object> track : respBody) {
                    String scTitle  = (String) track.get("title");
                    Object userObj  = track.get("user");
                    String scArtist = null;

                    if (userObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userMap = (Map<String, Object>) userObj;
                        scArtist = (String) userMap.get("username");
                    }

                    if (fuzzyMatch(artist, scArtist) && fuzzyMatch(title, scTitle)) {
                        String id = String.valueOf(track.get("id"));
                        scTrackCache.put(cacheKey, id);

                        return id;
                    }
                }
            }
        }

        catch (Exception e) {
            // Track may simply not exist on SoundCloud — that's fine
        }

        scTrackCache.put(cacheKey, ""); // negative cache

        return null;
    }

    // ────────────────────────────────────────
    //  Parsing / normalization helpers
    // ────────────────────────────────────────

    /**
     * Extract the artist name from a Last.fm track map.
     * Last.fm may return {@code "artist": {"#text": "name"}} or
     * {@code "artist": "name"}.
     */
    private String extractArtistName(Map<String, Object> track) {
        Object artistObj = track.get("artist");

        if (artistObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) artistObj;
            Object text = map.get("#text");

            return text instanceof String ? (String) text : null;
        }

        return artistObj instanceof String ? (String) artistObj : null;
    }

    /**
     * Extract the UNIX timestamp from a scrobble's {@code date} field.
     * Last.fm puts the epoch in the {@code uts} key:
     * {@code "date": {"uts": "1700000000", "#text": "14 Nov 2023, 22:13"}}
     */
    private Long extractScrobbleTimestamp(Map<String, Object> track) {
        Object dateObj = track.get("date");

        if (dateObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dateMap = (Map<String, Object>) dateObj;
            Object uts = dateMap.get("uts");

            if (uts instanceof String) {
                try {
                    return Long.parseLong((String) uts);
                }

                catch (NumberFormatException ignored) {
                    // If parsing fails, we'll return null below and skip this track
                }
            }

            else if (uts instanceof Number)
                return ((Number) uts).longValue();
        }

        return null;
    }

    /** Check whether the track is a "now playing" entry (no completed timestamp). */
    private boolean isNowPlaying(Map<String, Object> track) {
        Object attr = track.get("@attr");

        if (attr instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> attrMap = (Map<String, Object>) attr;

            return "true".equals(String.valueOf(attrMap.get("nowplaying")));
        }

        return track.get("date") == null;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object v = map.get(key);

        return v instanceof String ? (String) v : null;
    }

    /**
     * Normalize a string for fuzzy comparison: lowercase, strip non-alphanumeric,
     * collapse whitespace.
     */
    static String normalize(String s) {
        if (s == null)
            return "";

        else
            return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Fuzzy comparison: both sides are normalized then checked for containment
     * (covers cases like "Artist - Track (Remix)" matching "Track").
     */
    static boolean fuzzyMatch(String a, String b) {
        if (a == null || b == null)
            return false;

        String na = normalize(a);
        String nb = normalize(b);

        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }
}