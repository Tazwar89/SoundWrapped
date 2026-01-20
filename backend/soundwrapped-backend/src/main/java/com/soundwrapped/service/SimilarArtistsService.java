package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

/**
 * Service for fetching similar artists and recommendations using Last.fm API.
 * Provides "If you like X, you might like Y" functionality.
 */
@Service
public class SimilarArtistsService {
    
    private final RestTemplate restTemplate;
    
    @Value("${lastfm.api-key:}")
    private String lastFmApiKey;
    
    private static final String LASTFM_API_BASE_URL = "https://ws.audioscrobbler.com/2.0";
    
    public SimilarArtistsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Fetches similar artists for a given artist using Last.fm API.
     * Results are cached for 12 hours since similar artists don't change frequently.
     * 
     * @param artistName Artist name to find similar artists for
     * @param limit Maximum number of similar artists to return (default: 10)
     * @return List of similar artists with names and match scores, or empty list if not found
     */
    @Cacheable(value = "similarArtists", key = "#artistName.toLowerCase() + '|' + #limit", unless = "#result == null || #result.isEmpty()")
    public List<Map<String, Object>> getSimilarArtists(String artistName, int limit) {
        if (artistName == null || artistName.isEmpty() || lastFmApiKey == null || lastFmApiKey.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }
        
        try {
            // Last.fm API: artist.getSimilar method
            String url = LASTFM_API_BASE_URL + "/?method=artist.getsimilar" +
                        "&artist=" + java.net.URLEncoder.encode(artistName, "UTF-8") +
                        "&api_key=" + lastFmApiKey +
                        "&format=json" +
                        "&limit=" + limit;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<String>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );
            
            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                // Last.fm returns: { "similarartists": { "artist": [...] } }
                @SuppressWarnings("unchecked")
                Map<String, Object> similarArtistsObj = (Map<String, Object>) body.get("similarartists");
                
                if (similarArtistsObj != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> artists = (List<Map<String, Object>>) similarArtistsObj.get("artist");
                    
                    if (artists != null && !artists.isEmpty()) {
                        // Transform Last.fm response to our format
                        List<Map<String, Object>> similarArtists = new ArrayList<Map<String, Object>>();
                        for (Map<String, Object> artist : artists) {
                            Map<String, Object> similarArtist = new HashMap<String, Object>();
                            similarArtist.put("name", artist.get("name"));
                            
                            // Last.fm provides match score (0-1)
                            Object matchObj = artist.get("match");
                            if (matchObj instanceof String matchStr) {
                                try {
                                    double match = Double.parseDouble(matchStr);
                                    similarArtist.put("matchScore", match);
                                    similarArtist.put("matchPercentage", (int)(match * 100));
                                } catch (NumberFormatException e) {
                                    similarArtist.put("matchScore", 0.0);
                                    similarArtist.put("matchPercentage", 0);
                                }
                            } else if (matchObj instanceof Number) {
                                double match = ((Number) matchObj).doubleValue();
                                similarArtist.put("matchScore", match);
                                similarArtist.put("matchPercentage", (int)(match * 100));
                            } else {
                                similarArtist.put("matchScore", 0.0);
                                similarArtist.put("matchPercentage", 0);
                            }
                            
                            // Get artist image if available
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> images = (List<Map<String, Object>>) artist.get("image");
                            if (images != null && !images.isEmpty()) {
                                // Get large image (usually index 2 or 3)
                                for (int i = images.size() - 1; i >= 0; i--) {
                                    Map<String, Object> image = images.get(i);
                                    String imageUrl = (String) image.get("#text");
                                    if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("2a96cbd8b46e442fc41c2b86b821562f")) {
                                        // Skip placeholder images
                                        similarArtist.put("image", imageUrl);
                                        break;
                                    }
                                }
                            }
                            
                            similarArtists.add(similarArtist);
                        }
                        
                        return similarArtists;
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.out.println("Last.fm API error for " + artistName + ": " + e.getStatusCode() + " - " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error fetching similar artists for " + artistName + ": " + e.getMessage());
        }
        
        return new ArrayList<Map<String, Object>>();
    }
    
    /**
     * Gets top tracks for an artist (useful for recommendations).
     * 
     * @param artistName Artist name
     * @param limit Maximum number of tracks to return
     * @return List of top tracks
     */
    public List<Map<String, Object>> getArtistTopTracks(String artistName, int limit) {
        if (artistName == null || artistName.isEmpty() || lastFmApiKey == null || lastFmApiKey.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }
        
        try {
            String url = LASTFM_API_BASE_URL + "/?method=artist.gettoptracks" +
                        "&artist=" + java.net.URLEncoder.encode(artistName, "UTF-8") +
                        "&api_key=" + lastFmApiKey +
                        "&format=json" +
                        "&limit=" + limit;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<String>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );
            
            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> toptracksObj = (Map<String, Object>) body.get("toptracks");
                
                if (toptracksObj != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tracks = (List<Map<String, Object>>) toptracksObj.get("track");
                    
                    if (tracks != null && !tracks.isEmpty()) {
                        List<Map<String, Object>> topTracks = new ArrayList<Map<String, Object>>();
                        for (Map<String, Object> track : tracks) {
                            Map<String, Object> trackInfo = new HashMap<String, Object>();
                            trackInfo.put("name", track.get("name"));
                            trackInfo.put("playcount", track.get("playcount"));
                            
                            // Get track image
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> images = (List<Map<String, Object>>) track.get("image");
                            if (images != null && !images.isEmpty()) {
                                for (int i = images.size() - 1; i >= 0; i--) {
                                    Map<String, Object> image = images.get(i);
                                    String imageUrl = (String) image.get("#text");
                                    if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("2a96cbd8b46e442fc41c2b86b821562f")) {
                                        trackInfo.put("image", imageUrl);
                                        break;
                                    }
                                }
                            }
                            
                            topTracks.add(trackInfo);
                        }
                        
                        return topTracks;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching top tracks for " + artistName + ": " + e.getMessage());
        }
        
        return new ArrayList<Map<String, Object>>();
    }
}