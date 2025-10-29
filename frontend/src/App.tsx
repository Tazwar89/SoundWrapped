import { Routes, Route } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { AuthProvider } from './contexts/AuthContext'
import { MusicDataProvider } from './contexts/MusicDataContext'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import DashboardPage from './pages/DashboardPage'
import WrappedPage from './pages/WrappedPage'
import MusicTasteMapPage from './pages/MusicTasteMapPage'
import ProfilePage from './pages/ProfilePage'
import NotFoundPage from './pages/NotFoundPage'

function App() {
  return (
    <AuthProvider>
      <MusicDataProvider>
        <Helmet>
          <title>SoundWrapped - Your Music Journey</title>
          <meta name="description" content="Discover your music taste with SoundWrapped - personalized insights from SoundCloud and Spotify" />
        </Helmet>
        
        <Routes>
          <Route path="/" element={<Layout><HomePage /></Layout>} />
          <Route path="/dashboard" element={<Layout><DashboardPage /></Layout>} />
          <Route path="/wrapped" element={<Layout><WrappedPage /></Layout>} />
          <Route path="/music-taste-map" element={<Layout><MusicTasteMapPage /></Layout>} />
          <Route path="/profile" element={<Layout><ProfilePage /></Layout>} />
          <Route path="*" element={<Layout><NotFoundPage /></Layout>} />
        </Routes>
      </MusicDataProvider>
    </AuthProvider>
  )
}

export default App
