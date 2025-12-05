package com.soundwrapped.repository;

import com.soundwrapped.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    
    /**
     * Find all activities for a user within a date range
     */
    List<UserActivity> findBySoundcloudUserIdAndCreatedAtBetween(
        String soundcloudUserId, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );

    /**
     * Count activities by type for a user
     */
    long countBySoundcloudUserIdAndActivityType(String soundcloudUserId, UserActivity.ActivityType activityType);

    /**
     * Get total play duration in milliseconds for a user within date range
     */
    @Query("SELECT COALESCE(SUM(u.playDurationMs), 0) FROM UserActivity u " +
           "WHERE u.soundcloudUserId = :userId AND u.activityType = 'PLAY' " +
           "AND u.createdAt BETWEEN :startDate AND :endDate")
    Long getTotalPlayDurationMs(@Param("userId") String userId, 
                                 @Param("startDate") LocalDateTime startDate,
                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Get most played tracks for a user
     */
    @Query("SELECT u.trackId, COUNT(u) as playCount " +
           "FROM UserActivity u " +
           "WHERE u.soundcloudUserId = :userId AND u.activityType = 'PLAY' " +
           "AND u.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY u.trackId " +
           "ORDER BY playCount DESC")
    List<Object[]> getMostPlayedTracks(@Param("userId") String userId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Get activity count by type for a user
     */
    @Query("SELECT u.activityType, COUNT(u) as count " +
           "FROM UserActivity u " +
           "WHERE u.soundcloudUserId = :userId " +
           "AND u.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY u.activityType")
    List<Object[]> getActivityCountByType(@Param("userId") String userId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Find play activities for a user within a date range
     */
    List<UserActivity> findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
        String soundcloudUserId,
        UserActivity.ActivityType activityType,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
}
