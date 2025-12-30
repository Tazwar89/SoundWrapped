package com.soundwrapped.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

/**
 * Service for fetching lyrics using Lyrics.ovh API (no authentication required).
 * Provides lyrics for tracks to enhance the "Song of the Day" experience.
 */
@Service
public class LyricsService {
    
    private final RestTemplate restTemplate;
    private static final String LYRICS_API_BASE_URL = "https://api.lyrics.ovh/v1";
    
    public LyricsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Fetches lyrics for a track using Lyrics.ovh API.
     * Results are cached for 7 days since lyrics rarely change.
     * 
     * @param artist Artist name
     * @param title Track title
     * @return Lyrics text or null if not found
     */
    @Cacheable(value = "lyrics", key = "#artist + '|' + #title", unless = "#result == null")
    public String getLyrics(String artist, String title) {
        if (artist == null || artist.isEmpty() || title == null || title.isEmpty()) {
            return null;
        }
        
        try {
            // Clean artist and title names (remove special characters, extra spaces)
            String cleanArtist = cleanArtistName(artist);
            String cleanTitle = cleanTrackTitle(title);
            
            // Lyrics.ovh API format: /v1/{artist}/{title}
            String url = LYRICS_API_BASE_URL + "/" + 
                        java.net.URLEncoder.encode(cleanArtist, "UTF-8") + "/" + 
                        java.net.URLEncoder.encode(cleanTitle, "UTF-8");
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>(){}
            );
            
            Map<String, Object> body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object lyricsObj = body.get("lyrics");
                if (lyricsObj instanceof String lyrics) {
                    // Clean up lyrics (remove extra whitespace, normalize line breaks)
                    return cleanLyrics(lyrics);
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 404 means lyrics not found - this is expected for many tracks
            if (e.getStatusCode().value() == 404) {
                System.out.println("Lyrics not found for: " + artist + " - " + title);
            } else {
                System.out.println("Error fetching lyrics: " + e.getStatusCode() + " - " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Exception fetching lyrics for " + artist + " - " + title + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Cleans artist name for API call (removes common prefixes, extra spaces, etc.)
     */
    private String cleanArtistName(String artist) {
        if (artist == null) return "";
        
        // Remove common prefixes
        String cleaned = artist
            .replaceAll("(?i)^feat\\.?\\s+", "")
            .replaceAll("(?i)^ft\\.?\\s+", "")
            .replaceAll("(?i)^featuring\\s+", "")
            .replaceAll("(?i)\\s+feat\\.?\\s+.*$", "")
            .replaceAll("(?i)\\s+ft\\.?\\s+.*$", "")
            .replaceAll("(?i)\\s+featuring\\s+.*$", "");
        
        // Remove extra spaces and trim
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
        return cleaned;
    }
    
    /**
     * Cleans track title for API call (removes remix info, extra spaces, etc.)
     */
    private String cleanTrackTitle(String title) {
        if (title == null) return "";
        
        // Remove common suffixes in parentheses/brackets that might interfere
        // But keep the main title
        String cleaned = title
            .replaceAll("\\s*\\([^)]*remix[^)]*\\)", "")
            .replaceAll("\\s*\\[[^\\]]*remix[^\\]]*\\]", "")
            .replaceAll("\\s+", " ").trim();
        
        return cleaned;
    }
    
    /**
     * Cleans lyrics text (normalizes line breaks, removes excessive whitespace)
     */
    private String cleanLyrics(String lyrics) {
        if (lyrics == null) return "";
        
        // Normalize line breaks
        String cleaned = lyrics
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\r", "\n")
            .replaceAll("\\n{3,}", "\n\n") // Max 2 consecutive newlines
            .trim();
        
        return cleaned;
    }
}

