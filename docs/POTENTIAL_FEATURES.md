# Potential Features Using Public APIs

Based on the [public-apis repository](https://github.com/public-apis/public-apis), here are potential features that could enhance SoundWrapped using free, publicly available APIs.

## 🎵 Music & Audio APIs

### 1. **Lyrics Integration**
- **APIs**: 
  - **Genius API** (OAuth required): Access to song lyrics, annotations, and artist information
  - **Lyrics.ovh** (No Auth): Free lyrics API
  - **Musixmatch API** (API Key): Lyrics, translations, and metadata
- **Feature Ideas**:
  - Display lyrics for "Song of the Day" tracks
  - Show lyrics while playing tracks
  - Lyrics search functionality
  - Annotated lyrics (Genius-style explanations)
  - Lyrics-based music discovery ("Find songs with similar themes")

### 2. **Enhanced Artist Information**
- **APIs**:
  - **TheAudioDB API** (API Key, Free tier): Artist biographies, discographies, music videos, album art
  - **MusicBrainz API** (No Auth): Open music encyclopedia with detailed metadata
  - **Last.fm API** (API Key): Artist tags, similar artists, top tracks, charts
- **Feature Ideas**:
  - **Rich Artist Profiles**: Album artwork, discography, music videos
  - **Similar Artists Discovery**: "If you like X, you might like Y"
  - **Artist Tags & Genres**: More granular genre classification
  - **Music Videos**: Embed music videos for featured artists
  - **Album Information**: Show albums and release dates for artists

### 3. **Music Discovery & Recommendations**
- **APIs**:
  - **Last.fm API**: Similar artists, top tracks, user recommendations
  - **MusicBrainz API**: Related artists, release groups
  - **Discogs API** (OAuth): Music database with recommendations
- **Feature Ideas**:
  - **"Similar to Your Taste"**: Recommend artists based on listening history
  - **Genre Exploration**: Discover subgenres and related genres
  - **Release Calendar**: Upcoming releases from favorite artists
  - **Music Discovery Playlist**: Auto-generated playlists based on taste

### 4. **Music Charts & Trends**
- **APIs**:
  - **Last.fm API**: Global charts, trending tracks
  - **MusicBrainz API**: Popular releases
- **Feature Ideas**:
  - **Global Charts**: Compare user taste to global trends
  - **Trending Now**: Real-time trending tracks (beyond SoundCloud)
  - **Historical Charts**: "What was popular when you started listening?"
  - **Genre Charts**: Top tracks by genre worldwide

## 🎨 Visual & Media APIs

### 5. **Album Artwork & Images**
- **APIs**:
  - **TheAudioDB API**: High-quality album artwork
  - **MusicBrainz API**: Cover art via Cover Art Archive
  - **Last.fm API**: Artist images and album covers
- **Feature Ideas**:
  - **Enhanced Track Cards**: High-resolution album artwork
  - **Artist Image Gallery**: Professional photos of artists
  - **Visual Album Browser**: Browse albums by artwork
  - **Artwork Analysis**: Color palette extraction from album covers

### 6. **Music Videos**
- **APIs**:
  - **TheAudioDB API**: Music video links
  - **YouTube Data API** (API Key): Search for official music videos
- **Feature Ideas**:
  - **Video Player Integration**: Show music videos for tracks
  - **Video Recommendations**: "Watch the video for this track"
  - **Live Performances**: Link to live performance videos

## 🔍 Web Search & Research APIs

### 7. **Enhanced Web Search for Descriptions**
- **APIs**:
  - **SerpAPI** (API Key, Free tier: 250/month): Comprehensive web search with knowledge graph, answer boxes, organic results
  - **Current**: Wikipedia API, Google Knowledge Graph API
- **Feature Ideas**:
  - **Comprehensive Research**: Single API call returns knowledge graph, answer boxes, and top search results
  - **Better for Obscure Artists**: Find information even when Wikipedia/KG don't have entries
  - **Real-Time Information**: Latest news, releases, and trends about artists
  - **Multiple Sources**: Combine Wikipedia, Google KG, and web search for richer context
  - **Answer Boxes**: Quick facts and definitions for artists/genres
  - **Related Questions**: "People also ask" questions for deeper insights
- **Implementation**: Add as third search function in OpenAI function calling (alongside Wikipedia and Google KG)
- **Benefits**: 
  - More comprehensive research in one call
  - Can potentially replace Google Knowledge Graph API
  - Better context for OpenAI to generate descriptions
  - Real-time information about artists

## 📊 Analytics & Insights APIs

### 8. **Enhanced Analytics**
- **APIs**:
  - **Last.fm API**: Scrobbling data, listening history, statistics
  - **MusicBrainz API**: Release dates, label information
- **Feature Ideas**:
  - **Listening Timeline**: "Your music journey over time"
  - **Decade Analysis**: "Your favorite music by decade"
  - **Label Analysis**: "Artists from your favorite record labels"
  - **Release Year Trends**: "When did you discover most of your music?"

### 9. **Social & Community Features**
- **APIs**:
  - **Last.fm API**: User profiles, friends, groups
  - **Discogs API**: Community ratings and reviews
- **Feature Ideas**:
  - **Music Friends**: Connect with users with similar taste
  - **Community Ratings**: See how others rate your favorite tracks
  - **Group Playlists**: Collaborative playlists with friends
  - **Music Challenges**: "Listen to 10 new artists this week"

## 🗺️ Geographic & Location APIs

### 10. **Enhanced Music Taste Map**
- **APIs**:
  - **Last.fm API**: Geographic listening data
  - **MusicBrainz API**: Artist origin locations
- **Feature Ideas**:
  - **Artist Origins Map**: "Where your favorite artists are from"
  - **Regional Music Discovery**: "Popular in your city vs. globally"
  - **Cultural Music Map**: Explore music by geographic region
  - **Tour Dates Integration**: Show when artists are touring near you

## 🎤 Audio Features

### 10. **Audio Analysis**
- **APIs**:
  - **Echonest API** (deprecated, but concepts apply): Audio features, tempo, key, energy
  - **Spotify Web API** (OAuth): Audio features, track analysis
- **Feature Ideas**:
  - **Audio Feature Visualization**: Show tempo, energy, danceability
  - **Mood-Based Playlists**: "High energy tracks" or "Chill vibes"
  - **BPM Analysis**: "Your preferred tempo range"
  - **Key & Scale Analysis**: "Your favorite musical keys"

## 📱 Integration Features

### 12. **Multi-Platform Integration**
- **APIs**:
  - **Spotify Web API** (OAuth): Already planned
  - **Apple Music API** (OAuth): For future expansion
  - **YouTube Data API**: Video integration
- **Feature Ideas**:
  - **Cross-Platform Analytics**: Compare SoundCloud vs. Spotify listening
  - **Universal Playlists**: Create playlists that work across platforms
  - **Platform-Specific Insights**: "You listen more on SoundCloud for X genre"

## 🎯 High-Priority Recommendations

Based on SoundWrapped's current features, here are the **most valuable additions**:

### **1. SerpAPI Web Search Integration (High Impact, Medium Implementation)**
- **Why**: Significantly enhances artist/genre description generation
- **Benefits**: 
  - Comprehensive research in one API call (knowledge graph + answer boxes + organic results)
  - Better for obscure artists/genres
  - Real-time information
  - Can potentially replace Google Knowledge Graph API
- **Implementation**: Add as third search function in OpenAI function calling
- **Cost**: Free tier (250 searches/month) covers ~125 days of Artist/Genre of the Day
- **See**: `docs/SERPAPI_INTEGRATION.md` for detailed implementation plan

### **2. Lyrics Integration (High Impact, Easy Implementation)**
- Use **Lyrics.ovh** (no auth required) for quick lyrics display
- Add lyrics to "Song of the Day" section
- Enhance user engagement with sing-along features

### **3. Enhanced Artist Profiles (High Impact, Medium Implementation)**
- Integrate **TheAudioDB API** for rich artist data:
  - Album artwork
  - Discography
  - Music videos
  - Professional biographies
- Enhance "Artist of the Day" with visual content

### **4. Similar Artists Discovery (High Impact, Medium Implementation)**
- Use **Last.fm API** for similar artist recommendations
- Add "Similar Artists" section to artist profiles
- Enhance music discovery experience

### **5. Music Charts & Trends (Medium Impact, Easy Implementation)**
- Integrate **Last.fm API** for global charts
- Compare user taste to global trends
- Add "Trending Worldwide" section

### **6. Album Artwork Enhancement (Low Impact, Easy Implementation)**
- Use **TheAudioDB API** or **MusicBrainz API** for high-quality artwork
- Improve visual appeal of track cards
- Add album art to "Song of the Day"

## 🔧 Implementation Considerations

### API Key Management
- Most APIs require API keys (free tiers available)
- Add to `.env` file and `application.yml`
- Update `.envs.example` with placeholder keys

### Rate Limiting
- Most free APIs have rate limits
- Implement caching for frequently accessed data
- Use existing time-seed caching strategy

### Fallback Strategies
- Always have fallbacks if external APIs fail
- Use existing SoundCloud data as backup
- Graceful degradation for missing data

### Cost Considerations
- Most recommended APIs have free tiers
- Monitor API usage to avoid unexpected costs
- Consider implementing usage limits per user

## 📝 Next Steps

1. **Research API Documentation**: Review official docs for selected APIs
2. **Create API Service Classes**: Follow existing pattern in `SoundWrappedService`
3. **Add Configuration**: Update `application.yml` and `.envs.example`
4. **Implement Features**: Start with high-priority, easy-to-implement features
5. **Add Caching**: Cache API responses to reduce calls and improve performance
6. **Update Frontend**: Add UI components for new features
7. **Test & Deploy**: Comprehensive testing before production

## 🔗 Useful Resources

- [Public APIs Repository](https://github.com/public-apis/public-apis)
- [TheAudioDB API Docs](https://www.theaudiodb.com/api_guide.php)
- [MusicBrainz API Docs](https://musicbrainz.org/doc/MusicBrainz_API)
- [Last.fm API Docs](https://www.last.fm/api)
- [Genius API Docs](https://docs.genius.com/)
- [Lyrics.ovh API](https://lyrics.ovh/)
- [SerpAPI Documentation](https://serpapi.com/use-cases/web-search-api) - Web search with knowledge graph extraction

