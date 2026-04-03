# 🎵 [SoundWrapped](https://soundwrapped.onrender.com/)

A comprehensive music analytics platform that provides personalized insights from SoundCloud, inspired by Spotify Wrapped. SoundWrapped offers daily featured content, detailed analytics, and interactive visualizations to help users discover and understand their music taste.

## ✨ Features

### 🏠 Homepage - Daily Featured Content
![home](https://github.com/user-attachments/assets/df0bfcb0-3a8a-4ad2-982a-1766367fe507)

The homepage showcases three daily rotating features that persist throughout the day using time-seed based caching:

#### 🎵 Song of the Day
- **Feature**: Displays a featured track selected from popular SoundCloud tracks
- **Technical Implementation**: 
  - **Selection Algorithm** (prioritized order):
    1. **Discovery Tracks**: High engagement-to-plays ratio tracks (1000+ plays, sorted by engagement)
    2. **Popular Tracks (Positions 11-30)**: Avoids overlap with "Popular Now" section
    3. **Genre of the Day Tracks**: Fallback to tracks from featured genre
  - Uses date-based seed (`LocalDate.now().toEpochDay()`) for deterministic daily selection
  - Cached for 24 hours to ensure consistency

#### 🎤 Artist of the Day
<img width="1149" height="516" alt="artist of the day" src="https://github.com/user-attachments/assets/3993eda9-20e3-43b3-a537-0ed82b3657a8" />

- **Feature**: Highlights a featured artist with their popular tracks and biography
- **Technical Implementation**:
  - **Artist Selection**: Extracts unique artists from popular tracks, calculates trending scores, and uses time-seed to select from top 10 artists
  - **Description Generation**: Research-first approach with AI synthesis:
    1. **Research Phase**: Aggregates data from multiple sources:
       - **Wikipedia API** (`/api/rest_v1/page/summary/`): Fetches full extract paragraph from Wikipedia articles
       - **Google Knowledge Graph API**: Retrieves detailed descriptions from Google's Knowledge Graph
       - **SerpAPI**: Comprehensive web search for additional context (optional)
    2. **AI Generation**: Uses Groq API (`llama-3.3-70b-versatile`) to synthesize research into 2-3 sentence description (50-100 words)
    3. **Fallback**: SoundCloud bio or generic description if AI generation fails
  - **Verification Criteria**: More lenient - attempts description generation for all artists, with quality checks
  - **Track Fetching**: Uses multiple fallback strategies:
    - Attempts `popular-tracks` URL resolution
    - Falls back to direct user URN track fetching
    - Fetches at least 200 tracks before sorting by `playback_count` to ensure accurate popularity
  - **Name Matching**: Tries multiple name variations (case-insensitive, camelCase) to find Wikipedia pages
  - Cached for 24 hours using date-based seed

#### 🎸 Genre of the Day
<img width="1147" height="508" alt="genre of the day" src="https://github.com/user-attachments/assets/1ecf2b0b-01b1-4ab4-9cd9-adaa71fe6269" />

- **Feature**: Features a music genre with popular tracks and description
- **Technical Implementation**:
  - **Genre Selection**: Randomly selects from 18 popular genres using time-seed
  - **Description Generation**:
    1. **Research Phase**: Aggregates data from multiple sources:
       - **Google Knowledge Graph API**: Fetches genre description (supports obscure subgenres)
       - **Wikipedia API**: Attempts to find genre information
       - **SerpAPI**: Web search for additional context (optional)
    2. **AI Generation**: Uses Groq API to synthesize research into 2-3 sentence description (50-100 words)
    3. **Fallback**: Hardcoded descriptions for well-known genres or generic description
  - **Track Filtering**: Fetches tracks using `/tracks?tags={genre}` endpoint with flexible matching:
    - Partial tag matching (e.g., "country music" matches "country")
    - English titles preferred
  - Cached for 24 hours using date-based seed

#### 🔥 Popular Now
<img width="1149" height="480" alt="popular now" src="https://github.com/user-attachments/assets/1ee63981-0fd5-4144-882a-48efb3161952" />

- **Feature**: Displays the first 5 tracks from the US Top 50 charts playlist
- **Technical Implementation**:
  - Fetches tracks from SoundCloud playlist URN `1714689261` (US Top 50: `https://soundcloud.com/music-charts-us/sets/all-music-genres`)
  - Returns tracks in their original playlist order (no sorting) to show the actual top 5
  - Uses `/playlists/{id}/tracks` endpoint with pagination support

#### 🐝 Buzzing
- **Feature**: Highlights an up-and-coming artist/track daily from SoundCloud's buzzing playlists
- **Technical Implementation**:
  - Fetches playlists from SoundCloud user `buzzing-playlists` (`/users/buzzing-playlists/playlists?limit=50`)
  - Aggregates all tracks across all buzzing playlists into a single pool
  - Uses date-based seed (`year*10000 + month*100 + day`) with `java.util.Random` for deterministic daily selection
  - Same track shown all day, changes at midnight
  - Labels track with "Artist to watch out for"
  - Manual in-memory field caching (not Caffeine-managed)

### 📊 Dashboard Analytics

Comprehensive analytics dashboard showing:
- **Top Tracks**: User's most played tracks
- **Top Artists**: Most listened-to artists
- **Listening Statistics**: Total hours, likes given, tracks uploaded
- **Activity Timeline**: Recent likes, uploads, and follows
- **Interactive Charts**: Visual representations using Chart.js
- **Genre Discovery**: Top genres explored with discovery count
- **Listening Patterns**: Peak hours, peak days, and listening persona (Early Bird, Afternoon Listener, Evening Vibes, Night Owl)
- **Genre Constellation**: Interactive 3D visualization of genre relationships (HTML5 Canvas)

### 🎁 SoundCloud Wrapped

A Spotify Wrapped-style summary featuring:
- **Personalized Stories**: Slide-by-slide presentation of music insights
- **Top Tracks & Artists**: Year-end summary of favorites
- **Statistics**: Total listening hours, likes, uploads, comments
- **Fun Facts**: Interesting insights about listening habits
- **Peak Year Analysis**: Identifies the year with most activity
- **Global Taste Comparison**: Compares user's taste to global trends

#### Phase 1 Features ✨
- **Shareable Story Cards**: Download high-quality 9:16 aspect ratio cards for Instagram/TikTok stories with multiple card types (Summary, Listening, Top Track, Top Artist, Underground, Trendsetter, Repost, Archetype)
- **Support the Underground**: Calculates and displays the percentage of listening time spent on artists with fewer than 5,000 followers
- **Year in Review Poetry**: AI-generated personalized poems celebrating the user's musical journey using their top tracks and genres

#### Phase 2 Features 🚀
- **The Trendsetter (Early Adopter) Score**: Measures how early users discovered tracks compared to when they were created, with badges (Visionary, Trendsetter, Early Adopter, Explorer, Listener)
- **The Repost King/Queen**: Tracks how many reposted tracks became trending, with success rate and badges (Repost Royalty, Repost King/Queen, Repost Enthusiast, Repost Supporter)
- **The Sonic Archetype**: AI-generated musical persona (e.g., "The 3 AM Lo-Fi Scholar", "The High-Octane Bass Hunter") based on listening patterns, genres, and artists

#### Phase 3 Features 🎯
- **Lyrics Integration**: Automatic lyrics fetching for Song of the Day via Lyrics.ovh (no auth required), with collapsible display
- **Enhanced Artist Profiles**: Rich artist information from TheAudioDB — high-quality artwork, discography, biographies, music videos
- **Similar Artists Discovery**: Last.fm-powered "If you like X, you might like Y" recommendations with match scores
- **Interactive Genre Constellation**: 3D Canvas visualization of genre relationships with hover/click interaction, node sizes based on listening time
- **Dynamic Mood Background**: WebGL background colors adapt to track energy (high → reds/oranges, medium → warm oranges, low → blues/purples)
- **Story Card Customization**: 6 color themes (Orange, Blue, Purple, Green, Red, Pink), 3 font sizes, live preview for all 8 card types
- **Error Boundary & Retry**: React error boundaries for graceful crash handling, exponential backoff retry hook for failed API calls
- **Caffeine Caching**: In-memory caching for Groq descriptions (1h), enhanced artists (24h), similar artists (12h), lyrics (7d), popular tracks (30m), SoundCloud track search (24h)

### 🗺️ Music Taste Map

Interactive world map visualization showing:
- **Similar Listeners by City**: Geographic distribution of similar music tastes
- **Similarity Scoring**: Advanced algorithms to match preferences
- **Top Genres by Location**: Genre analysis for each city
- **Interactive Visualization**: Click cities to see detailed insights

### 👥 Music Doppelgänger

Finds users with similar music taste by:
- Analyzing liked tracks and playlists
- Comparing genre preferences
- Matching with followed users
- Calculating similarity scores

### 🎨 Artist Analytics

For users who upload tracks:
- **Track Performance**: Playback counts, likes, reposts
- **Audience Insights**: Follower growth, engagement metrics
- **Top Performing Tracks**: Best performing uploads
- **Recommendations**: Artist recommendations based on track analysis

### 🎵 Last.fm Scrobbling Integration
<img width="1280" height="535" alt="last fm connection" src="https://github.com/user-attachments/assets/807546d7-ede8-4789-a577-b74b35687cd3" />

Integrates with Last.fm via Web Auth OAuth to pull long-term listening history beyond SoundCloud limits.

**How It Works:**
1. User installs [Web Scrobbler](https://webscrobbler.com) browser extension (SoundCloud is a built-in connector)
2. Web Scrobbler sends play events to Last.fm: `SoundCloud Play → Web Scrobbler → Last.fm`
3. User connects Last.fm account in SoundWrapped Dashboard (OAuth Web Auth flow)
4. Backend polls Last.fm API every 15 minutes for new scrobbles
5. Scrobbles are fuzzy-matched to SoundCloud tracks and stored as `UserActivity` records

**Architecture:**
- `LastFmService`: Consolidated REST API client — handles auth URL generation, token exchange, session management, API signature generation, and recent tracks fetching
- `LastFmScrobblingService`: Scheduled sync job (every 15 min) — polls `user.getRecentTracks`, fuzzy-matches to SoundCloud tracks via search, creates `UserActivity` entries with `source=LASTFM`
- `LastFmController`: REST endpoints for OAuth flow (`/api/lastfm/auth-url`, `/callback`, `/status`, `/disconnect`, `/sync`)
- OAuth uses Web Auth mode (only `api_key` + `cb` callback URL — no request token in auth URL)

**Data Enrichment:**
- `UserActivity` entity tracks `source` (INAPP/LASTFM), `matchedSoundCloudTrackId`, `lastFmArtist`, `lastFmTrack`
- Unmatched Last.fm scrobbles are still stored for analytics
- Uses `soundcloudTrackSearch` Caffeine cache (5000 entries, 24h TTL) to avoid redundant search API calls

## 🏗️ Technical Architecture

SoundWrapped follows a **Model-View-Controller (MVC)** architectural pattern, providing clear separation of concerns and maintainable code structure.

### Architecture Pattern: Model-View-Controller (MVC)

#### **Model Layer** (Data & Business Logic)
- **Entities** (`entity/`): JPA entities representing database tables (e.g., `Token`, `UserActivity`)
- **Repositories** (`repository/`): Data access layer using Spring Data JPA for database operations
- **Services** (`service/`): Business logic layer containing core functionality:
  - `SoundWrappedService`: Main service for SoundCloud API integration, featured content, Wrapped summary, Buzzing track
  - `AnalyticsService`: Music analytics and statistics calculations
  - `GenreAnalysisService`: Genre extraction, normalization, and distribution analysis
  - `ListeningPatternService`: Time-of-day and day-of-week listening analysis
  - `MusicDoppelgangerService`: Music taste matching algorithms
  - `ArtistAnalyticsService`: Artist performance metrics
  - `MusicTasteMapService`: Geographic taste visualization
  - `LyricsService`: Lyrics fetching via Lyrics.ovh API
  - `EnhancedArtistService`: Rich artist profiles via TheAudioDB
  - `SimilarArtistsService`: Similar artists via Last.fm API
  - `LastFmService`: Last.fm REST API client (auth, session, recent tracks, signatures)
  - `LastFmScrobblingService`: Scheduled Last.fm scrobble sync (every 15 min)
  - `ActivityTrackingService`: In-app play/like/repost event tracking
  - `GeolocationService`: IP-based location resolution
  - `UserLocationService`: User location storage and city/country queries
  - `TokenStore`: OAuth2 token management
  - `TokenRefreshScheduler`: Automatic token refresh

#### **View Layer** (Frontend Presentation)
- **React Components** (`frontend/src/components/`): Reusable UI components
- **Pages** (`frontend/src/pages/`): Main application pages (Home, Dashboard, Wrapped, etc.)
- **Contexts** (`frontend/src/contexts/`): React Context API for state management
- **Services** (`frontend/src/services/`): API client services for backend communication

#### **Controller Layer** (Request Handling)
- **REST Controllers** (`controller/`): Spring Boot `@RestController` classes handling HTTP requests:
  - `SoundWrappedController`: Main API endpoints for music data, featured content, analytics, Wrapped
  - `OAuthCallbackController`: SoundCloud OAuth2 code exchange
  - `LastFmController`: Last.fm Web Auth OAuth flow and scrobble management
  - `ActivityTrackingController`: In-app play/like/repost event tracking (`/api/activity`)
  - `SystemPlaybackController`: System-level playback tracking from desktop/extension (`/api/tracking`)
- **Request Mapping**: RESTful endpoints with proper HTTP methods (GET, POST, etc.)
- **Response Handling**: JSON responses with appropriate status codes

#### **Data Flow**
1. **Client Request** → Frontend makes HTTP request to backend API
2. **Controller** → Receives request, validates input, delegates to service layer
3. **Service** → Executes business logic, interacts with repositories/APIs
4. **Repository/API** → Fetches data from database or external APIs (SoundCloud, Wikipedia, Google)
5. **Service** → Processes and transforms data
6. **Controller** → Returns JSON response
7. **View** → Frontend receives data and updates UI

#### **Key Architectural Principles**
- **Separation of Concerns**: Each layer has distinct responsibilities
- **Dependency Injection**: Spring's IoC container manages dependencies
- **RESTful Design**: Stateless API endpoints following REST conventions
- **Service-Oriented**: Business logic encapsulated in service classes
- **Repository Pattern**: Data access abstracted through repository interfaces

### Backend (Spring Boot + Java)

#### API Integration
- **SoundCloud API**: Full OAuth2 integration with automatic token refresh
- **Wikipedia API**: REST API (`/api/rest_v1/page/summary/`) for artist biographies
- **Google Knowledge Graph API**: Entity search API for descriptions and genre information
- **Groq API**: AI-powered description and poetry generation using `llama-3.3-70b-versatile` model (free tier, OpenAI-compatible)
- **SerpAPI**: Comprehensive web search for additional context (optional)
- **Token Management**: Automatic refresh, secure storage in H2/PostgreSQL database

#### Caching Strategy
- **Caffeine In-Memory Cache**: Spring Cache with Caffeine for expensive external API calls:
  - `groqDescriptions`: 1h TTL, 1000 entries (AI-generated descriptions)
  - `enhancedArtists`: 24h TTL, 500 entries (TheAudioDB artist info)
  - `similarArtists`: 12h TTL, 500 entries (Last.fm similar artists)
  - `lyrics`: 7d TTL, 2000 entries (Lyrics.ovh)
  - `popularTracks`: 30m TTL, 10 entries (SoundCloud popular tracks)
  - `soundcloudTrackSearch`: 24h TTL, 5000 entries (Last.fm → SoundCloud track ID mapping)
- **Time-Seed Based Caching**: Uses `LocalDate.now().toEpochDay()` as seed for `Random` class
- **Daily Persistence**: Featured content (Song, Artist, Genre, Buzzing) cached for 24 hours via in-memory fields
- **Cache Invalidation**: Via `POST /api/soundcloud/featured/clear-cache` endpoint or application startup `@PostConstruct`

#### Data Processing
- **Pagination Handling**: Supports SoundCloud's `linked_partitioning` for large datasets
- **Error Handling**: Comprehensive fallback mechanisms for API failures
- **Rate Limiting**: Respects SoundCloud API rate limits with retry logic

### Frontend (React + TypeScript)

#### UI Framework
- **React 18**: Modern React with hooks and context API
- **TypeScript**: Full type safety throughout
- **Tailwind CSS**: Utility-first CSS framework
- **Framer Motion**: Smooth animations and transitions

#### State Management
- **React Context**: `AuthContext` for authentication, `MusicDataContext` for music data
- **React Query**: Request deduplication, client-side caching, and prefetching via `useMusicQueries` hooks
- **Custom Hooks**: `useMusicQueries.ts` (data fetching), `useRetry.ts` (exponential backoff)

#### Components
- **StatCard**: Reusable card component for displaying statistics
- **Charts**: Interactive charts using Chart.js (`TopTracksChart`, `TopArtistsChart`)
- **GenreConstellation**: 3D Canvas visualization of genre relationships
- **DynamicMoodBackground**: Energy-reactive WebGL background
- **WebGLBackground**: Configurable WebGL background with dynamic color props
- **ShareableStoryCard**: Downloadable story cards with 6 color themes, 3 font sizes, 8 card types
- **LastFmConnection**: Last.fm connect/disconnect UI with sync status
- **ErrorBoundary**: Graceful React error handling with dev-mode details
- **AnimatedParticleBackground**: Particle-based background animation
- **RecentActivity**: Activity feed component
- **Responsive Design**: Mobile-first approach with breakpoints

#### Pages
- **HomePage**: Landing page with Song/Artist/Genre of the Day, Popular Now, Buzzing
- **DashboardPage**: Authenticated analytics dashboard with genre constellation
- **WrappedPage**: Wrapped summary experience with story slides
- **ProfilePage**: User profile and settings
- **MusicTasteMapPage**: Interactive geographic taste visualization
- **LastFmCallbackPage**: Last.fm OAuth callback handler

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Node.js 18+
- Maven 3.6+
- PostgreSQL 15+ (optional, H2 used by default)

### Backend Setup

```bash
cd backend/soundwrapped-backend

# Configure API keys in application.yml
# - SoundCloud Client ID & Secret
# - Google Knowledge Graph API Key

# Run with Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/soundwrapped-backend-0.0.1-SNAPSHOT.jar
```

### Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Configure environment variables
cp env.example .env
# Edit .env with your API base URL

# Run development server
npm run dev
```

### Docker Setup

```bash
# From project root
# Ensure backend JAR is built first (for integration tests)
cd backend/soundwrapped-backend
./mvnw clean package -DskipTests
cd ../..

# Start all services
docker-compose up --build

# Services will be available at:
# - Frontend: http://localhost:3000
# - Backend: http://localhost:8081
# - Database: localhost:5432
```

**Note**: The Dockerfile uses `eclipse-temurin:17-jre` as the base image. Environment variables should be provided via `docker-compose.yml` or `.env` file, not baked into the image.

## 🔧 Configuration

### Backend Configuration

SoundWrapped supports configuration through both `application.yml` and environment variables. Environment variables take precedence over `application.yml` values.

#### Option 1: Environment Variables (Recommended for Production)

Create a `.env` file in `backend/soundwrapped-backend/`:

```env
# SoundCloud API Configuration
SOUNDCLOUD_CLIENT_ID=your_soundcloud_client_id_here
SOUNDCLOUD_CLIENT_SECRET=your_soundcloud_client_secret_here
REDIRECT_URI=http://localhost:8080/callback

# Database Configuration
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Spring Boot Configuration (PostgreSQL)
SPRING_PROFILES_ACTIVE=default
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false

# Google Knowledge Graph API Configuration (Artist descriptions)
GOOGLE_KNOWLEDGE_GRAPH_API_KEY=your_google_api_key_here

# Groq API Configuration (LLM)
GROQ_API_KEY=your_groq_api_key_here

# SerpAPI Configuration (Web search)
SERPAPI_API_KEY=your_serpapi_api_key_here

# TheAudioDB API Configuration (Enhanced artist info)
THEAUDIODB_API_KEY=your_theaudiodb_api_key_here

# Last.fm API Configuration (Similar artists & scrobbling)
LASTFM_API_KEY=7f8f8a603a92fcb664f136304d5a02e7
LASTFM_API_SECRET=fc6b00d615146ceb667b8e4d9630a788
LASTFM_CALLBACK_URL=http://localhost:8080/api/lastfm/callback
APP_FRONTEND_BASE_URL=http://localhost:3000
VITE_LASTFM_CALLBACK_URL=http://localhost:8080/api/lastfm/callback
```

Then export them or use a tool like `dotenv` to load them.

#### Option 2: `application.yml` (Development)

```yaml
soundcloud:
  client-id: ${SOUNDCLOUD_CLIENT_ID:your_default_client_id}
  client-secret: ${SOUNDCLOUD_CLIENT_SECRET:your_default_client_secret}
  api:
    base-url: https://api.soundcloud.com

google:
  knowledge-graph:
    api-key: ${GOOGLE_KNOWLEDGE_GRAPH_API_KEY:}

groq:
  api-key: ${GROQ_API_KEY:}
  base-url: https://api.groq.com/openai/v1

serpapi:
  api-key: ${SERPAPI_API_KEY:}
```

**Note**: The `${VARIABLE_NAME:default_value}` syntax means "use environment variable if available, otherwise use default value". For production, always use environment variables to keep secrets secure.

### Frontend (`.env`)

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_SOUNDCLOUD_CLIENT_ID=YOUR_SOUNDCLOUD_CLIENT_ID
```

## 📡 API Endpoints

### User Data
- `GET /api/soundcloud/profile` — User profile
- `GET /api/soundcloud/tracks` — User top tracks (by tracked plays)
- `GET /api/soundcloud/likes` — Liked tracks
- `GET /api/soundcloud/playlists` — User playlists
- `GET /api/soundcloud/followers` — User followers

### Featured Content
- `GET /api/soundcloud/featured/track` — Song of the Day (with lyrics if available)
- `GET /api/soundcloud/featured/artist?forceRefresh=` — Artist of the Day (with enhanced info)
- `GET /api/soundcloud/featured/genre` — Genre of the Day
- `GET /api/soundcloud/popular/tracks?limit=4` — Popular Now (from Top 50)
- `GET /api/soundcloud/buzzing` — Buzzing track of the day
- `POST /api/soundcloud/featured/clear-cache` — Clear daily featured cache
- `GET /api/soundcloud/similar-artists?artist=&limit=10` — Similar artists via Last.fm

### Analytics
- `GET /api/soundcloud/wrapped/full` — Complete Wrapped summary
- `GET /api/soundcloud/dashboard/analytics` — Dashboard analytics (genres, listening patterns)
- `GET /api/soundcloud/music-doppelganger` — Music taste matching
- `GET /api/soundcloud/artist/analytics` — Artist performance metrics
- `GET /api/soundcloud/artist/recommendations?trackId=` — Artist recommendations
- `GET /api/soundcloud/music-taste-map` — Geographic taste visualization
- `GET /api/soundcloud/recent-activity?limit=10` — Recent activity feed
- `GET /api/soundcloud/online-users` — Count users active in last 5 min

### Activity Tracking
- `POST /api/activity/track/play?trackId=&durationMs=` — Track play event
- `POST /api/activity/track/like?trackId=` — Track like event
- `POST /api/activity/track/repost?trackId=` — Track repost event
- `POST /api/tracking/system-playback` — System-level playback (desktop/extension)
- `POST /api/tracking/system-like?trackId=` — System-level like event
- `POST /api/tracking/update-location` — IP-based location update

### SoundCloud Authentication
- `GET /callback?code={auth_code}` — SoundCloud OAuth2 callback
- `POST /api/soundcloud/refresh-token` — Proactive token refresh

### Last.fm Integration
- `GET /api/lastfm/auth-url` — Get Last.fm Web Auth authorization URL
- `GET /api/lastfm/callback?token=` — Last.fm OAuth callback (exchanges token for session, redirects to frontend)
- `GET /api/lastfm/callback/test` — Test callback endpoint accessibility
- `GET /api/lastfm/status` — Last.fm connection status
- `POST /api/lastfm/disconnect` — Disconnect Last.fm account
- `POST /api/lastfm/sync` — Manually trigger scrobble sync

### Debug
- `GET /api/soundcloud/debug/test-api` — Test SoundCloud API connection
- `GET /api/soundcloud/debug/tokens` — Token status
- `GET /api/soundcloud/debug/oauth-url` — Generate OAuth URL

## 🔐 Authentication Flows

### SoundCloud OAuth2
1. User clicks "Connect SoundCloud" on homepage
2. Redirected to SoundCloud OAuth2 authorization page
3. User authorizes application
4. SoundCloud redirects to `/callback?code={auth_code}`
5. Backend exchanges code for access and refresh tokens
6. Tokens stored securely in database
7. Automatic token refresh when access token expires (via `TokenRefreshScheduler`)

### Last.fm Web Auth
1. User clicks "Connect Last.fm" on the Dashboard
2. Frontend fetches auth URL from `GET /api/lastfm/auth-url`
3. Auth URL uses Web Auth mode: `https://www.last.fm/api/auth?api_key=KEY&cb=CALLBACK`
4. User authorizes on Last.fm, which redirects to `GET /api/lastfm/callback?token=TOKEN`
5. Backend exchanges token for a session key via `auth.getSession` (with API signature)
6. Session key and username stored in `LastFmToken` entity
7. Scrobble sync begins automatically every 15 minutes

**Note**: Last.fm Web Auth uses only `api_key` + `cb` (callback URL) in the auth URL — no request token is included, which ensures Last.fm uses its Web Auth flow and properly redirects back to the application.

## 🧪 Testing

### Backend Tests
```bash
cd backend/soundwrapped-backend
mvn test                    # Unit tests
mvn verify                  # Integration tests with Testcontainers
```

### Frontend Tests
   ```bash
cd frontend
npm test                    # Unit tests
npm run test:coverage      # Coverage report
```

## 📦 Project Structure

```
SoundWrapped/
├── backend/
│   └── soundwrapped-backend/
│       ├── src/main/java/com/soundwrapped/
│       │   ├── config/          # CacheConfig, etc.
│       │   ├── controller/      # REST controllers (SoundWrapped, LastFm, Activity, SystemPlayback, OAuth)
│       │   ├── service/         # Business logic (16+ services)
│       │   ├── entity/          # JPA entities (Token, UserActivity, LastFmToken, UserLocation)
│       │   ├── repository/      # Data access (Spring Data JPA)
│       │   └── exception/       # Custom exceptions
│       └── src/main/resources/
│           └── application.yml  # Configuration (profiles: default, test, docker)
├── frontend/
│   └── src/
│       ├── components/          # React components (15+ including GenreConstellation, ShareableStoryCard, etc.)
│       ├── pages/               # Page components (Home, Dashboard, Wrapped, Profile, MusicTasteMap, LastFmCallback)
│       ├── contexts/            # React contexts (Auth, MusicData)
│       ├── hooks/               # Custom hooks (useMusicQueries, useRetry)
│       ├── services/            # API client (Axios with interceptors)
│       └── utils/               # Utility functions
├── docs/                        # Documentation
└── docker-compose.yml           # Docker orchestration (frontend, backend, postgres)
```

## 🛠️ Technologies Used

### Backend
- **Spring Boot 3.5.5**: Application framework
- **Spring Data JPA**: Database abstraction
- **Spring Cache + Caffeine**: In-memory caching with configurable TTLs
- **PostgreSQL 18**: Database
- **RestTemplate**: HTTP client for API calls
- **Maven**: Build tool
- **Docker**: Containerization with multi-stage builds

### Frontend
- **React 18**: UI library
- **TypeScript**: Type-safe JavaScript
- **Vite 7.3**: Build tool with HMR
- **Tailwind CSS**: Styling
- **Framer Motion**: Animations
- **Chart.js**: Data visualization
- **React Router**: Navigation
- **React Query**: Server state management, caching, and prefetching
- **html2canvas**: Story card image generation

### External APIs
- **SoundCloud API**: Music data, authentication, and OAuth2
- **Last.fm API**: Similar artists, Web Auth OAuth, scrobble syncing
- **Wikipedia REST API**: Artist biographies
- **Google Knowledge Graph API**: Entity descriptions
- **Groq API**: AI-powered descriptions and poetry generation using `llama-3.3-70b-versatile` (free tier)
- **SerpAPI**: Comprehensive web search for additional context (optional)
- **TheAudioDB API**: Enhanced artist profiles, artwork, discographies (optional)
- **Lyrics.ovh**: Lyrics fetching (free, no auth required)

## 📝 License

See [LICENSE](LICENSE) file for details.

## 👤 Author

**Tazwar Sikder**

## 🙏 Acknowledgments

- Inspired by Spotify Wrapped, SoundCloud Playback 2025, and volt.fm
- SoundCloud API for music data
- Last.fm for scrobbling and similar artists
- Wikipedia and Google Knowledge Graph for rich descriptions
- Groq API for AI-powered content generation (free tier)
- SerpAPI for comprehensive web search
- TheAudioDB for enhanced artist profiles
- Lyrics.ovh for lyrics data

## 📚 Additional Documentation

- [API Documentation](docs/API.md) - Full REST API reference
- [API Keys Setup](docs/API_KEYS_SETUP.md) - How to obtain and configure API keys
- [Features Technical Overview](docs/FEATURES_TECHNICAL_OVERVIEW.md) - Interview-ready feature and architecture guide (includes Groq, Last.fm, SerpAPI details)
- [Performance Analysis](PERFORMANCE_ANALYSIS.md) - Codebase performance analysis and optimizations
- [Phase 1 & 2 Features](docs/PHASE_1_2_FEATURES.md) - Phase 1 & 2 feature implementations
- [Phase 1 & 2 Implementation](docs/PHASE_1_2_IMPLEMENTATION.md) - Phase 1 & 2 implementation notes
- [Phase 3 Complete](docs/PHASE_3_COMPLETE.md) - Phase 3 implementation summary
- [Last.fm Scrobbling](docs/LASTFM_SCROBBLING.md) - Last.fm scrobbling integration guide
- [Last.fm API Setup](docs/LASTFM_API_SETUP.md) - Last.fm API key and callback configuration
- [Deployment](docs/DEPLOYMENT.md) - Deployment guide
