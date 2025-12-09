# 🎵 SoundWrapped

A comprehensive music analytics platform that provides personalized insights from SoundCloud, inspired by Spotify Wrapped. SoundWrapped offers daily featured content, detailed analytics, and interactive visualizations to help users discover and understand their music taste.

## ✨ Features

### 🏠 Homepage - Daily Featured Content

The homepage showcases three daily rotating features that persist throughout the day using time-seed based caching:

#### 🎵 Song of the Day
- **Feature**: Displays a featured track selected from popular SoundCloud tracks
- **Technical Implementation**: 
  - Uses date-based seed (`LocalDate.now().toEpochDay()`) for deterministic daily selection
  - Selects from top 10 popular tracks using the seed
  - Cached for 24 hours to ensure consistency
  - Embeds SoundCloud player for direct playback

#### 🎤 Artist of the Day
- **Feature**: Highlights a featured artist with their popular tracks and biography
- **Technical Implementation**:
  - **Artist Selection**: Extracts unique artists from popular tracks, calculates trending scores, and uses time-seed to select from top 10 artists
  - **Description Generation**: Prioritizes external sources for verified information:
    1. **Wikipedia API** (`/api/rest_v1/page/summary/`): Fetches full extract paragraph from Wikipedia articles
    2. **Google Knowledge Graph API**: Retrieves detailed descriptions from Google's Knowledge Graph
    3. **SoundCloud Bio**: Falls back to artist's SoundCloud description if substantial
    4. **Generated Description**: Creates AI-style description as final fallback
  - **Verification Criteria**: Only generates descriptions for artists with:
    - ≥10,000 followers, OR
    - Wikipedia entry, OR
    - Google Knowledge Graph entry
  - **Track Fetching**: Uses multiple fallback strategies:
    - Attempts `popular-tracks` URL resolution
    - Falls back to direct user ID track fetching
    - Fetches at least 200 tracks before sorting by `playback_count` to ensure accurate popularity
  - **Name Matching**: Tries multiple name variations (case-insensitive, camelCase) to find Wikipedia pages
  - Cached for 24 hours using date-based seed

#### 🎸 Genre of the Day
- **Feature**: Features a music genre with popular tracks and description
- **Technical Implementation**:
  - **Genre Selection**: Randomly selects from 18 popular genres using time-seed
  - **Description Generation**:
    1. **Google Knowledge Graph API**: First attempts to fetch genre description (supports obscure subgenres like "indietronica", "wave", "future garage")
    2. **Hardcoded Descriptions**: Falls back to curated descriptions for well-known genres
    3. **Generic Fallback**: Provides default description if genre not found
  - **Track Filtering**: Fetches tracks using `/tracks?tags={genre}` endpoint, filters for:
    - English titles only
    - Genre tag must be present in track tags
  - Cached for 24 hours using date-based seed

#### 🔥 Popular Now
- **Feature**: Displays the first 5 tracks from the US Top 50 charts playlist
- **Technical Implementation**:
  - Fetches tracks from SoundCloud playlist ID `1714689261` (US Top 50: `https://soundcloud.com/music-charts-us/sets/all-music-genres`)
  - Returns tracks in their original playlist order (no sorting) to show the actual top 5
  - Uses `/playlists/{id}/tracks` endpoint with pagination support

### 📊 Dashboard Analytics

Comprehensive analytics dashboard showing:
- **Top Tracks**: User's most played tracks
- **Top Artists**: Most listened-to artists
- **Listening Statistics**: Total hours, likes given, tracks uploaded
- **Activity Timeline**: Recent likes, uploads, and follows
- **Interactive Charts**: Visual representations using Chart.js

### 🎁 SoundCloud Wrapped

A Spotify Wrapped-style summary featuring:
- **Personalized Stories**: Slide-by-slide presentation of music insights
- **Top Tracks & Artists**: Year-end summary of favorites
- **Statistics**: Total listening hours, likes, uploads, comments
- **Fun Facts**: Interesting insights about listening habits
- **Peak Year Analysis**: Identifies the year with most activity
- **Global Taste Comparison**: Compares user's taste to global trends

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

### 📱 Browser Extension

Chrome extension for system-level playback tracking:
- **Background Tracking**: Monitors audio playback across tabs
- **Automatic Sync**: Syncs playback data with backend
- **Activity Logging**: Tracks listening history and patterns

## 🏗️ Technical Architecture

### Backend (Spring Boot + Java)

#### API Integration
- **SoundCloud API**: Full OAuth2 integration with automatic token refresh
- **Wikipedia API**: REST API (`/api/rest_v1/page/summary/`) for artist biographies
- **Google Knowledge Graph API**: Entity search API for descriptions and genre information
- **Token Management**: Automatic refresh, secure storage in H2/PostgreSQL database

#### Caching Strategy
- **Time-Seed Based Caching**: Uses `LocalDate.now().toEpochDay()` as seed for `Random` class
- **Daily Persistence**: Featured content (Song, Artist, Genre) cached for 24 hours
- **Cache Invalidation**: Cleared on application startup via `@PostConstruct`

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
- **Custom Hooks**: Reusable hooks for data fetching and state management

#### Components
- **StatCard**: Reusable card component for displaying statistics
- **Charts**: Interactive charts using Chart.js
- **Animated Background**: WebGL and particle-based backgrounds
- **Responsive Design**: Mobile-first approach with breakpoints

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
docker-compose up --build
```

## 🔧 Configuration

### Backend (`application.yml`)

```yaml
soundcloud:
  client-id: YOUR_SOUNDCLOUD_CLIENT_ID
  client-secret: YOUR_SOUNDCLOUD_CLIENT_SECRET
  api:
    base-url: https://api.soundcloud.com

google:
  knowledge-graph:
    api-key: YOUR_GOOGLE_KNOWLEDGE_GRAPH_API_KEY
```

### Frontend (`.env`)

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_SOUNDCLOUD_CLIENT_ID=YOUR_SOUNDCLOUD_CLIENT_ID
```

## 📡 API Endpoints

### User Data
- `GET /api/soundcloud/profile` - User profile
- `GET /api/soundcloud/tracks` - User tracks
- `GET /api/soundcloud/likes` - Liked tracks
- `GET /api/soundcloud/playlists` - User playlists
- `GET /api/soundcloud/followers` - User followers

### Featured Content
- `GET /api/soundcloud/featured/track` - Song of the Day
- `GET /api/soundcloud/featured/artist` - Artist of the Day
- `GET /api/soundcloud/featured/genre` - Genre of the Day
- `GET /api/soundcloud/popular/tracks?limit=5` - Popular Now (first 5 from Top 50)

### Analytics
- `GET /api/soundcloud/wrapped/full` - Complete Wrapped summary
- `GET /api/soundcloud/dashboard/analytics` - Dashboard analytics
- `GET /api/soundcloud/music-doppelganger` - Music taste matching
- `GET /api/soundcloud/artist/analytics` - Artist performance metrics
- `GET /api/soundcloud/music-taste-map` - Geographic taste visualization
- `GET /api/soundcloud/recent-activity?limit=10` - Recent activity feed

### Authentication
- `GET /callback?code={auth_code}` - OAuth2 callback
- `POST /api/soundcloud/refresh-token` - Manual token refresh

## 🔐 Authentication Flow

1. User clicks "Connect SoundCloud" on homepage
2. Redirected to SoundCloud OAuth2 authorization page
3. User authorizes application
4. SoundCloud redirects to `/callback?code={auth_code}`
5. Backend exchanges code for access and refresh tokens
6. Tokens stored securely in database
7. Automatic token refresh when access token expires

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
│       │   ├── controller/     # REST controllers
│       │   ├── service/         # Business logic
│       │   ├── entity/          # JPA entities
│       │   ├── repository/      # Data access
│       │   └── exception/       # Custom exceptions
│       └── src/main/resources/
│           └── application.yml  # Configuration
├── frontend/
│   └── src/
│       ├── components/          # React components
│       ├── pages/               # Page components
│       ├── contexts/            # React contexts
│       ├── services/            # API services
│       └── utils/               # Utility functions
├── browser-extension/           # Chrome extension
└── docs/                        # Documentation
```

## 🛠️ Technologies Used

### Backend
- **Spring Boot 3.5.5**: Application framework
- **Spring Data JPA**: Database abstraction
- **H2/PostgreSQL**: Database
- **RestTemplate**: HTTP client for API calls
- **Maven**: Build tool

### Frontend
- **React 18**: UI library
- **TypeScript**: Type-safe JavaScript
- **Vite**: Build tool
- **Tailwind CSS**: Styling
- **Framer Motion**: Animations
- **Chart.js**: Data visualization
- **React Router**: Navigation

### External APIs
- **SoundCloud API**: Music data and authentication
- **Wikipedia REST API**: Artist biographies
- **Google Knowledge Graph API**: Entity descriptions

## 📝 License

See [LICENSE](LICENSE) file for details.

## 👤 Author

**Tazwar Sikder**

## 🙏 Acknowledgments

- Inspired by Spotify Wrapped and volt.fm
- SoundCloud API for music data
- Wikipedia and Google Knowledge Graph for rich descriptions

