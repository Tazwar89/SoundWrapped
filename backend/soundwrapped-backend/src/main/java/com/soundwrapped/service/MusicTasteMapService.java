package com.soundwrapped.service;

import com.soundwrapped.entity.UserLocation;
import com.soundwrapped.repository.UserLocationRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating music taste map data based on similar users and their real locations.
 * Uses IP-based geolocation data stored in the database.
 */
@Service
public class MusicTasteMapService {
    
    private final SoundWrappedService soundWrappedService;
    private final GenreAnalysisService genreAnalysisService;
    private final UserLocationRepository locationRepository;
    private final MusicDoppelgangerService musicDoppelgangerService;
    
    public MusicTasteMapService(
            SoundWrappedService soundWrappedService,
            GenreAnalysisService genreAnalysisService,
            UserLocationRepository locationRepository,
            MusicDoppelgangerService musicDoppelgangerService) {
        this.soundWrappedService = soundWrappedService;
        this.genreAnalysisService = genreAnalysisService;
        this.locationRepository = locationRepository;
        this.musicDoppelgangerService = musicDoppelgangerService;
    }
    
    /**
     * Generate music taste map data based on similar users and their real locations.
     * Uses IP-based geolocation data from the database.
     */
    public List<Map<String, Object>> getMusicTasteMap() {
        try {
            // Get current user's profile
            Map<String, Object> userProfile = soundWrappedService.getUserProfile();
            String currentUserId = String.valueOf(userProfile.getOrDefault("id", ""));
            
            if (currentUserId.isEmpty() || currentUserId.equals("null")) {
                return new ArrayList<>();
            }
            
            // Get current user's tracks and genres for similarity calculation
            List<Map<String, Object>> userTracks = soundWrappedService.getUserTracks();
            Set<String> userGenres = extractGenres(userTracks);
            
            // Get all users with location data, grouped by city
            List<Object[]> citiesWithCounts = locationRepository.getCitiesWithUserCounts();
            
            if (citiesWithCounts.isEmpty()) {
                return new ArrayList<>();
            }
            
            // For each city, calculate similarity with users in that city
            List<Map<String, Object>> locations = new ArrayList<>();
            
            for (Object[] cityData : citiesWithCounts) {
                String city = (String) cityData[0];
                String country = (String) cityData[1];
                Double latitude = ((Number) cityData[2]).doubleValue();
                Double longitude = ((Number) cityData[3]).doubleValue();
                Long userCount = ((Number) cityData[4]).longValue();
                
                // Skip if no users in this city
                if (userCount == null || userCount == 0) {
                    continue;
                }
                
                // Get user IDs in this city
                List<String> userIdsInCity = locationRepository.getUserIdsByCity(city, country);
                
                // Calculate average similarity with users in this city
                List<Double> similarities = new ArrayList<>();
                Map<String, Long> genreCounts = new HashMap<>();
                
                for (String userId : userIdsInCity) {
                    // Skip current user
                    if (userId.equals(currentUserId)) {
                        continue;
                    }
                    
                    try {
                        // Get this user's tracks
                        List<Map<String, Object>> userTracksInCity = getUserTracksById(userId);
                        if (userTracksInCity.isEmpty()) {
                            continue;
                        }
                        
                        // Calculate similarity
                        Set<String> userGenresInCity = extractGenres(userTracksInCity);
                        double similarity = calculateGenreSimilarity(userGenres, userGenresInCity);
                        
                        if (similarity > 0) {
                            similarities.add(similarity);
                            
                            // Collect genres for top genres calculation
                            for (String genre : userGenresInCity) {
                                genreCounts.put(genre, genreCounts.getOrDefault(genre, 0L) + 1);
                            }
                        }
                    } catch (Exception e) {
                        // Skip users we can't analyze
                        continue;
                    }
                }
                
                // Skip cities with no similar users
                if (similarities.isEmpty()) {
                    continue;
                }
                
                // Calculate average similarity
                double avgSimilarity = similarities.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
                
                // Get top genres
                List<String> topGenres = genreCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                
                // Create location entry
                Map<String, Object> location = new HashMap<>();
                location.put("city", city);
                location.put("country", country);
                location.put("similarity", avgSimilarity);
                location.put("userCount", (int) userCount.longValue());
                location.put("topGenres", topGenres.isEmpty() ? List.of("Electronic", "Indie", "Pop") : topGenres);
                location.put("coordinates", Map.of(
                    "lat", latitude,
                    "lng", longitude
                ));
                
                locations.add(location);
            }
            
            // Sort by similarity (highest first) and limit to top 10
            return locations.stream()
                .sorted((a, b) -> Double.compare(
                    ((Number) b.get("similarity")).doubleValue(),
                    ((Number) a.get("similarity")).doubleValue()
                ))
                .limit(10)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            System.out.println("Error generating music taste map: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private Set<String> extractGenres(List<Map<String, Object>> tracks) {
        Set<String> genres = new HashSet<>();
        for (Map<String, Object> track : tracks) {
            Object genreObj = track.get("genre");
            if (genreObj != null && !genreObj.toString().isEmpty()) {
                genres.add(genreObj.toString());
            }
        }
        return genres;
    }
    
    private double calculateGenreSimilarity(Set<String> genres1, Set<String> genres2) {
        if (genres1.isEmpty() || genres2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> intersection = new HashSet<>(genres1);
        intersection.retainAll(genres2);
        
        Set<String> union = new HashSet<>(genres1);
        union.addAll(genres2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    private List<Map<String, Object>> getUserTracksById(String userId) {
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
}

