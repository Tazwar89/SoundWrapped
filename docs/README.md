# 🎵 SoundWrapped

**Your Music Journey, Beautifully Visualized**

SoundWrapped is a modern web application that provides personalized music insights from SoundCloud and Spotify, inspired by Spotify Wrapped and volt.fm. Discover your music taste patterns, connect with similar listeners worldwide, and get comprehensive analytics about your listening habits.

![SoundWrapped Preview](https://via.placeholder.com/800x400/1e293b/ffffff?text=SoundWrapped+Preview)

## ✨ Features

### 🎯 **Core Features**
- **Personalized Analytics**: Deep insights into your listening habits, top artists, and music trends
- **Music Taste Map**: Discover where people with similar music taste live around the world
- **Spotify Wrapped-Style UI**: Beautiful, interactive summaries with stories and insights
- **Multi-Platform Support**: Connect both SoundCloud and Spotify accounts
- **Real-time Data**: Live updates from your music platforms
- **Responsive Design**: Optimized for desktop, tablet, and mobile devices

### 📊 **Analytics Dashboard**
- Top tracks and artists with interactive charts
- Listening time statistics and trends
- Genre analysis and music taste evolution
- Social engagement metrics (likes, reposts, shares)
- Year-over-year comparisons

### 🗺️ **Music Taste Mapping**
- Interactive world map showing similar listeners
- City-by-city music taste similarity scores
- Top genres by location
- Global music taste trends and patterns

### 🎨 **Beautiful UI/UX**
- Modern, dark theme with gradient accents
- Smooth animations and micro-interactions
- Glass morphism design elements
- Mobile-first responsive design
- Accessibility-focused components

## 🚀 Tech Stack

### **Frontend**
- **React 18** with TypeScript
- **Vite** for fast development and building
- **Tailwind CSS** for styling
- **Framer Motion** for animations
- **Recharts** for data visualization
- **React Router** for navigation
- **Axios** for API communication

### **Backend**
- **Spring Boot 3.5.5** with Java 17
- **Spring Data JPA** for database operations
- **PostgreSQL** for data persistence
- **Spring Security** for authentication
- **RESTful API** design

### **DevOps & Deployment**
- **Docker** & **Docker Compose** for containerization
- **GitHub Actions** for CI/CD
- **Nginx** for reverse proxy
- **Testcontainers** for integration testing

## 🏗️ Project Structure

```
SoundWrapped/
├── frontend/                 # React frontend application
│   ├── src/
│   │   ├── components/      # Reusable UI components
│   │   ├── contexts/        # React context providers
│   │   ├── pages/          # Page components
│   │   ├── services/       # API services
│   │   └── utils/          # Utility functions
│   ├── public/             # Static assets
│   └── Dockerfile          # Frontend container config
├── backend/                 # Spring Boot backend
│   └── soundwrapped-backend/
│       ├── src/main/java/  # Java source code
│       ├── src/test/       # Test files
│       └── Dockerfile      # Backend container config
├── bruno/                  # API testing collection
├── docs/                   # Documentation
└── docker-compose.yml      # Multi-container setup
```

## 🚀 Quick Start

### Prerequisites
- **Node.js** 18+ and **npm**
- **Java** 17+
- **Maven** 3.6+
- **Docker** and **Docker Compose**
- **PostgreSQL** 15+

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/SoundWrapped.git
cd SoundWrapped
```

### 2. Backend Setup
```bash
cd backend/soundwrapped-backend

# Create .env file
cp .env.example .env
# Edit .env with your database credentials

# Run with Maven
mvn spring-boot:run

# Or with Docker
docker-compose up backend
```

### 3. Frontend Setup
```bash
cd frontend

# Install dependencies
npm install

# Create environment file
cp .env.example .env
# Edit .env with your API configuration

# Start development server
npm run dev
```

### 4. Full Stack with Docker
```bash
# From project root
docker-compose up --build
```

## 🔧 Configuration

### Environment Variables

#### Backend (.env)
```env
# Database
POSTGRES_DB=soundwrapped
POSTGRES_USER=soundwrapped_user
POSTGRES_PASSWORD=your_secure_password

# SoundCloud API
SOUNDCLOUD_CLIENT_ID=your_client_id
SOUNDCLOUD_CLIENT_SECRET=your_client_secret

# Spring Configuration
SPRING_PROFILES_ACTIVE=docker
```

#### Frontend (.env)
```env
# API Configuration
VITE_API_BASE_URL=http://localhost:8081/api

# SoundCloud Configuration
VITE_SOUNDCLOUD_CLIENT_ID=your_client_id

# Spotify Configuration
VITE_SPOTIFY_CLIENT_ID=your_spotify_client_id
VITE_SPOTIFY_REDIRECT_URI=http://localhost:3000/callback/spotify
```

## 🎨 UI/UX Features

### Design System
- **Color Palette**: Primary (blue), SoundCloud (purple), Spotify (green)
- **Typography**: Inter (body), Poppins (headings)
- **Components**: Glass morphism cards, gradient buttons, animated charts
- **Responsive**: Mobile-first design with breakpoints

### Key Pages
1. **Homepage**: Hero section with platform connections
2. **Dashboard**: Analytics overview with charts and stats
3. **Wrapped**: Spotify Wrapped-style summary with stories
4. **Music Taste Map**: Interactive world map of similar listeners
5. **Profile**: User profile and account management

## 🧪 Testing

### Backend Tests
```bash
cd backend/soundwrapped-backend
mvn test                    # Unit tests
mvn verify                  # Integration tests
```

### Frontend Tests
```bash
cd frontend
npm test                    # Unit tests
npm run test:coverage      # Coverage report
```

### E2E Tests
```bash
# Run full application
docker-compose up

# Run E2E tests
npm run test:e2e
```

## 📊 API Documentation

### SoundCloud Endpoints
- `GET /api/soundcloud/profile` - User profile
- `GET /api/soundcloud/tracks` - User tracks
- `GET /api/soundcloud/likes` - Liked tracks
- `GET /api/soundcloud/playlists` - User playlists
- `GET /api/soundcloud/wrapped/full` - Complete wrapped data

### OAuth Flow
1. User clicks "Connect SoundCloud"
2. Redirects to SoundCloud OAuth
3. User authorizes application
4. Callback exchanges code for tokens
5. Tokens stored in database
6. User redirected to dashboard

## 🚀 Deployment

### Production Build
```bash
# Frontend
cd frontend
npm run build

# Backend
cd backend/soundwrapped-backend
mvn clean package
```

### Docker Deployment
```bash
# Build and push images
docker-compose -f docker-compose.prod.yml up --build

# Or deploy to cloud platform
docker-compose -f docker-compose.prod.yml push
```

### Environment Setup
1. Set up PostgreSQL database
2. Configure environment variables
3. Set up reverse proxy (Nginx)
4. Configure SSL certificates
5. Set up monitoring and logging

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow TypeScript best practices
- Write comprehensive tests
- Use conventional commit messages
- Ensure responsive design
- Test across different browsers

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Spotify Wrapped** for design inspiration
- **volt.fm** for analytics concepts
- **SoundCloud API** for music data
- **Spring Boot** and **React** communities
- **Tailwind CSS** for styling framework

## 📞 Support

- **Documentation**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/your-username/SoundWrapped/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-username/SoundWrapped/discussions)
- **Email**: hello@soundwrapped.com

---

**Made with ❤️ for music lovers**

*Discover your music story with SoundWrapped*