# Codebase Performance Analysis & Bug Report

## Critical Performance Issues

### 1. **SimpleDateFormat Created in Loop** ⚠️ HIGH PRIORITY
**Location:** `SoundWrappedService.calculateTrendsetterScore()` line 4314
**Issue:** Creating `SimpleDateFormat` inside a loop is expensive and thread-unsafe
**Impact:** O(n) unnecessary object creation, potential thread-safety issues
**Fix:** Create once outside loop or use `DateTimeFormatter` (thread-safe)

### 2. **N+1 API Calls in MusicTasteMapService** ⚠️ CRITICAL
**Location:** `MusicTasteMapService.getMusicTasteMap()` lines 79-108
**Issue:** Making API calls (`getUserTracksById`) for each user in each city in nested loops
**Impact:** O(cities × users_per_city) API calls - could be hundreds/thousands of calls
**Time Complexity:** O(n²) or worse
**Fix:** Batch API calls, add caching, or limit the number of users analyzed per city

### 3. **Inefficient Genre Matching** ⚠️ MEDIUM PRIORITY
**Location:** `SoundWrappedService.generateMusicAge()` lines 4573, 4576
**Issue:** Using `stream().anyMatch()` inside a loop creates O(n×m) complexity
**Impact:** For 10 genres × 10 classic genres = 100 operations per iteration
**Fix:** Use HashSet.contains() for O(1) lookups

### 4. **Thread.sleep Blocking Calls** ⚠️ MEDIUM PRIORITY
**Location:** `SoundWrappedService.getFullWrappedSummary()` lines 3885, 3893, 3901, 3909
**Issue:** Blocking thread execution with Thread.sleep()
**Impact:** Blocks entire request thread, poor user experience
**Fix:** Use async/await pattern or parallel execution where possible

## Bugs

### 1. **Logic Bug in calculateTrendsetterScore** 🐛
**Location:** Line 4287-4289
**Issue:** 
```java
firstPlayTimestamps.putIfAbsent(trackId, playTime);
if (playTime.isBefore(firstPlayTimestamps.get(trackId))) {
    firstPlayTimestamps.put(trackId, playTime);
}
```
The `isBefore` check will always be false because we just put the same value. Should check BEFORE putting.

### 2. **Incorrect Fallback in parseSoundCloudDate** 🐛
**Location:** Line 4671
**Issue:** Returns `LocalDate.now()` on parsing failure, which could give incorrect results
**Impact:** Tracks with unparseable dates are treated as "today", skewing music age calculations

### 3. **RepostKingScore Inefficiency** ⚠️
**Location:** `calculateRepostKingScore()` line 4437
**Issue:** Iterating through all likes to check if they were reposted
**Impact:** O(n×m) complexity where n=likes, m=reposted tracks
**Fix:** Use Set intersection for O(n+m) complexity

## Code Quality Issues

### 1. **No Caching for Expensive Operations**
- `getMusicTasteMap()` recalculates everything on each call
- Genre analysis could be cached per user
- Artist follower counts fetched repeatedly

### 2. **Frontend Polling Too Frequent**
- HomePage polls online users every 30 seconds
- Could be optimized to only poll when tab is visible

### 3. **Missing Error Handling**
- `MusicTasteMapService.getUserTracksById()` silently fails
- No retry logic for API failures
- No circuit breaker pattern

## Recommended Fixes Priority

1. ✅ **CRITICAL:** Fix MusicTasteMapService API call pattern - **FIXED**
   - Added limit of 10 users per city
   - Added safety limit of 10 API calls per city
   - Added random sampling for representative results

2. ✅ **HIGH:** Fix SimpleDateFormat in loop - **FIXED**
   - Replaced with thread-safe DateTimeFormatter
   - Created once outside loop

3. ✅ **HIGH:** Fix calculateTrendsetterScore logic bug - **FIXED**
   - Fixed logic to check before putting in map
   - Now correctly finds earliest play timestamp

4. ✅ **MEDIUM:** Optimize genre matching - **IMPROVED**
   - Changed to use HashSet for better structure
   - Still uses stream for substring matching (necessary)

5. ✅ **MEDIUM:** Fix parseSoundCloudDate fallback - **FIXED**
   - Returns null instead of current date
   - Prevents skewing music age calculations

6. ✅ **LOW:** Optimize frontend polling - **FIXED**
   - Only polls when tab is visible
   - Fetches immediately when tab becomes visible

## Additional Improvements Made

- Fixed date parsing to use modern Java time API (thread-safe)
- Added validation for parsed dates
- Improved error handling in MusicTasteMapService
- Added visibility-based polling optimization

## Remaining Recommendations

1. **Caching:** Consider adding Redis or in-memory cache for:
   - Music taste map results (cache per user for 1 hour)
   - Genre analysis results
   - Artist follower counts

2. **Async Processing:** Consider making `getFullWrappedSummary()` async:
   - Use CompletableFuture for parallel API calls
   - Return immediately with a job ID, poll for results

3. **Rate Limiting:** Add rate limiting middleware:
   - Prevent API abuse
   - Implement exponential backoff for failed requests

4. **Database Indexing:** Ensure indexes exist for:
   - `user_activities.created_at` (for online users query)
   - `user_activities.soundcloud_user_id, activity_type, created_at` (composite index)

