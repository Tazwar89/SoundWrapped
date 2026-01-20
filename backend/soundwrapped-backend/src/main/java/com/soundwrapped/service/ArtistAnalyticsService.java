package com.soundwrapped.service;

import com.soundwrapped.entity.UserActivity;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for providing artist-specific analytics.
 * Note: SoundCloud API has limited analytics, so we work with available data:
 * - Track-level data (plays, likes, reposts)
 * - Related tracks API
 * - Our own tracked activity data
 */
@Service
public class ArtistAnalyticsService {

    private final SoundWrappedService soundWrappedService;
    private final UserActivityRepository activityRepository;

    public ArtistAnalyticsService(
            SoundWrappedService soundWrappedService,
            UserActivityRepository activityRepository) {
        this.soundWrappedService = soundWrappedService;
        this.activityRepository = activityRepository;
    }

    /**
     * Get artist analytics for the authenticated user (if they're an artist).
     * 
     * @param soundcloudUserId The SoundCloud user ID
     * @return Artist analytics data
     */
    public Map<String, Object> getArtistAnalytics(String soundcloudUserId) {
        Map<String, Object> analytics = new HashMap<String, Object>();
        
        try {
            // Get user's uploaded tracks
            List<Map<String, Object>> uploadedTracks = soundWrappedService.getUserTracks();
            
            // Filter to only uploaded tracks (not liked tracks)
            // If getUserTracks returns liked tracks, user is not an artist
            boolean isArtist = uploadedTracks.stream()
                .anyMatch(track -> {
                    Object userObj = track.get("user");
                    if (userObj instanceof Map<?, ?>) {
                        Object userIdObj = ((Map<?, ?>) userObj).get("id");
                        return String.valueOf(userIdObj).equals(soundcloudUserId);
                    }
                    return false;
                });
            
            if (!isArtist || uploadedTracks.isEmpty()) {
                analytics.put("isArtist", false);
                analytics.put("message", "User has no uploaded tracks");
                return analytics;
            }
            
            analytics.put("isArtist", true);
            
            // Calculate total plays across all tracks
            long totalPlays = uploadedTracks.stream()
                .mapToLong(track -> ((Number) track.getOrDefault("playback_count", 0)).longValue())
                .sum();
            
            // Calculate total likes
            long totalLikes = uploadedTracks.stream()
                .mapToLong(track -> ((Number) track.getOrDefault("likes_count", 0)).longValue())
                .sum();
            
            // Calculate total reposts
            long totalReposts = uploadedTracks.stream()
                .mapToLong(track -> ((Number) track.getOrDefault("reposts_count", 0)).longValue())
                .sum();
            
            // Get top tracks by plays
            List<Map<String, Object>> topTracksByPlays = uploadedTracks.stream()
                .sorted((a, b) -> Long.compare(
                    ((Number) b.getOrDefault("playback_count", 0)).longValue(),
                    ((Number) a.getOrDefault("playback_count", 0)).longValue()))
                .limit(5)
                .map(track -> {
                    Map<String, Object> trackData = new HashMap<String, Object>();
                    trackData.put("id", track.get("id"));
                    trackData.put("title", track.get("title"));
                    trackData.put("playbackCount", track.get("playback_count"));
                    trackData.put("likesCount", track.get("likes_count"));
                    trackData.put("repostsCount", track.get("reposts_count"));
                    trackData.put("artworkUrl", track.get("artwork_url"));
                    return trackData;
                })
                .collect(Collectors.toList());
            
            // Get top tracks by engagement (likes + reposts)
            List<Map<String, Object>> topTracksByEngagement = uploadedTracks.stream()
                .sorted((a, b) -> {
                    long engagementA = ((Number) a.getOrDefault("likes_count", 0)).longValue() +
                                      ((Number) a.getOrDefault("reposts_count", 0)).longValue();
                    long engagementB = ((Number) b.getOrDefault("likes_count", 0)).longValue() +
                                      ((Number) b.getOrDefault("reposts_count", 0)).longValue();
                    return Long.compare(engagementB, engagementA);
                })
                .limit(5)
                .map(track -> {
                    Map<String, Object> trackData = new HashMap<String, Object>();
                    trackData.put("id", track.get("id"));
                    trackData.put("title", track.get("title"));
                    trackData.put("playbackCount", track.get("playback_count"));
                    trackData.put("likesCount", track.get("likes_count"));
                    trackData.put("repostsCount", track.get("reposts_count"));
                    trackData.put("engagement", 
                        ((Number) track.getOrDefault("likes_count", 0)).longValue() +
                        ((Number) track.getOrDefault("reposts_count", 0)).longValue());
                    trackData.put("artworkUrl", track.get("artwork_url"));
                    return trackData;
                })
                .collect(Collectors.toList());
            
            // Get tracked activity for this artist's tracks
            LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
            LocalDateTime yearEnd = LocalDateTime.now();
            
            Set<String> trackIds = uploadedTracks.stream()
                .map(track -> String.valueOf(track.getOrDefault("id", "")))
                .filter(id -> !id.isEmpty() && !id.equals("null"))
                .collect(Collectors.toSet());
            
            // Count plays from our tracked activity
            long trackedPlays = activityRepository.findAll().stream()
                .filter(activity -> 
                    activity.getActivityType() == UserActivity.ActivityType.PLAY &&
                    trackIds.contains(activity.getTrackId()) &&
                    activity.getCreatedAt().isAfter(yearStart) &&
                    activity.getCreatedAt().isBefore(yearEnd))
                .count();
            
            // Get top listeners (users who played tracks most)
            Map<String, Long> listenerPlayCounts = activityRepository.findAll().stream()
                .filter(activity -> 
                    activity.getActivityType() == UserActivity.ActivityType.PLAY &&
                    trackIds.contains(activity.getTrackId()) &&
                    activity.getCreatedAt().isAfter(yearStart) &&
                    activity.getCreatedAt().isBefore(yearEnd))
                .collect(Collectors.groupingBy(
                    UserActivity::getSoundcloudUserId,
                    Collectors.counting()
                ));
            
            List<Map<String, Object>> topListeners = listenerPlayCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> listener = new HashMap<String, Object>();
                    listener.put("userId", entry.getKey());
                    listener.put("playCount", entry.getValue());
                    return listener;
                })
                .collect(Collectors.toList());
            
            analytics.put("totalTracks", uploadedTracks.size());
            analytics.put("totalPlays", totalPlays);
            analytics.put("totalLikes", totalLikes);
            analytics.put("totalReposts", totalReposts);
            analytics.put("totalEngagement", totalLikes + totalReposts);
            analytics.put("trackedPlays", trackedPlays);
            analytics.put("topTracksByPlays", topTracksByPlays);
            analytics.put("topTracksByEngagement", topTracksByEngagement);
            analytics.put("topListeners", topListeners);
            
            // Note about limitations
            analytics.put("limitations", List.of(
                "Location data requires SoundCloud Pro subscription",
                "Detailed listener demographics not available via API",
                "Tracked plays only reflect SoundWrapped app activity"
            ));
            
        } catch (Exception e) {
            System.err.println("Error getting artist analytics: " + e.getMessage());
            e.printStackTrace();
            analytics.put("isArtist", false);
            analytics.put("error", e.getMessage());
        }
        
        return analytics;
    }

    /**
     * Get artist recommendations based on related tracks.
     * 
     * @param trackId Track ID to find similar artists for
     * @return List of recommended artists
     */
    public List<Map<String, Object>> getArtistRecommendations(String trackId) {
        try {
            // Get related tracks
            List<Map<String, Object>> relatedTracks = soundWrappedService.getRelatedTracks(trackId);
            
            // Extract unique artists from related tracks
            Map<String, Map<String, Object>> artists = new LinkedHashMap<String, Map<String, Object>>();
            
            for (Map<String, Object> track : relatedTracks) {
                Object userObj = track.get("user");
                if (userObj instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> user = (Map<String, Object>) userObj;
                    String userId = String.valueOf(user.getOrDefault("id", ""));
                    String username = (String) user.getOrDefault("username", "");
                    
                    if (!userId.isEmpty() && !userId.equals("null") && !artists.containsKey(userId)) {
                        Map<String, Object> artist = new HashMap<String, Object>();
                        artist.put("userId", userId);
                        artist.put("username", username);
                        artist.put("fullName", user.getOrDefault("full_name", username));
                        artist.put("avatarUrl", user.getOrDefault("avatar_url", ""));
                        artist.put("trackCount", user.getOrDefault("track_count", 0));
                        artist.put("followersCount", user.getOrDefault("followers_count", 0));
                        artists.put(userId, artist);
                    }
                }
            }
            
            return new ArrayList<Map<String, Object>>(artists.values());
            
        } catch (Exception e) {
            System.err.println("Error getting artist recommendations: " + e.getMessage());
            return new ArrayList<Map<String, Object>>();
        }
    }
}