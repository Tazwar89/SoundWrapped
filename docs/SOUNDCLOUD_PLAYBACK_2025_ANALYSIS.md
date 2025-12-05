# SoundCloud Playback 2025 Feature Analysis

## Overview
SoundCloud's 2025 Playback (similar to Spotify Wrapped) was released in December 2025. This document analyzes their features and identifies opportunities to enhance SoundWrapped.

## SoundCloud Playback 2025 Features

### For Fans (Listeners)

1. **Top 5 Statistics**
   - Top 5 listened artists
   - Top 5 tracks
   - Top 5 genres

2. **Listening Metrics**
   - Total plays
   - Total listening time

3. **Moods Tracking**
   - Track your moods through all of 2025
   - Mood-based listening patterns

4. **Genre Discovery**
   - Genre Discovery Count: How many genres you explored this year
   - Genre exploration insights

5. **Social Features**
   - **Music Doppelgänger**: From the profiles you follow, the user that shares highest music-taste percentage
   - Taste matching with other users

6. **Listening Patterns**
   - **Listening Time Persona**: When you're most active (time-based listening patterns)
   - Peak listening times

7. **Historical Data**
   - Dedicated homepage module featuring previous years' Playback playlists
   - Year-over-year comparison

### For Artists

1. **Top Statistics**
   - Top 5 fans
   - Top 5 uploaded tracks
   - Top 5 tracks by engagement

2. **Engagement Metrics**
   - Total plays
   - Total listening time
   - Total engagement with tracks

3. **Audience Insights**
   - **Top cities**: Where your fans are located
   - **Time fans spent**: How much time fans spent with your tracks
   - **Follower growth**: Growth metrics

4. **Artist Connections**
   - **Artist Doppelgänger**: Artists you should connect with
   - Similar artist recommendations

5. **Fan Recognition**
   - Dedicated spotlight to show love to top fans
   - Fan appreciation features

## Current SoundWrapped Features

### Already Implemented
- ✅ User profile data (followers, following, tracks uploaded)
- ✅ Top tracks (by play count)
- ✅ Top artists (from liked tracks)
- ✅ Total listening hours (based on track durations)
- ✅ Top reposted tracks
- ✅ Account age
- ✅ Playlists created
- ✅ Comments posted
- ✅ Browser extension for real-time listening tracking
- ✅ Dashboard analytics (in-app plays, likes, reposts, shares)

### Missing Features (Compared to SoundCloud Playback 2025)

1. **Genre Analysis**
   - ❌ Genre discovery count
   - ❌ Top genres
   - ❌ Genre exploration timeline

2. **Mood Tracking**
   - ❌ Mood-based listening patterns
   - ❌ Mood timeline throughout the year

3. **Social Features**
   - ❌ Music Doppelgänger (taste matching with followed users)
   - ❌ Taste similarity percentage with other users

4. **Time-Based Insights**
   - ❌ Listening Time Persona (when you're most active)
   - ❌ Peak listening times
   - ❌ Listening patterns by day/week/month

5. **Artist-Specific Features**
   - ❌ Top fans identification
   - ❌ Audience location (cities)
   - ❌ Artist Doppelgänger recommendations
   - ❌ Fan spotlight/recognition

6. **Historical Data**
   - ❌ Previous years' Playback playlists
   - ❌ Year-over-year comparison

## Implementation Opportunities

### High Priority (Easy Wins)

1. **Genre Analysis**
   - SoundCloud API provides genre tags on tracks
   - Can aggregate genres from liked/uploaded tracks
   - Calculate genre discovery count
   - Show top genres by play count/listening time

2. **Time-Based Listening Patterns**
   - Use browser extension tracking data
   - Track timestamps of plays
   - Identify peak listening times
   - Create "Listening Time Persona" (e.g., "Night Owl", "Morning Listener")

3. **Top Genres**
   - Aggregate genres from user's tracks
   - Rank by total listening time or play count
   - Show genre distribution chart

### Medium Priority (Requires More Data)

4. **Music Doppelgänger**
   - Compare liked tracks between users
   - Calculate taste similarity percentage
   - Find user with highest similarity from followed users
   - Requires SoundCloud API access to other users' likes (may need permissions)

5. **Mood Tracking**
   - Would require manual user input or AI analysis
   - Could infer moods from genres/tempo
   - Less accurate than explicit tracking

6. **Audience Insights (For Artists)**
   - Requires SoundCloud API access to track-level analytics
   - May need SoundCloud Pro/Pro Unlimited subscription data
   - City-level data might not be available via public API

### Low Priority (Future Enhancements)

7. **Historical Data**
   - Store yearly snapshots
   - Compare year-over-year
   - Requires data persistence over multiple years

8. **Artist Doppelgänger**
   - Similar artist recommendations
   - Based on genre, tags, or related tracks API

## Recommended Next Steps

### Phase 1: Genre Analysis (Quick Win)
1. Extract genres from user's tracks (liked + uploaded)
2. Aggregate and count genres
3. Calculate top genres by listening time
4. Add genre discovery count
5. Display in Dashboard analytics

### Phase 2: Time-Based Insights
1. Enhance browser extension to track play timestamps
2. Store timestamp data in backend
3. Analyze listening patterns (hour of day, day of week)
4. Create "Listening Time Persona" feature
5. Display peak listening times

### Phase 3: Social Features (If API Allows)
1. Research SoundCloud API permissions for accessing other users' likes
2. Implement taste similarity calculation
3. Find Music Doppelgänger from followed users
4. Display similarity percentage

### Phase 4: Artist Features (If Data Available)
1. Check if SoundCloud API provides:
   - Track-level analytics (plays by location)
   - Follower location data
   - Top listeners/fans
2. Implement audience insights if available
3. Add artist recommendations

## Technical Considerations

### SoundCloud API Limitations
- Genre data: Available on tracks ✅
- Timestamp data: Need to track ourselves ✅ (via browser extension)
- User likes: May require specific permissions ⚠️
- Location data: Likely requires Pro subscription ❌
- Historical data: Need to store ourselves ✅

### Data Storage Requirements
- Genre analysis: Can be calculated on-the-fly ✅
- Time-based patterns: Need to store play timestamps ✅
- Historical comparisons: Need yearly snapshots 📦
- Social features: May need to cache user data 📦

## Conclusion

SoundWrapped already has a solid foundation with real-time tracking via the browser extension. The main opportunities are:

1. **Genre Analysis** - Easy to implement, high value
2. **Time-Based Insights** - Leverage existing tracking data
3. **Social Features** - Depends on API permissions
4. **Historical Data** - Long-term enhancement

The browser extension's real-time tracking gives SoundWrapped an advantage over SoundCloud's Playback, which only shows annual summaries. We can provide real-time insights throughout the year!

