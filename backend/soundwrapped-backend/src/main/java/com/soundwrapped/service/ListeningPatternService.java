package com.soundwrapped.service;

import com.soundwrapped.entity.UserActivity;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing listening patterns from tracked activity data.
 * Provides insights into when users listen to music (time of day, day of week).
 */
@Service
public class ListeningPatternService {

    private final UserActivityRepository activityRepository;

    public ListeningPatternService(UserActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * Analyze listening patterns for a user
     * 
     * @param soundcloudUserId The SoundCloud user ID
     * @return Analysis of listening patterns
     */
    public Map<String, Object> analyzeListeningPatterns(String soundcloudUserId) {
        Map<String, Object> analysis = new HashMap<String, Object>();
        
        // Get all play activities for current year
        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        LocalDateTime yearEnd = LocalDateTime.now();
        
        List<UserActivity> playActivities = activityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
            soundcloudUserId,
            UserActivity.ActivityType.PLAY,
            yearStart,
            yearEnd
        );
        
        if (playActivities.isEmpty()) {
            analysis.put("hasData", false);
            analysis.put("message", "Not enough listening data to analyze patterns");
            return analysis;
        }
        
        // Analyze by hour of day (0-23)
        Map<Integer, Integer> hourCounts = new HashMap<Integer, Integer>();
        Map<Integer, Long> hourListeningMs = new HashMap<Integer, Long>();
        
        // Analyze by day of week (1=Monday, 7=Sunday)
        Map<DayOfWeek, Integer> dayCounts = new HashMap<DayOfWeek, Integer>();
        Map<DayOfWeek, Long> dayListeningMs = new HashMap<DayOfWeek, Long>();
        
        for (UserActivity activity : playActivities) {
            LocalDateTime createdAt = activity.getCreatedAt();
            int hour = createdAt.getHour();
            DayOfWeek dayOfWeek = createdAt.getDayOfWeek();
            long durationMs = activity.getPlayDurationMs() != null ? activity.getPlayDurationMs() : 0L;
            
            // Count by hour
            hourCounts.put(hour, hourCounts.getOrDefault(hour, 0) + 1);
            hourListeningMs.put(hour, hourListeningMs.getOrDefault(hour, 0L) + durationMs);
            
            // Count by day of week
            dayCounts.put(dayOfWeek, dayCounts.getOrDefault(dayOfWeek, 0) + 1);
            dayListeningMs.put(dayOfWeek, dayListeningMs.getOrDefault(dayOfWeek, 0L) + durationMs);
        }
        
        // Find peak listening hour
        int peakHour = hourCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(-1);
        
        // Find peak listening day
        DayOfWeek peakDay = dayCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        // Calculate listening time persona
        String persona = calculateListeningPersona(hourCounts, hourListeningMs);
        
        // Prepare hour distribution (0-23)
        List<Map<String, Object>> hourDistribution = new ArrayList<Map<String, Object>>();
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> hourData = new HashMap<String, Object>();
            hourData.put("hour", hour);
            hourData.put("hourLabel", formatHour(hour));
            hourData.put("playCount", hourCounts.getOrDefault(hour, 0));
            hourData.put("listeningMs", hourListeningMs.getOrDefault(hour, 0L));
            hourData.put("listeningHours", hourListeningMs.getOrDefault(hour, 0L) / 1000.0 / 60.0 / 60.0);
            hourDistribution.add(hourData);
        }
        
        // Prepare day distribution
        List<Map<String, Object>> dayDistribution = new ArrayList<Map<String, Object>>();
        DayOfWeek[] daysOfWeek = DayOfWeek.values();
        for (DayOfWeek day : daysOfWeek) {
            Map<String, Object> dayData = new HashMap<String, Object>();
            dayData.put("day", day.name());
            dayData.put("dayLabel", formatDay(day));
            dayData.put("playCount", dayCounts.getOrDefault(day, 0));
            dayData.put("listeningMs", dayListeningMs.getOrDefault(day, 0L));
            dayData.put("listeningHours", dayListeningMs.getOrDefault(day, 0L) / 1000.0 / 60.0 / 60.0);
            dayDistribution.add(dayData);
        }
        
        analysis.put("hasData", true);
        analysis.put("totalPlays", playActivities.size());
        analysis.put("peakHour", peakHour);
        analysis.put("peakHourLabel", peakHour >= 0 ? formatHour(peakHour) : "N/A");
        analysis.put("peakDay", peakDay != null ? peakDay.name() : "N/A");
        analysis.put("peakDayLabel", peakDay != null ? formatDay(peakDay) : "N/A");
        analysis.put("listeningPersona", persona);
        analysis.put("hourDistribution", hourDistribution);
        analysis.put("dayDistribution", dayDistribution);
        
        return analysis;
    }

    /**
     * Calculate listening time persona based on peak listening hours
     */
    private String calculateListeningPersona(Map<Integer, Integer> hourCounts, Map<Integer, Long> hourListeningMs) {
        if (hourCounts.isEmpty()) {
            return "Early Bird"; // Default
        }
        
        // Find peak hours
        int peakHour = hourCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(12);
        
        // Calculate average listening time by time period
        long morningListening = 0L; // 6-11
        long afternoonListening = 0L; // 12-17
        long eveningListening = 0L; // 18-23
        long nightListening = 0L; // 0-5
        
        for (Map.Entry<Integer, Long> entry : hourListeningMs.entrySet()) {
            int hour = entry.getKey();
            long ms = entry.getValue();
            
            if (hour >= 6 && hour < 12) {
                morningListening += ms;
            } else if (hour >= 12 && hour < 18) {
                afternoonListening += ms;
            } else if (hour >= 18 && hour < 24) {
                eveningListening += ms;
            } else {
                nightListening += ms;
            }
        }
        
        // Determine persona based on peak listening time
        if (peakHour >= 6 && peakHour < 12) {
            return "Early Bird 🌅";
        } else if (peakHour >= 12 && peakHour < 18) {
            return "Afternoon Listener ☀️";
        } else if (peakHour >= 18 && peakHour < 24) {
            return "Evening Vibes 🌆";
        } else {
            return "Night Owl 🦉";
        }
    }

    private String formatHour(int hour) {
        if (hour == 0) {
            return "12 AM";
        } else if (hour < 12) {
            return hour + " AM";
        } else if (hour == 12) {
            return "12 PM";
        } else {
            return (hour - 12) + " PM";
        }
    }

    private String formatDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Monday";
            case TUESDAY -> "Tuesday";
            case WEDNESDAY -> "Wednesday";
            case THURSDAY -> "Thursday";
            case FRIDAY -> "Friday";
            case SATURDAY -> "Saturday";
            case SUNDAY -> "Sunday";
        };
    }
}