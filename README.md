# 🎵 SoundWrapped

A comprehensive music analytics platform that provides personalized insights from SoundCloud, inspired by Spotify Wrapped. SoundWrapped offers daily featured content, detailed analytics, and interactive visualizations to help users discover and understand their music taste.

## ✨ Features

### 🏠 Homepage - Daily Featured Content

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
  - Embeds SoundCloud player for direct playback

#### 🎤 Artist of the Day
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
- **Feature**: Displays the first 5 tracks from the US Top 50 charts playlist
- **Technical Implementation**:
  - Fetches tracks from SoundCloud playlist URN `1714689261` (US Top 50: `https://soundcloud.com/music-charts-us/sets/all-music-genres`)
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

#### Phase 1 Features ✨
- **Shareable Story Cards**: Download high-quality 9:16 aspect ratio cards for Instagram/TikTok stories with multiple card types (Summary, Listening, Top Track, Top Artist, Underground, Trendsetter, Repost, Archetype)
- **Support the Underground**: Calculates and displays the percentage of listening time spent on artists with fewer than 5,000 followers
- **Year in Review Poetry**: AI-generated personalized poems celebrating the user's musical journey using their top tracks and genres

#### Phase 2 Features 🚀
- **The Trendsetter (Early Adopter) Score**: Measures how early users discovered tracks compared to when they were created, with badges (Visionary, Trendsetter, Early Adopter, Explorer, Listener)
- **The Repost King/Queen**: Tracks how many reposted tracks became trending, with success rate and badges (Repost Royalty, Repost King/Queen, Repost Enthusiast, Repost Supporter)
- **The Sonic Archetype**: AI-generated musical persona (e.g., "The 3 AM Lo-Fi Scholar", "The High-Octane Bass Hunter") based on listening patterns, genres, and artists

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

Automatic tracking via Last.fm scrobbling (replaces browser extension):
- **Cross-Browser Support**: Works on Chrome, Firefox, Safari, Edge via [Web Scrobbler](https://webscrobbler.com) extension
- **Automatic Syncing**: Polls Last.fm API every 15 minutes to sync scrobbles
- **OAuth Integration**: Secure Last.fm authentication flow
- **Track Matching**: Automatically matches Last.fm scrobbles to SoundCloud tracks
- **User Dashboard**: Connection status and manual sync trigger in Dashboard

## 🏗️ Technical Architecture

SoundWrapped follows a **Model-View-Controller (MVC)** architectural pattern, providing clear separation of concerns and maintainable code structure.

### Architecture Pattern: Model-View-Controller (MVC)

#### **Model Layer** (Data & Business Logic)
- **Entities** (`entity/`): JPA entities representing database tables (e.g., `Token`, `UserActivity`)
- **Repositories** (`repository/`): Data access layer using Spring Data JPA for database operations
- **Services** (`service/`): Business logic layer containing core functionality:
  - `SoundWrappedService`: Main service for SoundCloud API integration
  - `AnalyticsService`: Music analytics and statistics calculations
  - `MusicDoppelgangerService`: Music taste matching algorithms
  - `ArtistAnalyticsService`: Artist performance metrics
  - `MusicTasteMapService`: Geographic taste visualization
  - `TokenStore`: OAuth2 token management

#### **View Layer** (Frontend Presentation)
- **React Components** (`frontend/src/components/`): Reusable UI components
- **Pages** (`frontend/src/pages/`): Main application pages (Home, Dashboard, Wrapped, etc.)
- **Contexts** (`frontend/src/contexts/`): React Context API for state management
- **Services** (`frontend/src/services/`): API client services for backend communication

#### **Controller Layer** (Request Handling)
- **REST Controllers** (`controller/`): Spring Boot `@RestController` classes handling HTTP requests:
  - `SoundWrappedController`: Main API endpoints for music data
  - `OAuthCallbackController`: OAuth2 authentication flow
  - `LastFmController`: Last.fm OAuth and scrobbling management
  - `LastFmScrobblingService`: Syncs Last.fm scrobbles to UserActivity database
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
- **ShareableStoryCard**: Downloadable story cards for social media (9:16 aspect ratio, multiple card types)
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
SOUNDCLOUD_CLIENT_ID=your_soundcloud_client_id_here
SOUNDCLOUD_CLIENT_SECRET=your_soundcloud_client_secret_here
GOOGLE_KNOWLEDGE_GRAPH_API_KEY=your_google_api_key_here
GROQ_API_KEY=your_groq_api_key_here
SERPAPI_API_KEY=your_serpapi_key_here  # Optional
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
│       │   ├── controller/      # REST controllers
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
└── docs/                        # Documentation
```

## 🛠️ Technologies Used

### Backend
- **Spring Boot 3.5.5**: Application framework
- **Spring Data JPA**: Database abstraction
- **H2/PostgreSQL**: Database (H2 for local dev, PostgreSQL for production/CI)
- **RestTemplate**: HTTP client for API calls
- **Maven**: Build tool
- **Docker**: Containerization with multi-stage builds

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
- **Groq API**: AI-powered descriptions and poetry generation (free tier)
- **SerpAPI**: Web search for additional context (optional)

## 📝 License

See [LICENSE](LICENSE) file for details.

## 👤 Author

**Tazwar Sikder**

## 🙏 Acknowledgments

- Inspired by Spotify Wrapped, SoundCloud Playback 2025, and volt.fm
- SoundCloud API for music data
- Wikipedia and Google Knowledge Graph for rich descriptions
- Groq API for AI-powered content generation (free tier)
- SerpAPI for comprehensive web search

## 📚 Additional Documentation

- [Phase 1 & 2 Features](docs/PHASE_1_2_FEATURES.md) - Detailed documentation of Phase 1 & 2 implementations
- [Groq API Implementation](docs/GROQ_IMPLEMENTATION.md) - AI-powered features using Groq
- [Phase 1 & 2 Implementation](docs/PHASE_1_2_IMPLEMENTATION.md) - Original Phase 1 & 2 implementation notes
- [OpenAI Implementation](docs/OPENAI_IMPLEMENTATION.md) - Legacy OpenAI documentation (migrated to Groq)

