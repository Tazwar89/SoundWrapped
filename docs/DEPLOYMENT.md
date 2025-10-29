# 🚀 SoundWrapped Deployment Guide

This guide covers deploying SoundWrapped to production environments including cloud platforms like Render, Fly.io, and Railway.

## 📋 Prerequisites

- Docker and Docker Compose installed
- Domain name (optional but recommended)
- SSL certificate (for HTTPS)
- Cloud platform account (Render, Fly.io, Railway, etc.)

## 🏗️ Local Development Setup

### 1. Clone and Setup
```bash
git clone https://github.com/your-username/SoundWrapped.git
cd SoundWrapped
```

### 2. Environment Configuration
```bash
# Create environment files
cp .env.example .env
cp frontend/.env.example frontend/.env

# Edit configuration files
nano .env
nano frontend/.env
```

### 3. Start Development Environment
```bash
# Start all services
docker-compose up --build

# Or start individual services
docker-compose up db backend frontend
```

### 4. Access Application
- Frontend: http://localhost:3000
- Backend API: http://localhost:8081
- Database: localhost:5432

## 🌐 Production Deployment

### Option 1: Render.com Deployment

#### Backend Deployment
1. **Create Render Web Service**
   - Connect your GitHub repository
   - Select `backend/soundwrapped-backend` as root directory
   - Build command: `mvn clean package -DskipTests`
   - Start command: `java -jar target/soundwrapped-backend-0.0.1-SNAPSHOT.jar`

2. **Environment Variables**
   ```env
   SPRING_PROFILES_ACTIVE=production
   SPRING_DATASOURCE_URL=jdbc:postgresql://your-db-url
   SPRING_DATASOURCE_USERNAME=your-username
   SPRING_DATASOURCE_PASSWORD=your-password
   SOUNDCLOUD_CLIENT_ID=your-client-id
   SOUNDCLOUD_CLIENT_SECRET=your-client-secret
   ```

3. **Create PostgreSQL Database**
   - Add PostgreSQL add-on in Render
   - Copy connection details to backend environment variables

#### Frontend Deployment
1. **Create Static Site**
   - Connect GitHub repository
   - Select `frontend` as root directory
   - Build command: `npm install && npm run build`
   - Publish directory: `dist`

2. **Environment Variables**
   ```env
   VITE_API_BASE_URL=https://your-backend-url.onrender.com/api
   VITE_SOUNDCLOUD_CLIENT_ID=your-client-id
   VITE_SPOTIFY_CLIENT_ID=your-spotify-client-id
   ```

### Option 2: Fly.io Deployment

#### 1. Install Fly CLI
```bash
# macOS
brew install flyctl

# Linux/Windows
curl -L https://fly.io/install.sh | sh
```

#### 2. Deploy Backend
```bash
cd backend/soundwrapped-backend

# Initialize Fly app
fly launch

# Set environment variables
fly secrets set SPRING_DATASOURCE_URL="your-db-url"
fly secrets set SPRING_DATASOURCE_USERNAME="your-username"
fly secrets set SPRING_DATASOURCE_PASSWORD="your-password"
fly secrets set SOUNDCLOUD_CLIENT_ID="your-client-id"
fly secrets set SOUNDCLOUD_CLIENT_SECRET="your-client-secret"

# Deploy
fly deploy
```

#### 3. Deploy Frontend
```bash
cd frontend

# Initialize Fly app
fly launch

# Set environment variables
fly secrets set VITE_API_BASE_URL="https://your-backend-app.fly.dev/api"

# Deploy
fly deploy
```

### Option 3: Railway Deployment

#### 1. Connect Repository
- Go to Railway dashboard
- Click "New Project" → "Deploy from GitHub repo"
- Select your SoundWrapped repository

#### 2. Configure Services
- **Backend Service**: Set root directory to `backend/soundwrapped-backend`
- **Frontend Service**: Set root directory to `frontend`
- **Database Service**: Add PostgreSQL add-on

#### 3. Environment Variables
Set the same environment variables as in other platforms.

### Option 4: Docker Compose (VPS)

#### 1. Server Setup
```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

#### 2. Deploy Application
```bash
# Clone repository
git clone https://github.com/your-username/SoundWrapped.git
cd SoundWrapped

# Configure environment
cp .env.example .env
nano .env

# Start services
docker-compose -f docker-compose.prod.yml up -d
```

#### 3. SSL Configuration
```bash
# Install Certbot
sudo apt install certbot

# Get SSL certificate
sudo certbot certonly --standalone -d yourdomain.com

# Update nginx configuration
sudo nano nginx/nginx.conf
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
SPRING_PROFILES_ACTIVE=production
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

#### Frontend (.env)
```env
# API Configuration
VITE_API_BASE_URL=https://your-backend-url.com/api

# SoundCloud Configuration
VITE_SOUNDCLOUD_CLIENT_ID=your_client_id

# Spotify Configuration
VITE_SPOTIFY_CLIENT_ID=your_spotify_client_id
VITE_SPOTIFY_REDIRECT_URI=https://your-frontend-url.com/callback/spotify
```

### Nginx Configuration

Create `nginx/nginx.conf`:
```nginx
events {
    worker_connections 1024;
}

http {
    upstream backend {
        server backend:8081;
    }

    upstream frontend {
        server frontend:80;
    }

    server {
        listen 80;
        server_name yourdomain.com;
        return 301 https://$server_name$request_uri;
    }

    server {
        listen 443 ssl;
        server_name yourdomain.com;

        ssl_certificate /etc/nginx/ssl/cert.pem;
        ssl_certificate_key /etc/nginx/ssl/key.pem;

        location / {
            proxy_pass http://frontend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }

        location /api/ {
            proxy_pass http://backend/api/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
}
```

## 🔍 Monitoring and Logs

### Health Checks
```bash
# Check backend health
curl https://your-backend-url.com/actuator/health

# Check frontend
curl https://your-frontend-url.com
```

### Logs
```bash
# Docker Compose logs
docker-compose logs -f backend
docker-compose logs -f frontend

# Individual container logs
docker logs soundwrapped-backend
docker logs soundwrapped-frontend
```

### Monitoring Setup
1. **Uptime Monitoring**: Use services like UptimeRobot
2. **Error Tracking**: Integrate Sentry for error monitoring
3. **Performance Monitoring**: Use New Relic or DataDog
4. **Database Monitoring**: Set up PostgreSQL monitoring

## 🔒 Security Considerations

### 1. Environment Variables
- Never commit `.env` files to version control
- Use secure password generation
- Rotate API keys regularly

### 2. Database Security
- Use strong passwords
- Enable SSL connections
- Regular backups
- Access restrictions

### 3. Application Security
- Enable HTTPS
- Set security headers
- Regular dependency updates
- Input validation

### 4. API Security
- Rate limiting
- CORS configuration
- Authentication tokens
- Input sanitization

## 📊 Performance Optimization

### 1. Frontend Optimization
- Enable gzip compression
- Use CDN for static assets
- Implement caching strategies
- Optimize images and assets

### 2. Backend Optimization
- Database query optimization
- Connection pooling
- Caching strategies
- Load balancing

### 3. Database Optimization
- Proper indexing
- Query optimization
- Connection pooling
- Regular maintenance

## 🚨 Troubleshooting

### Common Issues

#### 1. Database Connection Issues
```bash
# Check database status
docker-compose ps db

# Check database logs
docker-compose logs db

# Test connection
docker-compose exec backend curl http://db:5432
```

#### 2. Frontend Build Issues
```bash
# Clear npm cache
npm cache clean --force

# Reinstall dependencies
rm -rf node_modules package-lock.json
npm install

# Check build logs
npm run build
```

#### 3. Backend Startup Issues
```bash
# Check Java version
java -version

# Check Maven build
mvn clean package

# Check application logs
docker-compose logs backend
```

### Debug Commands
```bash
# Check all services
docker-compose ps

# View logs
docker-compose logs -f

# Restart services
docker-compose restart

# Rebuild and restart
docker-compose up --build --force-recreate
```

## 📈 Scaling

### Horizontal Scaling
- Use load balancers
- Multiple backend instances
- Database read replicas
- CDN for static assets

### Vertical Scaling
- Increase server resources
- Optimize database performance
- Implement caching layers
- Monitor resource usage

## 🔄 CI/CD Pipeline

### GitHub Actions
The project includes a comprehensive CI/CD pipeline:
- Automated testing
- Docker image building
- Security scanning
- Deployment automation

### Manual Deployment
```bash
# Build and push images
docker-compose -f docker-compose.prod.yml build
docker-compose -f docker-compose.prod.yml push

# Deploy to production
docker-compose -f docker-compose.prod.yml up -d
```

## 📞 Support

For deployment issues:
- Check the logs first
- Review environment variables
- Verify network connectivity
- Check resource usage

For additional help:
- GitHub Issues
- Documentation
- Community Discord
- Email support

---

**Happy Deploying! 🚀**
