import React, { useEffect, useState, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Link, useLocation } from 'react-router-dom'
import toast from 'react-hot-toast'
import { 
  BarChart3, 
  Music, 
  Users, 
  Clock,
  Play,
  Heart,
  Share2,
  ArrowRight,
  RefreshCw,
  Info
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { useMusicData } from '../contexts/MusicDataContext'
import { api } from '../services/api'
import StatCard from '../components/StatCard'
import TopTracksChart from '../components/TopTracksChart'
import TopArtistsChart from '../components/TopArtistsChart'
import RecentActivity from '../components/RecentActivity'
import LoadingSpinner from '../components/LoadingSpinner'

const DashboardPage: React.FC = () => {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth()
  const location = useLocation()
  const { 
    tracks, 
    artists, 
    isLoadingTracks,
    isLoadingArtists,
    refreshAllData
  } = useMusicData()
  
  const [analytics, setAnalytics] = useState<any>(null)
  const [isLoadingAnalytics, setIsLoadingAnalytics] = useState(false)
  const [recentActivity, setRecentActivity] = useState<any[]>([])
  const [isLoadingActivity, setIsLoadingActivity] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  // Define fetch functions before useEffect
  const fetchRecentActivity = useCallback(async () => {
    try {
      setIsLoadingActivity(true)
      console.log('[Dashboard] Fetching recent activity...')
      const response = await api.get('/soundcloud/recent-activity?limit=5')
      console.log('[Dashboard] Recent activity response:', response?.data)
      if (response?.data && Array.isArray(response.data)) {
        console.log('[Dashboard] Setting recent activity, count:', response.data.length)
        setRecentActivity(response.data)
      } else {
        console.warn('[Dashboard] Invalid recent activity response:', response?.data)
        setRecentActivity([])
      }
    } catch (error: any) {
      console.error('Failed to fetch recent activity:', error)
      console.error('Error details:', error.response?.data || error.message)
      setRecentActivity([])
    } finally {
      setIsLoadingActivity(false)
    }
  }, [])

  const fetchAnalytics = useCallback(async () => {
    try {
      setIsLoadingAnalytics(true)
      const response = await api.get('/soundcloud/dashboard/analytics')
      setAnalytics(response.data)
    } catch (error) {
      console.error('Failed to fetch analytics:', error)
    } finally {
      setIsLoadingAnalytics(false)
    }
  }, [])

  // Auto-refresh whenever the Dashboard page is visited
  useEffect(() => {
    console.log('Dashboard useEffect: isAuthenticated =', isAuthenticated, 'location =', location.pathname)
    if (isAuthenticated && location.pathname === '/dashboard') {
      console.log('Dashboard: Auto-refreshing all data on page visit...')
      refreshAllData()
      fetchAnalytics()
      fetchRecentActivity()
    }
  }, [isAuthenticated, location.pathname, refreshAllData, fetchAnalytics, fetchRecentActivity])

  const handleRefresh = async () => {
    try {
      setIsRefreshing(true)
      console.log('[Dashboard] Refresh button clicked - refreshing all data...')
      
      // Refresh all data in parallel for better performance
      await Promise.all([
        refreshAllData(), // Refreshes tracks, artists, playlists, wrapped data, music taste map
        fetchAnalytics(), // Refreshes analytics stats
        fetchRecentActivity() // Refreshes recent activity
      ])
      
      console.log('[Dashboard] All data refreshed successfully')
      toast.success('Dashboard refreshed successfully!', {
        icon: '✅',
        duration: 2000
      })
    } catch (error) {
      console.error('[Dashboard] Error refreshing data:', error)
      toast.error('Failed to refresh dashboard. Please try again.', {
        icon: '❌',
        duration: 3000
      })
    } finally {
      setIsRefreshing(false)
    }
  }

  // Show loading state while checking authentication
  if (authLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <LoadingSpinner />
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">Please Log In</h1>
          <p className="text-white/90 mb-8 font-medium">Connect your account to view your dashboard</p>
          <Link to="/" className="btn-primary">
            Go to Home
          </Link>
        </div>
      </div>
    )
  }

  // Get stats from analytics (combines API data + tracked activity)
  // Note: SoundCloud API doesn't provide listening history, so we can only track in-app activity
  const apiStats = analytics?.apiStats || {}
  const trackedStats = analytics?.trackedStats || {}
  const availableMetrics = analytics?.availableMetrics || {}
  const genreAnalysis = analytics?.genreAnalysis || {}

  // API-available stats (from SoundCloud profile/data)
  const totalTracks = availableMetrics.totalTracks || apiStats.totalTracksAvailable || tracks.length
  const profileLikes = availableMetrics.profileLikes || apiStats.totalLikesOnProfile || 0

  // Tracked in-app stats (only tracks activity within SoundWrapped app)
  const inAppListeningHours = availableMetrics.inAppListeningHours || trackedStats.inAppListeningHours || 0
  const inAppPlays = availableMetrics.inAppPlays || trackedStats.inAppPlays || 0

  // Genre stats
  const genreDiscoveryCount = genreAnalysis.totalGenresDiscovered || availableMetrics.genreDiscoveryCount || 0
  const topGenres = availableMetrics.topGenres || genreAnalysis.topGenresByListeningTime?.slice(0, 5)?.map((g: any) => g.genre) || []

  // Listening pattern stats
  const listeningPatterns = analytics?.listeningPatterns || {}
  const listeningPersona = listeningPatterns.listeningPersona || null
  const peakHour = listeningPatterns.peakHourLabel || null
  const peakDay = listeningPatterns.peakDayLabel || null
  const hasListeningData = listeningPatterns.hasData === true

  // Music Doppelgänger stats
  const musicDoppelganger = analytics?.musicDoppelganger || {}
  const hasDoppelganger = musicDoppelganger.found === true
  const doppelgangerData = musicDoppelganger.doppelganger || {}

  const stats = [
    {
      title: 'Available Tracks',
      value: totalTracks.toLocaleString(),
      icon: Music,
      color: 'from-orange-500 to-orange-600',
      subtitle: 'From SoundCloud API',
      dataSource: 'api' as const
    },
    {
      title: 'In-App Listening',
      value: inAppListeningHours > 0 ? inAppListeningHours.toFixed(1) : '0.0',
      icon: Clock,
      color: 'from-orange-500 to-orange-600',
      subtitle: 'Tracks in-app activity only',
      dataSource: 'tracked' as const
    },
    {
      title: 'In-App Plays',
      value: inAppPlays.toLocaleString(),
      icon: Play,
      color: 'from-orange-500 to-orange-600',
      subtitle: 'Plays within SoundWrapped',
      dataSource: 'tracked' as const
    },
    {
      title: 'Profile Likes',
      value: profileLikes.toLocaleString(),
      icon: Heart,
      color: 'from-orange-500 to-orange-600',
      subtitle: 'From SoundCloud profile',
      dataSource: 'api' as const
    }
  ]

  return (
    <div className="py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8">
          <div>
            <h1 className="text-3xl md:text-4xl font-bold gradient-text mb-2">
              Welcome back, {user?.username}!
            </h1>
            <p className="text-white/90 font-medium">
              Here's what's happening with your music taste
            </p>
          </div>
          <div className="flex space-x-4 mt-4 md:mt-0">
            <button
              onClick={handleRefresh}
              disabled={isRefreshing}
              className={`px-4 py-2 bg-white/5 hover:bg-white/10 rounded-lg transition-colors flex items-center space-x-2 ${
                isRefreshing ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
              <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
            </button>
            <Link
              to="/wrapped"
              className="btn-primary flex items-center space-x-2"
            >
              <BarChart3 className="h-4 w-4" />
              <span>View Wrapped</span>
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>

        {/* Info Banner */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 p-4 bg-orange-500/10 border border-orange-500/20 rounded-lg"
        >
          <div className="flex items-start space-x-3">
            <Info className="h-5 w-5 text-orange-400 mt-0.5 flex-shrink-0" />
            <div className="flex-1">
              <h4 className="text-sm font-semibold text-orange-300 mb-1">About Your Analytics</h4>
              <p className="text-xs text-orange-200/80">
                SoundCloud API doesn't provide listening history. The "In-App" stats only track activity within SoundWrapped. 
                To build comprehensive analytics, use our player to listen to tracks. Platform-wide listening data is not available.
              </p>
            </div>
          </div>
        </motion.div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          {stats.map((stat, index) => (
            <motion.div
              key={stat.title}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: index * 0.1 }}
              className="relative"
            >
              <StatCard {...stat} />
              {stat.subtitle && (
                <p className="text-xs text-white/70 mt-2 text-center">{stat.subtitle}</p>
              )}
            </motion.div>
          ))}
        </div>

        {/* Charts Section */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
          {/* Top Tracks */}
          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6 }}
            className="stat-card"
          >
            <div className="flex items-center justify-between mb-6">
              <h3 className="subsection-title">Top Tracks</h3>
              <Link
                to="/wrapped"
                className="text-orange-400 hover:text-orange-300 text-sm font-medium flex items-center space-x-1"
              >
                <span>View All</span>
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
            {isLoadingTracks ? (
              <LoadingSpinner />
            ) : (
              <TopTracksChart tracks={tracks.slice(0, 5)} />
            )}
          </motion.div>

          {/* Top Artists */}
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="stat-card"
          >
            <div className="flex items-center justify-between mb-6">
              <h3 className="subsection-title">Top Artists</h3>
              <Link
                to="/wrapped"
                className="text-orange-400 hover:text-orange-300 text-sm font-medium flex items-center space-x-1"
              >
                <span>View All</span>
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
            {isLoadingArtists ? (
              <LoadingSpinner />
            ) : (
              <TopArtistsChart artists={artists.slice(0, 5)} />
            )}
          </motion.div>
        </div>

        {/* Genre Analysis Section */}
        {genreDiscoveryCount > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="stat-card mb-8"
          >
            <div className="flex items-center justify-between mb-6">
              <div>
                <h3 className="subsection-title">Genre Discovery</h3>
                <p className="text-sm text-white/80 mt-1 font-medium">
                  You've explored {genreDiscoveryCount} {genreDiscoveryCount === 1 ? 'genre' : 'genres'}
                </p>
              </div>
            </div>
            {topGenres.length > 0 && (
              <div className="flex flex-wrap gap-3">
                {topGenres.map((genre: string, index: number) => (
                  <motion.div
                    key={genre}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.3, delay: 0.4 + index * 0.05 }}
                    className="px-4 py-2 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-full border border-orange-500/30"
                  >
                    <span className="text-sm font-medium text-orange-300 capitalize">
                      {genre}
                    </span>
                  </motion.div>
                ))}
              </div>
            )}
          </motion.div>
        )}

        {/* Listening Patterns Section */}
        {hasListeningData && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="stat-card mb-8"
          >
            <div className="flex items-center justify-between mb-6">
              <div>
                <h3 className="subsection-title">Listening Patterns</h3>
                <p className="text-sm text-white/80 mt-1 font-medium">
                  Your music listening habits
                </p>
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Listening Persona */}
              {listeningPersona && (
                <div className="p-4 bg-gradient-to-br from-orange-500/20 to-orange-600/20 rounded-lg border border-orange-500/30">
                  <div className="text-sm text-white/80 mb-1">Your Persona</div>
                  <div className="text-2xl font-bold text-orange-300">{listeningPersona}</div>
                </div>
              )}
              
              {/* Peak Hour */}
              {peakHour && (
                <div className="p-4 bg-gradient-to-br from-orange-500/20 to-orange-600/20 rounded-lg border border-orange-500/30">
                  <div className="text-sm text-white/80 mb-1">Peak Listening Time</div>
                  <div className="text-2xl font-bold text-orange-300">{peakHour}</div>
                </div>
              )}
              
              {/* Peak Day */}
              {peakDay && (
                <div className="p-4 bg-gradient-to-br from-orange-500/20 to-orange-600/20 rounded-lg border border-orange-500/30">
                  <div className="text-sm text-white/80 mb-1">Most Active Day</div>
                  <div className="text-2xl font-bold text-orange-300">{peakDay}</div>
                </div>
              )}
            </div>
          </motion.div>
        )}

        {/* Music Doppelgänger Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.5 }}
          className="stat-card mb-8"
        >
          <div className="flex items-center justify-between mb-6">
            <div>
              <h3 className="subsection-title">Music Doppelgänger</h3>
              <p className="text-sm text-white/80 mt-1 font-medium">
                Your taste twin from people you follow
              </p>
            </div>
          </div>
          {hasDoppelganger ? (
            <div className="flex items-center space-x-6 p-6 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-lg border border-orange-500/30">
              {doppelgangerData.avatarUrl && (
                <img 
                  src={doppelgangerData.avatarUrl} 
                  alt={doppelgangerData.username}
                  className="w-16 h-16 rounded-full border-2 border-orange-400/50"
                />
              )}
              <div className="flex-1">
                <div className="flex items-center space-x-3 mb-2">
                  <h4 className="text-xl font-bold text-orange-300">
                    @{doppelgangerData.username}
                  </h4>
                  <span className="px-3 py-1 bg-orange-500/30 rounded-full text-sm font-medium text-orange-200">
                    {doppelgangerData.similarityPercentage}% match
                  </span>
                </div>
                {doppelgangerData.fullName && (
                  <p className="text-white/90 mb-3">{doppelgangerData.fullName}</p>
                )}
                <div className="flex flex-wrap gap-4 text-sm text-white/80">
                  {doppelgangerData.sharedTracks > 0 && (
                    <span>{doppelgangerData.sharedTracks} shared tracks</span>
                  )}
                  {doppelgangerData.sharedArtists > 0 && (
                    <span>{doppelgangerData.sharedArtists} shared artists</span>
                  )}
                  {doppelgangerData.sharedGenres > 0 && (
                    <span>{doppelgangerData.sharedGenres} shared genres</span>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="p-6 bg-black/10 rounded-lg border border-white/5">
              <p className="text-white/70 text-center">
                {musicDoppelganger.message || 'Not enough data to find your Music Doppelgänger. Make sure you have tracks and follow some users!'}
              </p>
            </div>
          )}
        </motion.div>

        {/* Recent Activity & Quick Actions */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Recent Activity */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="lg:col-span-2 stat-card"
          >
            <h3 className="subsection-title">Recent Activity</h3>
            {isLoadingActivity ? (
              <div className="flex items-center justify-center py-8">
                <LoadingSpinner />
              </div>
            ) : (
              <RecentActivity activities={recentActivity} />
            )}
          </motion.div>

          {/* Quick Actions */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.6 }}
            className="stat-card"
          >
            <h3 className="subsection-title">Quick Actions</h3>
            <div className="space-y-4">
              <Link
                to="/wrapped"
                className="flex items-center space-x-3 p-4 rounded-lg bg-gradient-to-r from-orange-500/20 to-orange-600/20 hover:from-orange-500/30 hover:to-orange-600/30 transition-all group"
              >
                <BarChart3 className="h-5 w-5 text-orange-400" />
                <div>
                  <div className="font-medium text-white">View Your Wrapped</div>
                  <div className="text-sm text-white/80">Complete music summary</div>
                </div>
                <ArrowRight className="h-4 w-4 text-white/80 group-hover:translate-x-1 transition-transform ml-auto" />
              </Link>

              <Link
                to="/music-taste-map"
                className="flex items-center space-x-3 p-4 rounded-lg bg-gradient-to-r from-orange-500/20 to-orange-600/20 hover:from-orange-500/30 hover:to-orange-600/30 transition-all group"
              >
                <Users className="h-5 w-5 text-orange-400" />
                <div>
                  <div className="font-medium text-white">Music Taste Map</div>
                  <div className="text-sm text-white/80">Find similar listeners</div>
                </div>
                <ArrowRight className="h-4 w-4 text-white/80 group-hover:translate-x-1 transition-transform ml-auto" />
              </Link>

              <Link
                to="/profile"
                className="flex items-center space-x-3 p-4 rounded-lg bg-gradient-to-r from-orange-500/20 to-orange-600/20 hover:from-orange-500/30 hover:to-orange-600/30 transition-all group"
              >
                <Share2 className="h-5 w-5 text-orange-400" />
                <div>
                  <div className="font-medium text-white">Share Profile</div>
                  <div className="text-sm text-white/80">Show off your taste</div>
                </div>
                <ArrowRight className="h-4 w-4 text-white/80 group-hover:translate-x-1 transition-transform ml-auto" />
              </Link>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  )
}

export default DashboardPage
