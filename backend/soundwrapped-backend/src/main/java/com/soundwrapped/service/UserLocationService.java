package com.soundwrapped.service;

import com.soundwrapped.entity.UserLocation;
import com.soundwrapped.repository.UserLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Service for managing user location data.
 * Handles storing and retrieving IP-based geolocation data.
 */
@Service
public class UserLocationService {
    
    private final UserLocationRepository locationRepository;
    private final GeolocationService geolocationService;
    
    public UserLocationService(
            UserLocationRepository locationRepository,
            GeolocationService geolocationService) {
        this.locationRepository = locationRepository;
        this.geolocationService = geolocationService;
    }
    
    /**
     * Update or create user location from IP address.
     * 
     * @param soundcloudUserId SoundCloud user ID
     * @param ipAddress IP address (can be null, will use request IP)
     * @return UserLocation entity
     */
    @Transactional
    public UserLocation updateUserLocation(String soundcloudUserId, String ipAddress) {
        // Get location from IP
        Map<String, Object> locationData = geolocationService.getLocationFromIp(ipAddress);
        
        // If primary service fails, try alternative
        if ("Unknown".equals(locationData.get("city"))) {
            locationData = geolocationService.getLocationFromIpAlternative(ipAddress);
        }
        
        // Find existing location or create new
        Optional<UserLocation> existing = locationRepository.findBySoundcloudUserId(soundcloudUserId);
        
        UserLocation userLocation;
        if (existing.isPresent()) {
            userLocation = existing.get();
        } else {
            userLocation = new UserLocation();
            userLocation.setSoundcloudUserId(soundcloudUserId);
        }
        
        // Update location data
        userLocation.setCity((String) locationData.getOrDefault("city", "Unknown"));
        userLocation.setCountry((String) locationData.getOrDefault("country", "Unknown"));
        userLocation.setCountryCode((String) locationData.getOrDefault("countryCode", ""));
        userLocation.setLatitude((Double) locationData.getOrDefault("latitude", 0.0));
        userLocation.setLongitude((Double) locationData.getOrDefault("longitude", 0.0));
        userLocation.setRegion((String) locationData.getOrDefault("region", ""));
        
        return locationRepository.save(userLocation);
    }
    
    /**
     * Get user location by SoundCloud user ID.
     */
    public Optional<UserLocation> getUserLocation(String soundcloudUserId) {
        return locationRepository.findBySoundcloudUserId(soundcloudUserId);
    }
    
    /**
     * Get all users in a specific city.
     */
    public java.util.List<UserLocation> getUsersByCity(String city, String country) {
        return locationRepository.findByCityAndCountry(city, country);
    }
    
    /**
     * Get all users in a specific country.
     */
    public java.util.List<UserLocation> getUsersByCountry(String country) {
        return locationRepository.findByCountry(country);
    }
}

