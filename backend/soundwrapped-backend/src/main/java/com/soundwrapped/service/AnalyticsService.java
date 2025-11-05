package com.soundwrapped.service;

import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for aggregating analytics from both SoundCloud API data
 * and our own tracked activity data.
 */
@Service
public class AnalyticsService {

    private final UserActivityRepository activityRepository;

    public AnalyticsService(UserActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * Get dashboard analytics combining API data and tracked activity
     * 
     * @param soundcloudUserId The SoundCloud user ID
     * @param apiProfileData Profile data from SoundCloud API
     * @param apiTracksData Tracks data from SoundCloud API
     * @return Combined analytics data
     */
    public Map<String, Object> getDashboardAnalytics(
            String soundcloudUserId,
            Map<String, Object> apiProfileData,
            List<Map<String, Object>> apiTracksData) {
        
        Map<String, Object> analytics = new HashMap<>();
        
        // Current year date range for tracked activity
        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        LocalDateTime yearEnd = LocalDateTime.now();
        
        // Get tracked in-app activity
        long trackedPlays = activityRepository.countBySoundcloudUserIdAndActivityType(
            soundcloudUserId, 
            com.soundwrapped.entity.UserActivity.ActivityType.PLAY
        );
        
        Long trackedListeningMs = activityRepository.getTotalPlayDurationMs(
            soundcloudUserId, 
            yearStart, 
            yearEnd
        );
        double trackedListeningHours = (trackedListeningMs != null ? trackedListeningMs : 0L) / 1000.0 / 60.0 / 60.0;
        
        long trackedLikes = activityRepository.countBySoundcloudUserIdAndActivityType(
            soundcloudUserId, 
            com.soundwrapped.entity.UserActivity.ActivityType.LIKE
        );
        
        // API data (available from SoundCloud)
        int apiTrackCount = apiTracksData != null ? apiTracksData.size() : 0;
        int apiLikesCount = (Integer) apiProfileData.getOrDefault("public_favorites_count", 0);
        int apiFollowersCount = (Integer) apiProfileData.getOrDefault("followers_count", 0);
        int apiFollowingCount = (Integer) apiProfileData.getOrDefault("followings_count", 0);
        
        // Combine API metadata with tracked activity
        // Note: API tracks have playback_count, but that's global plays, not user-specific
        // For true listening stats, we rely on tracked in-app activity
        
        analytics.put("profile", Map.of(
            "soundcloudUserId", soundcloudUserId,
            "username", apiProfileData.getOrDefault("username", "Unknown"),
            "followers", apiFollowersCount,
            "following", apiFollowingCount
        ));
        
        // Available from API
        analytics.put("apiStats", Map.of(
            "totalTracksAvailable", apiTrackCount,
            "totalLikesOnProfile", apiLikesCount,
            "tracksFromAPI", apiTracksData != null ? apiTracksData.size() : 0
        ));
        
        // Tracked in-app activity (only tracks activity within our app)
        analytics.put("trackedStats", Map.of(
            "inAppPlays", trackedPlays,
            "inAppListeningHours", trackedListeningHours,
            "inAppLikes", trackedLikes,
            "note", "These stats only reflect activity within SoundWrapped app, not SoundCloud platform activity"
        ));
        
        // Combined view (shows what we can actually calculate)
        analytics.put("availableMetrics", Map.of(
            "totalTracks", apiTrackCount, // From API
            "profileLikes", apiLikesCount, // From API
            "inAppListeningHours", trackedListeningHours, // From tracked activity
            "inAppPlays", trackedPlays, // From tracked activity
            "limitations", List.of(
                "Listening history only tracks in-app activity",
                "Platform-wide listening stats are not available from SoundCloud API",
                "To build comprehensive analytics, use SoundWrapped player to listen to tracks"
            )
        ));
        
        return analytics;
    }
}
