# Phase 1 & 2 Features Implementation

## Overview

This document details the Phase 1 and Phase 2 features implemented for SoundWrapped, inspired by SoundCloud Playback 2025 and Spotify Wrapped.

## Phase 1 Features ✅

### 1. Shareable Story Cards

**Description**: Downloadable high-quality story cards (9:16 aspect ratio) for social media sharing on Instagram, TikTok, and other platforms.

**Implementation**:
- **Component**: `ShareableStoryCard.tsx`
- **Technology**: `html2canvas` for image generation
- **Features**:
  - 8 different card types (Summary, Listening, Top Track, Top Artist, Underground, Trendsetter, Repost, Archetype)
  - SoundCloud Playback-inspired design with vibrant gradients
  - Bold typography and clean layouts
  - High-resolution export (2x scale)
  - Card type selector for choosing which metric to share

**Card Types**:
1. **Summary**: Overview card with username, top track, hours, likes, and underground support
2. **Listening**: Focused on total listening hours with orange/yellow gradient
3. **Top Track**: Highlights #1 track with purple/pink gradient
4. **Top Artist**: Highlights #1 artist with blue/indigo gradient
5. **Underground**: Support the Underground percentage with purple/pink gradient
6. **Trendsetter**: Trendsetter badge and score with yellow/orange/red gradient
7. **Repost**: Repost King/Queen metrics with green/emerald gradient
8. **Archetype**: Sonic Archetype persona with indigo/purple/pink gradient

**Usage**:
- Click "Download Story Card" button on Wrapped page
- Select card type from buttons
- Click "Download Card" to generate and download PNG image

### 2. Support the Underground Metric

**Description**: Calculates and displays the percentage of listening time spent on "underground" artists (artists with fewer than 5,000 followers).

**Implementation**:
- **Backend**: `SoundWrappedService.calculateUndergroundSupportPercentage()`
- **Frontend**: New slide in WrappedPage
- **Calculation**:
  - Iterates through all tracks
  - Checks artist follower count
  - Calculates total listening time vs. underground listening time
  - Returns percentage (0-100)

**Display**:
- Percentage shown with 1 decimal place
- Personalized messages based on percentage:
  - ≥50%: "You're a true champion of the underground scene!"
  - ≥25%: "You have great taste in discovering new artists!"
  - <25%: "Every artist starts somewhere—thanks for supporting them!"

### 3. Year in Review Poetry

**Description**: AI-generated personalized poems celebrating the user's musical journey using their top tracks and genres.

**Implementation**:
- **Backend**: `SoundWrappedService.generateYearInReviewPoetry()`
- **AI Model**: Groq API (`llama-3.3-70b-versatile`)
- **Process**:
  1. Collects top 3 tracks and top 5 genres
  2. Builds context with username, track titles, artists, and genres
  3. Sends prompt to Groq API
  4. Generates 4-6 line poem
  5. Falls back to default poem if generation fails

**Display**:
- New slide in WrappedPage
- Formatted with line breaks for readability
- Sparkles icon for visual appeal

## Phase 2 Features ✅

### 1. The Trendsetter (Early Adopter) Score

**Description**: Measures how early users discovered tracks compared to when they were created, weighted by current popularity.

**Implementation**:
- **Backend**: `SoundWrappedService.calculateTrendsetterScore()`
- **Data Source**: UserActivity repository (first play timestamps)
- **Calculation**:
  - **Early Adopter Tracks**: Played within 7 days of creation
  - **Visionary Tracks**: Played within 30 days of creation AND now have >100k plays
  - **Score**: Weighted by current popularity (playback count)

**Badge Levels**:
- **Visionary**: ≥5 visionary tracks OR score ≥1000
- **Trendsetter**: ≥2 visionary tracks OR score ≥500
- **Early Adopter**: ≥5 early adopter tracks OR score ≥200
- **Explorer**: ≥1 early adopter track OR score ≥50
- **Listener**: Default badge

**Display**:
- New slide in WrappedPage
- Shows badge, score, description, and stats (visionary tracks, early adopter tracks)

### 2. The Repost King/Queen

**Description**: Tracks how many reposted tracks went on to become popular/trending.

**Implementation**:
- **Backend**: `SoundWrappedService.calculateRepostKingScore()`
- **Data Source**: UserActivity repository (REPOST activities)
- **Calculation**:
  - Gets all tracks user reposted in the past year
  - Checks which reposted tracks now have `reposts_count > 1000` (trending threshold)
  - Calculates success percentage

**Badge Levels**:
- **Repost Royalty**: ≥10 trending tracks OR ≥50% success rate
- **Repost King/Queen**: ≥5 trending tracks OR ≥30% success rate
- **Repost Enthusiast**: ≥2 trending tracks OR ≥15% success rate
- **Repost Supporter**: ≥5 total reposts
- **Listener**: Default badge

**Display**:
- New slide in WrappedPage
- Shows badge, trending tracks count, total reposts, success percentage, and description

### 3. The Sonic Archetype

**Description**: AI-generated musical persona based on listening data (genres, artists, listening patterns).

**Implementation**:
- **Backend**: `SoundWrappedService.generateSonicArchetype()`
- **AI Model**: Groq API (`llama-3.3-70b-versatile`)
- **Process**:
  1. Collects top genres, top artists, and listening patterns
  2. Builds context from user's music profile
  3. Sends prompt to Groq API requesting creative persona
  4. Generates persona title (e.g., "The 3 AM Lo-Fi Scholar") and 2-3 sentence description
  5. Falls back to default persona if generation fails

**Display**:
- New slide in WrappedPage
- Shows persona title and description
- Purple gradient background with sparkles icon

## Technical Details

### Backend Implementation

**New Methods in `SoundWrappedService.java`**:
- `calculateUndergroundSupportPercentage()`: Calculates underground support metric
- `generateYearInReviewPoetry()`: Generates AI poetry
- `calculateTrendsetterScore()`: Calculates trendsetter metrics
- `calculateRepostKingScore()`: Calculates repost success metrics
- `generateSonicArchetype()`: Generates AI persona
- `getGroqDescription()` (overloaded): Custom prompt support for poetry/persona

**Data Integration**:
- All Phase 1 & 2 metrics added to `getFullWrappedSummary()`
- Metrics included in `formattedWrappedSummary()` response
- Proper null handling and fallbacks

### Frontend Implementation

**New Components**:
- `ShareableStoryCard.tsx`: Story card generation with multiple card types

**Updated Components**:
- `WrappedPage.tsx`: Added new slides for Phase 1 & 2 features
- `MusicDataContext.tsx`: Updated `WrappedData` interface with new fields

**New Fields in `WrappedData` Interface**:
```typescript
undergroundSupportPercentage?: number
yearInReviewPoetry?: string
trendsetterScore?: {
  score: number
  badge: string
  description: string
  visionaryTracks: number
  earlyAdopterTracks: number
}
repostKingScore?: {
  repostedTracks: number
  trendingTracks: number
  percentage: number
  badge: string
  description: string
}
sonicArchetype?: string
```

## API Response Structure

The `/api/soundcloud/wrapped/full` endpoint now includes:

```json
{
  "undergroundSupportPercentage": 41.9,
  "yearInReviewPoetry": "In the rhythm of your year...",
  "trendsetterScore": {
    "score": 450,
    "badge": "Early Adopter",
    "description": "You're always ahead of the curve!",
    "visionaryTracks": 2,
    "earlyAdopterTracks": 8
  },
  "repostKingScore": {
    "repostedTracks": 15,
    "trendingTracks": 5,
    "percentage": 33.3,
    "badge": "Repost King/Queen",
    "description": "You have impeccable taste!"
  },
  "sonicArchetype": "The 3 AM Lo-Fi Scholar - You find peace in the quiet hours..."
}
```

## Dependencies

### Backend
- No new dependencies (uses existing Groq API integration)

### Frontend
- `html2canvas`: For story card image generation
  ```bash
  npm install html2canvas
  ```

## Configuration

### Required API Keys
- `GROQ_API_KEY`: Required for AI-generated content (poetry, persona, descriptions)

### Optional API Keys
- `SERPAPI_API_KEY`: Optional, used for additional research context

## Testing

### Manual Testing Steps
1. Authenticate with SoundCloud
2. Navigate to Wrapped page
3. Verify all new slides appear:
   - Support the Underground
   - Year in Review Poetry
   - Trendsetter Score
   - Repost King/Queen
   - Sonic Archetype
4. Test story card download:
   - Click "Download Story Card"
   - Select different card types
   - Download and verify image quality

### Expected Behavior
- All metrics calculate correctly
- AI-generated content appears (if Groq API key is configured)
- Story cards download successfully
- Fallback content appears if AI generation fails

## Future Enhancements

### Potential Phase 3 Features
- Interactive Genre Constellation (3D visualization)
- Dynamic Mood Background (color changes based on track energy)
- Musical Migration (geographic taste shifts over time)
- Taste Twin Discovery (most similar user matching)

## Notes

- All AI features use Groq API (free tier, no credit card required)
- Metrics gracefully handle missing data
- Story cards are optimized for social media (9:16 aspect ratio)
- All calculations use tracked activity data from UserActivity repository

