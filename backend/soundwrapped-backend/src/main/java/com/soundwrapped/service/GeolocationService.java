package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for IP-based geolocation using free geolocation APIs.
 * Uses ipapi.co as primary service (free tier: 1000 requests/day).
 */
@Service
public class GeolocationService {
    
    private final RestTemplate restTemplate;
    
    @Value("${geolocation.api.provider:ipapi}")
    private String geolocationProvider;
    
    public GeolocationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Get location data from IP address.
     * 
     * @param ipAddress IP address (can be null, will use request IP)
     * @return Map containing city, country, coordinates, etc.
     */
    public Map<String, Object> getLocationFromIp(String ipAddress) {
        try {
            // Use ipapi.co (free tier: 1000 requests/day, no API key needed)
            String url;
            if (ipAddress != null && !ipAddress.isEmpty()) {
                url = "https://ipapi.co/" + ipAddress + "/json/";
            } else {
                // Get location from current request IP
                url = "https://ipapi.co/json/";
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SoundWrapped/1.0");
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<String>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> data = response.getBody();
            if (data == null || data.containsKey("error")) {
                return getFallbackLocation();
            }
            
            // Transform ipapi.co response to our format
            Map<String, Object> location = new HashMap<String, Object>();
            location.put("city", data.getOrDefault("city", "Unknown"));
            location.put("country", data.getOrDefault("country_name", "Unknown"));
            location.put("countryCode", data.getOrDefault("country_code", ""));
            location.put("latitude", parseDouble(data.get("latitude"), 0.0));
            location.put("longitude", parseDouble(data.get("longitude"), 0.0));
            location.put("region", data.getOrDefault("region", ""));
            
            return location;
            
        } catch (RestClientException e) {
            System.out.println("Error fetching geolocation from ipapi.co: " + e.getMessage());
            // Fallback to alternative service or default
            return getFallbackLocation();
        }
    }
    
    /**
     * Alternative geolocation using ip-api.com (free tier: 45 requests/minute)
     */
    public Map<String, Object> getLocationFromIpAlternative(String ipAddress) {
        try {
            String url = "http://ip-api.com/json/" + (ipAddress != null ? ipAddress : "");
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SoundWrapped/1.0");
            headers.set("Accept", "application/json");
            HttpEntity<String> request = new HttpEntity<String>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> data = response.getBody();
            if (data == null || !"success".equals(data.get("status"))) {
                return getFallbackLocation();
            }
            
            Map<String, Object> location = new HashMap<String, Object>();
            location.put("city", data.getOrDefault("city", "Unknown"));
            location.put("country", data.getOrDefault("country", "Unknown"));
            location.put("countryCode", data.getOrDefault("countryCode", ""));
            location.put("latitude", parseDouble(data.get("lat"), 0.0));
            location.put("longitude", parseDouble(data.get("lon"), 0.0));
            location.put("region", data.getOrDefault("regionName", ""));
            
            return location;
            
        } catch (RestClientException e) {
            System.out.println("Error fetching geolocation from ip-api.com: " + e.getMessage());
            return getFallbackLocation();
        }
    }
    
    private Map<String, Object> getFallbackLocation() {
        Map<String, Object> fallback = new HashMap<String, Object>();
        fallback.put("city", "Unknown");
        fallback.put("country", "Unknown");
        fallback.put("countryCode", "");
        fallback.put("latitude", 0.0);
        fallback.put("longitude", 0.0);
        fallback.put("region", "");
        return fallback;
    }
    
    private Double parseDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

