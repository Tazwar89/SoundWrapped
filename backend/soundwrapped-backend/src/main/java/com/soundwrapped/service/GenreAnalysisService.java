package com.soundwrapped.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing genres from user's tracks.
 * Extracts genres from SoundCloud track data and provides insights.
 */
@Service
public class GenreAnalysisService {

    /**
     * Extract genres from a track.
     * SoundCloud tracks can have:
     * - genre (string): Main genre
     * - tag_list (string): Comma-separated tags
     * - genre_family (string): Genre family/category
     * 
     * @param track Track data from SoundCloud API
     * @return Set of unique genres/tags found in the track
     */
    public Set<String> extractGenresFromTrack(Map<String, Object> track) {
        Set<String> genres = new HashSet<>();
        
        // Extract main genre
        Object genreObj = track.get("genre");
        if (genreObj instanceof String && !((String) genreObj).isBlank()) {
            genres.add(normalizeGenre((String) genreObj));
        }
        
        // Extract genre_family
        Object genreFamilyObj = track.get("genre_family");
        if (genreFamilyObj instanceof String && !((String) genreFamilyObj).isBlank()) {
            genres.add(normalizeGenre((String) genreFamilyObj));
        }
        
        // Extract tags from tag_list
        Object tagListObj = track.get("tag_list");
        if (tagListObj instanceof String && !((String) tagListObj).isBlank()) {
            String tagList = (String) tagListObj;
            // Split by comma and process each tag
            String[] tags = tagList.split(",");
            for (String tag : tags) {
                String normalized = normalizeGenre(tag.trim());
                if (!normalized.isBlank()) {
                    genres.add(normalized);
                }
            }
        }
        
        return genres;
    }

    /**
     * Normalize genre name (lowercase, trim, handle common variations)
     */
    private String normalizeGenre(String genre) {
        if (genre == null || genre.isBlank()) {
            return "";
        }
        
        // Convert to lowercase and trim
        String normalized = genre.toLowerCase().trim();
        
        // Remove common prefixes/suffixes
        normalized = normalized.replaceAll("^genre[\\s-]*", "");
        normalized = normalized.replaceAll("[\\s-]*music$", "");
        
        // Handle common variations
        Map<String, String> variations = Map.of(
            "hip-hop", "hip hop",
            "hiphop", "hip hop",
            "r&b", "rnb",
            "r and b", "rnb",
            "edm", "electronic dance music",
            "d&b", "drum and bass",
            "drum&bass", "drum and bass"
        );
        
        if (variations.containsKey(normalized)) {
            normalized = variations.get(normalized);
        }
        
        return normalized;
    }

    /**
     * Analyze genres from a list of tracks.
     * 
     * @param tracks List of tracks from SoundCloud API
     * @return Genre analysis results
     */
    public Map<String, Object> analyzeGenres(List<Map<String, Object>> tracks) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Track genre counts
        Map<String, Integer> genreCounts = new HashMap<>();
        Map<String, Long> genreListeningMs = new HashMap<>();
        Set<String> allGenres = new HashSet<>();
        
        for (Map<String, Object> track : tracks) {
            Set<String> trackGenres = extractGenresFromTrack(track);
            allGenres.addAll(trackGenres);
            
            // Get track duration
            long durationMs = ((Number) track.getOrDefault("duration", 0)).longValue();
            
            // Count genres
            for (String genre : trackGenres) {
                genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
                genreListeningMs.put(genre, genreListeningMs.getOrDefault(genre, 0L) + durationMs);
            }
        }
        
        // Calculate top genres by count
        List<Map<String, Object>> topGenresByCount = genreCounts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(10)
            .map(entry -> {
                Map<String, Object> genreData = new HashMap<>();
                genreData.put("genre", entry.getKey());
                genreData.put("trackCount", entry.getValue());
                genreData.put("listeningMs", genreListeningMs.getOrDefault(entry.getKey(), 0L));
                genreData.put("listeningHours", genreListeningMs.getOrDefault(entry.getKey(), 0L) / 1000.0 / 60.0 / 60.0);
                return genreData;
            })
            .collect(Collectors.toList());
        
        // Calculate top genres by listening time
        List<Map<String, Object>> topGenresByTime = genreListeningMs.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(10)
            .map(entry -> {
                Map<String, Object> genreData = new HashMap<>();
                genreData.put("genre", entry.getKey());
                genreData.put("listeningMs", entry.getValue());
                genreData.put("listeningHours", entry.getValue() / 1000.0 / 60.0 / 60.0);
                genreData.put("trackCount", genreCounts.getOrDefault(entry.getKey(), 0));
                return genreData;
            })
            .collect(Collectors.toList());
        
        // Genre discovery count (unique genres explored)
        int genreDiscoveryCount = allGenres.size();
        
        // Genre distribution (percentage of tracks per genre)
        Map<String, Double> genreDistribution = new HashMap<>();
        int totalTracks = tracks.size();
        if (totalTracks > 0) {
            for (Map.Entry<String, Integer> entry : genreCounts.entrySet()) {
                double percentage = (entry.getValue() * 100.0) / totalTracks;
                genreDistribution.put(entry.getKey(), percentage);
            }
        }
        
        analysis.put("totalGenresDiscovered", genreDiscoveryCount);
        analysis.put("topGenresByTrackCount", topGenresByCount);
        analysis.put("topGenresByListeningTime", topGenresByTime);
        analysis.put("genreDistribution", genreDistribution);
        analysis.put("allGenres", new ArrayList<>(allGenres));
        
        return analysis;
    }

    /**
     * Get top 5 genres for dashboard display
     */
    public List<String> getTop5Genres(List<Map<String, Object>> tracks) {
        Map<String, Object> analysis = analyzeGenres(tracks);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topGenres = (List<Map<String, Object>>) analysis.get("topGenresByListeningTime");
        
        if (topGenres == null || topGenres.isEmpty()) {
            return new ArrayList<>();
        }
        
        return topGenres.stream()
            .limit(5)
            .map(genre -> (String) genre.get("genre"))
            .collect(Collectors.toList());
    }
}

