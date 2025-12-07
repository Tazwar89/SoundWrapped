package com.soundwrapped.repository;

import com.soundwrapped.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {
    
    /**
     * Find location by SoundCloud user ID
     */
    Optional<UserLocation> findBySoundcloudUserId(String soundcloudUserId);
    
    /**
     * Find all users in a specific city
     */
    List<UserLocation> findByCityAndCountry(String city, String country);
    
    /**
     * Find all users in a specific country
     */
    List<UserLocation> findByCountry(String country);
    
    /**
     * Get distinct cities with user counts
     */
    @Query("SELECT ul.city, ul.country, ul.latitude, ul.longitude, COUNT(ul) as userCount " +
           "FROM UserLocation ul " +
           "GROUP BY ul.city, ul.country, ul.latitude, ul.longitude " +
           "ORDER BY userCount DESC")
    List<Object[]> getCitiesWithUserCounts();
    
    /**
     * Get users in a specific city
     */
    @Query("SELECT ul.soundcloudUserId FROM UserLocation ul WHERE ul.city = :city AND ul.country = :country")
    List<String> getUserIdsByCity(@Param("city") String city, @Param("country") String country);
}

