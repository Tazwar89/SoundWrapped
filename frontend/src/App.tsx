import { Routes, Route } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { AuthProvider } from './contexts/AuthContext'
import { MusicDataProvider } from './contexts/MusicDataContext'
import Layout from './components/Layout'
import ErrorBoundary from './components/ErrorBoundary'
import HomePage from './pages/HomePage'
import DashboardPage from './pages/DashboardPage'
import WrappedPage from './pages/WrappedPage'
import MusicTasteMapPage from './pages/MusicTasteMapPage'
import ProfilePage from './pages/ProfilePage'
import NotFoundPage from './pages/NotFoundPage'

function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <MusicDataProvider>
          <Helmet>
            <title>SoundWrapped - Your Music Journey</title>
            <meta name="description" content="Discover your music taste with SoundWrapped - personalized insights from SoundCloud and Spotify" />
          </Helmet>
          
          <Layout>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/wrapped" element={<WrappedPage />} />
              <Route path="/music-taste-map" element={<MusicTasteMapPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Layout>
        </MusicDataProvider>
      </AuthProvider>
    </ErrorBoundary>
  )
}

export default App
