package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

/**
 * Service for fetching enhanced artist information using TheAudioDB API.
 * Provides album artwork, discography, music videos, and rich biographies.
 */
@Service
public class EnhancedArtistService {
    
    private final RestTemplate restTemplate;
    
    @Value("${theaudiodb.api-key:}")
    private String theAudioDbApiKey;
    
    private static final String THEAUDIODB_API_BASE_URL = "https://www.theaudiodb.com/api/v1/json";
    
    public EnhancedArtistService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Fetches enhanced artist information from TheAudioDB.
     * Results are cached for 24 hours since artist info doesn't change frequently.
     * 
     * @param artistName Artist name to search for
     * @return Map containing enhanced artist data (albums, videos, biography) or null if not found
     */
    @Cacheable(value = "enhancedArtists", key = "#artistName.toLowerCase()", unless = "#result == null")
    public Map<String, Object> getEnhancedArtistInfo(String artistName) {
        if (artistName == null || artistName.isEmpty()) {
            return null;
        }
        
        // TheAudioDB may not require API key for basic usage, but check if configured
        if (theAudioDbApiKey == null || theAudioDbApiKey.isEmpty() || "123".equals(theAudioDbApiKey)) {
            // TheAudioDB doesn't require API key for basic searches - continue without it
            System.out.println("EnhancedArtistService: TheAudioDB API key not configured, using public API");
        }
        
        try {
            // Search for artist - TheAudioDB format: /api/v1/json/{api_key}/search.php?s={artist}
            // If no API key, use: /api/v1/json/1/search.php (public endpoint)
            String apiKeySegment = (theAudioDbApiKey != null && !theAudioDbApiKey.isEmpty() && !"123".equals(theAudioDbApiKey)) 
                ? theAudioDbApiKey + "/" 
                : "1/"; // Use "1" as default for public API
            String searchUrl = THEAUDIODB_API_BASE_URL + "/" + apiKeySegment + "search.php?s=" + 
                              java.net.URLEncoder.encode(artistName, "UTF-8");
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );
            
            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                
                // TheAudioDB returns: { "artists": [...] }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> artists = (List<Map<String, Object>>) body.get("artists");
                
                if (artists != null && !artists.isEmpty()) {
                    Map<String, Object> artist = artists.get(0); // Use first match
                    
                    // Get artist ID for additional data
                    String artistId = (String) artist.get("idArtist");
                    if (artistId != null && !artistId.isEmpty()) {
                        // Fetch albums
                        List<Map<String, Object>> albums = getArtistAlbums(artistId);
                        if (albums != null && !albums.isEmpty()) {
                            artist.put("albums", albums);
                        }
                        
                        // Fetch music videos
                        List<Map<String, Object>> videos = getArtistVideos(artistId);
                        if (videos != null && !videos.isEmpty()) {
                            artist.put("videos", videos);
                        }
                    }
                    
                    return artist;
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching enhanced artist info for " + artistName + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Fetches artist albums from TheAudioDB.
     */
    private List<Map<String, Object>> getArtistAlbums(String artistId) {
        try {
            String url = THEAUDIODB_API_BASE_URL + "/album.php?i=" + artistId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> albums = (List<Map<String, Object>>) responseBody.get("album");
                return albums != null ? albums : new ArrayList<>();
            }
        } catch (Exception e) {
            System.out.println("Error fetching albums for artist " + artistId + ": " + e.getMessage());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Fetches artist music videos from TheAudioDB.
     */
    private List<Map<String, Object>> getArtistVideos(String artistId) {
        try {
            String apiKeySegment = (theAudioDbApiKey != null && !theAudioDbApiKey.isEmpty() && !"123".equals(theAudioDbApiKey)) 
                ? theAudioDbApiKey + "/" 
                : "1/";
            String url = THEAUDIODB_API_BASE_URL + "/" + apiKeySegment + "mvid.php?i=" + artistId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> videos = (List<Map<String, Object>>) responseBody.get("mvids");
                return videos != null ? videos : new ArrayList<>();
            }
        } catch (Exception e) {
            System.out.println("Error fetching videos for artist " + artistId + ": " + e.getMessage());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Gets high-quality album artwork URL for a track.
     * 
     * @param artistName Artist name
     * @param trackTitle Track title (optional, for album matching)
     * @return Album artwork URL or null if not found
     */
    public String getAlbumArtwork(String artistName, String trackTitle) {
        Map<String, Object> artistInfo = getEnhancedArtistInfo(artistName);
        if (artistInfo != null) {
            // Try to get artwork from artist info
            String strArtistThumb = (String) artistInfo.get("strArtistThumb");
            String strArtistLogo = (String) artistInfo.get("strArtistLogo");
            String strArtistFanart = (String) artistInfo.get("strArtistFanart");
            
            // Prefer logo, then thumb, then fanart
            if (strArtistLogo != null && !strArtistLogo.isEmpty()) {
                return strArtistLogo;
            } else if (strArtistThumb != null && !strArtistThumb.isEmpty()) {
                return strArtistThumb;
            } else if (strArtistFanart != null && !strArtistFanart.isEmpty()) {
                return strArtistFanart;
            }
            
            // Try to get from albums
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> albums = (List<Map<String, Object>>) artistInfo.get("albums");
            if (albums != null && !albums.isEmpty()) {
                // Get most recent album artwork
                Map<String, Object> latestAlbum = albums.get(0);
                String strAlbumThumb = (String) latestAlbum.get("strAlbumThumb");
                if (strAlbumThumb != null && !strAlbumThumb.isEmpty()) {
                    return strAlbumThumb;
                }
            }
        }
        
        return null;
    }
}

