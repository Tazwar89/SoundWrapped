import React, { useEffect } from 'react'
import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { 
  BarChart3, 
  Music, 
  Users, 
  Clock,
  Play,
  Heart,
  Share2,
  ArrowRight,
  RefreshCw
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { useMusicData } from '../contexts/MusicDataContext'
import StatCard from '../components/StatCard'
import TopTracksChart from '../components/TopTracksChart'
import TopArtistsChart from '../components/TopArtistsChart'
import RecentActivity from '../components/RecentActivity'
import LoadingSpinner from '../components/LoadingSpinner'

const DashboardPage: React.FC = () => {
  const { user, isAuthenticated } = useAuth()
  const { 
    tracks, 
    artists, 
    wrappedData,
    isLoadingTracks,
    isLoadingArtists,
    refreshAllData
  } = useMusicData()

  useEffect(() => {
    console.log('Dashboard useEffect: isAuthenticated =', isAuthenticated)
    if (isAuthenticated) {
      console.log('Dashboard: Calling refreshAllData...')
      refreshAllData()
    }
  }, [isAuthenticated]) // Removed refreshAllData from dependencies to prevent infinite loop

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">Please Log In</h1>
          <p className="text-slate-300 mb-8">Connect your account to view your dashboard</p>
          <Link to="/" className="btn-primary">
            Go to Home
          </Link>
        </div>
      </div>
    )
  }

  // Calculate stats based on ACTUAL LISTENING HISTORY, not metadata
  // Following Spotify Wrapped approach: use play counts × duration for real listening time
  const totalListeningHours = wrappedData?.stats?.totalListeningHours ?? 
    tracks.reduce((acc, track) => {
      // Use playback_count × duration for actual listening time
      const playCount = track.playCount || 0
      const durationHours = (track.duration || 0) / 1000 / 60 / 60
      return acc + (playCount * durationHours)
    }, 0)
  
  const totalPlays = tracks.reduce((acc, track) => acc + (track.playCount || 0), 0)
  
  // Use likes from wrapped data (based on actual fetched likes, not track metadata)
  const totalLikes = wrappedData?.stats?.likesGiven ?? 0

  const stats = [
    {
      title: 'Total Tracks',
      value: tracks.length.toLocaleString(),
      icon: Music,
      color: 'from-primary-500 to-primary-600',
      change: '+12%',
      changeType: 'positive' as const
    },
    {
      title: 'Listening Hours',
      value: totalListeningHours.toFixed(1),
      icon: Clock,
      color: 'from-soundcloud-500 to-soundcloud-600',
      change: '+8%',
      changeType: 'positive' as const
    },
    {
      title: 'Total Plays',
      value: totalPlays.toLocaleString(),
      icon: Play,
      color: 'from-spotify-500 to-spotify-600',
      change: '+15%',
      changeType: 'positive' as const
    },
    {
      title: 'Total Likes',
      value: totalLikes.toLocaleString(),
      icon: Heart,
      color: 'from-pink-500 to-pink-600',
      change: '+22%',
      changeType: 'positive' as const
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
            <p className="text-slate-300">
              Here's what's happening with your music taste
            </p>
          </div>
          <div className="flex space-x-4 mt-4 md:mt-0">
            <button
              onClick={refreshAllData}
              className="px-4 py-2 bg-white/5 hover:bg-white/10 rounded-lg transition-colors flex items-center space-x-2"
            >
              <RefreshCw className="h-4 w-4" />
              <span>Refresh</span>
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

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          {stats.map((stat, index) => (
            <motion.div
              key={stat.title}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: index * 0.1 }}
            >
              <StatCard {...stat} />
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
                className="text-primary-400 hover:text-primary-300 text-sm font-medium flex items-center space-x-1"
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
                className="text-primary-400 hover:text-primary-300 text-sm font-medium flex items-center space-x-1"
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
            <RecentActivity tracks={tracks.slice(0, 5)} />
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
                className="flex items-center space-x-3 p-4 rounded-lg bg-gradient-to-r from-primary-500/20 to-primary-600/20 hover:from-primary-500/30 hover:to-primary-600/30 transition-all group"
              >
                <BarChart3 className="h-5 w-5 text-primary-400" />
                <div>
                  <div className="font-medium text-slate-200">View Your Wrapped</div>
                  <div className="text-sm text-slate-400">Complete music summary</div>
                </div>
                <ArrowRight className="h-4 w-4 text-slate-400 group-hover:translate-x-1 transition-transform ml-auto" />
              </Link>

              <Link
                to="/music-taste-map"
                className="flex items-center space-x-3 p-4 rounded-lg bg-gradient-to-r from-soundcloud-500/20 to-soundcloud-600/20 hover:from-soundcloud-500/30 hover:to-soundcloud-600/30 transition-all group"
              >
                <Users className="h-5 w-5 text-soundcloud-400" />
                <div>
                  <div className="font-medium text-slate-200">Music Taste Map</div>
                  <div className="text-sm text-slate-400">Find similar listeners</div>
                </div>
                <ArrowRight className="h-4 w-4 text-slate-400 group-hover:translate-x-1 transition-transform ml-auto" />
              </Link>

              <Link
                to="/profile"
                className="flex items-center space-x-3 p-4 rounded-lg bg-gradient-to-r from-spotify-500/20 to-spotify-600/20 hover:from-spotify-500/30 hover:to-spotify-600/30 transition-all group"
              >
                <Share2 className="h-5 w-5 text-spotify-400" />
                <div>
                  <div className="font-medium text-slate-200">Share Profile</div>
                  <div className="text-sm text-slate-400">Show off your taste</div>
                </div>
                <ArrowRight className="h-4 w-4 text-slate-400 group-hover:translate-x-1 transition-transform ml-auto" />
              </Link>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  )
}

export default DashboardPage
