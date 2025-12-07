package com.soundwrapped.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for finding Music Doppelgänger (taste twin) from followed users.
 * Compares music taste similarity based on shared tracks, artists, and genres.
 * 
 * Note: SoundCloud API limitations may restrict access to other users' likes.
 * This implementation works with available data (tracks, artists, genres).
 */
@Service
public class MusicDoppelgangerService {

    private final SoundWrappedService soundWrappedService;
    private final GenreAnalysisService genreAnalysisService;

    public MusicDoppelgangerService(
            SoundWrappedService soundWrappedService,
            GenreAnalysisService genreAnalysisService) {
        this.soundWrappedService = soundWrappedService;
        this.genreAnalysisService = genreAnalysisService;
    }

    /**
     * Find Music Doppelgänger from followed users.
     * Compares taste similarity based on:
     * - Shared tracks (if accessible)
     * - Shared artists
     * - Shared genres
     * 
     * @return Music Doppelgänger information or null if not enough data
     */
    public Map<String, Object> findMusicDoppelganger() {
        try {
            // Get current user's data
            List<Map<String, Object>> userTracks = soundWrappedService.getUserTracks();
            Set<String> userTrackIds = userTracks.stream()
                .map(track -> String.valueOf(track.getOrDefault("id", "")))
                .filter(id -> !id.isEmpty() && !id.equals("null"))
                .collect(Collectors.toSet());
            
            Set<String> userArtists = extractArtists(userTracks);
            Set<String> userGenres = extractGenres(userTracks);
            
            if (userTracks.isEmpty()) {
                return createNoDataResponse("Not enough tracks to compare taste");
            }
            
            // Get followed users
            List<Map<String, Object>> followings = soundWrappedService.getUserFollowings();
            
            if (followings.isEmpty()) {
                return createNoDataResponse("You're not following anyone yet");
            }
            
            // Compare with each followed user
            List<Map<String, Object>> similarityScores = new ArrayList<>();
            
            for (Map<String, Object> following : followings) {
                try {
                    String followingId = String.valueOf(following.getOrDefault("id", ""));
                    String followingUsername = (String) following.getOrDefault("username", "Unknown");
                    
                    if (followingId.isEmpty() || followingId.equals("null")) {
                        continue;
                    }
                    
                    // Try to get their tracks (may fail if private or API limitations)
                    List<Map<String, Object>> followingTracks = getUserTracks(followingId);
                    
                    if (followingTracks.isEmpty()) {
                        // Skip if we can't get their tracks
                        continue;
                    }
                    
                    Set<String> followingTrackIds = followingTracks.stream()
                        .map(track -> String.valueOf(track.getOrDefault("id", "")))
                        .filter(id -> !id.isEmpty() && !id.equals("null"))
                        .collect(Collectors.toSet());
                    
                    Set<String> followingArtists = extractArtists(followingTracks);
                    Set<String> followingGenres = extractGenres(followingTracks);
                    
                    // Calculate similarity
                    double similarity = calculateSimilarity(
                        userTrackIds, userArtists, userGenres,
                        followingTrackIds, followingArtists, followingGenres
                    );
                    
                    if (similarity > 0) {
                        Map<String, Object> score = new HashMap<>();
                        score.put("userId", followingId);
                        score.put("username", followingUsername);
                        score.put("fullName", following.getOrDefault("full_name", followingUsername));
                        score.put("avatarUrl", following.getOrDefault("avatar_url", ""));
                        score.put("similarity", similarity);
                        score.put("sharedTracks", countShared(userTrackIds, followingTrackIds));
                        score.put("sharedArtists", countShared(userArtists, followingArtists));
                        score.put("sharedGenres", countShared(userGenres, followingGenres));
                        similarityScores.add(score);
                    }
                    
                    // Add delay to avoid rate limiting
                    Thread.sleep(200);
                    
                } catch (Exception e) {
                    // Skip this user if we can't get their data
                    System.out.println("Error processing user " + following.get("username") + ": " + e.getMessage());
                    continue;
                }
            }
            
            if (similarityScores.isEmpty()) {
                return createNoDataResponse("Could not compare taste with followed users (may be due to privacy settings)");
            }
            
            // Find user with highest similarity
            Map<String, Object> doppelganger = similarityScores.stream()
                .max(Comparator.comparingDouble(score -> (Double) score.get("similarity")))
                .orElse(null);
            
            if (doppelganger == null) {
                return createNoDataResponse("No similar users found");
            }
            
            // Format response
            Map<String, Object> doppelgangerData = new HashMap<>();
            doppelgangerData.put("userId", doppelganger.get("userId"));
            doppelgangerData.put("username", doppelganger.get("username"));
            doppelgangerData.put("fullName", doppelganger.get("fullName"));
            doppelgangerData.put("avatarUrl", doppelganger.get("avatarUrl"));
            doppelgangerData.put("similarityPercentage", Math.round((Double) doppelganger.get("similarity") * 100));
            doppelgangerData.put("sharedTracks", doppelganger.get("sharedTracks"));
            doppelgangerData.put("sharedArtists", doppelganger.get("sharedArtists"));
            doppelgangerData.put("sharedGenres", doppelganger.get("sharedGenres"));
            
            Map<String, Object> result = new HashMap<>();
            result.put("found", true);
            result.put("doppelganger", doppelgangerData);
            result.put("totalCompared", similarityScores.size());
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error finding Music Doppelgänger: " + e.getMessage());
            e.printStackTrace();
            return createNoDataResponse("Error analyzing taste similarity: " + e.getMessage());
        }
    }

    /**
     * Get tracks for a specific user (may fail due to API limitations)
     */
    private List<Map<String, Object>> getUserTracks(String userId) {
        try {
            // Try to get their uploaded tracks first
            String url = "https://api.soundcloud.com/users/" + userId + "/tracks?linked_partitioning=true&limit=50";
            Map<String, Object> response = soundWrappedService.makeGetRequestWithRefresh(url);
            
            // Handle paginated response
            Object collection = response.get("collection");
            if (collection instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) collection;
                return tracks;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            // If that fails, try to get their favorites (may be private)
            try {
                String url = "https://api.soundcloud.com/users/" + userId + "/favorites?linked_partitioning=true&limit=50";
                Map<String, Object> response = soundWrappedService.makeGetRequestWithRefresh(url);
                
                Object collection = response.get("collection");
                if (collection instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tracks = (List<Map<String, Object>>) collection;
                    return tracks;
                }
                return new ArrayList<>();
            } catch (Exception e2) {
                // Both failed - user's tracks/favorites may be private
                return new ArrayList<>();
            }
        }
    }

    /**
     * Extract artist names from tracks
     */
    private Set<String> extractArtists(List<Map<String, Object>> tracks) {
        Set<String> artists = new HashSet<>();
        for (Map<String, Object> track : tracks) {
            Object userObj = track.get("user");
            if (userObj instanceof Map<?, ?>) {
                Object usernameObj = ((Map<?, ?>) userObj).get("username");
                if (usernameObj instanceof String) {
                    artists.add(((String) usernameObj).toLowerCase());
                }
            }
        }
        return artists;
    }

    /**
     * Extract genres from tracks
     */
    private Set<String> extractGenres(List<Map<String, Object>> tracks) {
        Set<String> allGenres = new HashSet<>();
        for (Map<String, Object> track : tracks) {
            Set<String> trackGenres = genreAnalysisService.extractGenresFromTrack(track);
            allGenres.addAll(trackGenres);
        }
        return allGenres;
    }

    /**
     * Calculate similarity score between two users (0.0 to 1.0)
     */
    private double calculateSimilarity(
            Set<String> userTrackIds, Set<String> userArtists, Set<String> userGenres,
            Set<String> otherTrackIds, Set<String> otherArtists, Set<String> otherGenres) {
        
        if (userTrackIds.isEmpty() && userArtists.isEmpty() && userGenres.isEmpty()) {
            return 0.0;
        }
        
        // Calculate Jaccard similarity for each dimension
        double trackSimilarity = calculateJaccardSimilarity(userTrackIds, otherTrackIds);
        double artistSimilarity = calculateJaccardSimilarity(userArtists, otherArtists);
        double genreSimilarity = calculateJaccardSimilarity(userGenres, otherGenres);
        
        // Weighted average (tracks are most important, then artists, then genres)
        double weights = 0.0;
        double weightedSum = 0.0;
        
        if (!userTrackIds.isEmpty()) {
            weightedSum += trackSimilarity * 0.5;
            weights += 0.5;
        }
        if (!userArtists.isEmpty()) {
            weightedSum += artistSimilarity * 0.3;
            weights += 0.3;
        }
        if (!userGenres.isEmpty()) {
            weightedSum += genreSimilarity * 0.2;
            weights += 0.2;
        }
        
        return weights > 0 ? weightedSum / weights : 0.0;
    }

    /**
     * Calculate Jaccard similarity coefficient (intersection over union)
     */
    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0;
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Count shared items between two sets
     */
    private int countShared(Set<String> set1, Set<String> set2) {
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        return intersection.size();
    }

    private Map<String, Object> createNoDataResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("found", false);
        result.put("message", message);
        return result;
    }
}

