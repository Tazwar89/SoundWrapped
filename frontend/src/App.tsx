import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './contexts/AuthContext'
import { MusicDataProvider } from './contexts/MusicDataContext'
import Layout from './components/Layout'
import ErrorBoundary from './components/ErrorBoundary'
import LoadingSpinner from './components/LoadingSpinner'

// Route-based code splitting - lazy load pages
const HomePage = lazy(() => import('./pages/HomePage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const WrappedPage = lazy(() => import('./pages/WrappedPage'))
const MusicTasteMapPage = lazy(() => import('./pages/MusicTasteMapPage'))
const ProfilePage = lazy(() => import('./pages/ProfilePage'))
const LastFmCallbackPage = lazy(() => import('./pages/LastFmCallbackPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))

// Create React Query client with optimized defaults
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 10 * 60 * 1000, // 10 minutes (formerly cacheTime)
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MusicDataProvider>
            <Helmet>
              <title>SoundWrapped - Your Music Journey</title>
              <meta name="description" content="Discover your music taste with SoundWrapped - personalized insights from SoundCloud and Spotify" />
            </Helmet>
            
            <Layout>
              <Suspense fallback={<LoadingSpinner />}>
                <Routes>
                  <Route path="/" element={<HomePage />} />
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/wrapped" element={<WrappedPage />} />
                  <Route path="/music-taste-map" element={<MusicTasteMapPage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/lastfm/callback" element={<LastFmCallbackPage />} />
                  <Route path="*" element={<NotFoundPage />} />
                </Routes>
              </Suspense>
            </Layout>
          </MusicDataProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
