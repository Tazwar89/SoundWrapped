import React, { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { useSearchParams } from 'react-router-dom'
import { 
  Music, 
  BarChart3, 
  MapPin, 
  Sparkles, 
  Headphones, 
  Users,
  Clock,
  ArrowRight,
  Star,
  TrendingUp
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { api } from '../services/api'

const HomePage: React.FC = () => {
  const { isAuthenticated, login } = useAuth()
  const [searchParams] = useSearchParams()
  const [featuredTrack, setFeaturedTrack] = useState<any>(null)
  const [featuredArtist, setFeaturedArtist] = useState<any>(null)
  const [trendingTracks, setTrendingTracks] = useState<any[]>([])
  const [featuredGenre, setFeaturedGenre] = useState<any>(null)

  useEffect(() => {
    const authStatus = searchParams.get('auth')
    if (authStatus === 'success') {
      // Show success message and refresh user data
      console.log('Authentication successful!')
      // You can add a toast notification here
    } else if (authStatus === 'error') {
      // Show error message
      console.log('Authentication failed!')
      // You can add a toast notification here
    }
  }, [searchParams])

  // Fetch featured content (works for both authenticated and non-authenticated users)
  useEffect(() => {
    const fetchFeaturedContent = async () => {
      // Fetch featured track from SoundCloud's popular tracks
      try {
        const trackResponse = await api.get('/soundcloud/featured/track')
        console.log('[HomePage] Featured track response:', trackResponse?.data)
        console.log('[HomePage] Featured track response keys:', trackResponse?.data ? Object.keys(trackResponse.data) : 'null')
        if (trackResponse?.data && Object.keys(trackResponse.data).length > 0) {
          setFeaturedTrack(trackResponse.data)
        } else {
          console.warn('[HomePage] Featured track response is empty or has no keys')
        }
      } catch (e: any) {
        console.error('[HomePage] Error fetching featured track:', e)
        console.error('[HomePage] Error details:', e.response?.data || e.message)
        // Silently fail - use placeholder
      }

      // Fetch featured artist from SoundCloud's popular tracks
      try {
        const artistResponse = await api.get('/soundcloud/featured/artist')
        console.log('[HomePage] Featured artist response:', artistResponse?.data)
        console.log('[HomePage] Featured artist tracks:', artistResponse?.data?.tracks)
        console.log('[HomePage] Featured artist tracks count:', artistResponse?.data?.tracks?.length || 0)
        if (artistResponse?.data && Object.keys(artistResponse.data).length > 0) {
          setFeaturedArtist(artistResponse.data)
        } else {
          console.warn('[HomePage] Featured artist response is empty')
        }
      } catch (e) {
        console.error('[HomePage] Error fetching featured artist:', e)
        // Silently fail - use placeholder
      }

      // Fetch popular/trending tracks from SoundCloud
      try {
        const popularResponse = await api.get('/soundcloud/popular/tracks?limit=4')
        console.log('[HomePage] Popular tracks response:', popularResponse?.data)
        console.log('[HomePage] Popular tracks is array?', Array.isArray(popularResponse?.data))
        console.log('[HomePage] Popular tracks length:', popularResponse?.data?.length)
        if (popularResponse?.data && Array.isArray(popularResponse.data) && popularResponse.data.length > 0) {
          setTrendingTracks(popularResponse.data)
        } else {
          console.warn('[HomePage] Popular tracks response is empty or not an array:', popularResponse?.data)
        }
      } catch (e: any) {
        console.error('[HomePage] Error fetching popular tracks:', e)
        console.error('[HomePage] Error details:', e.response?.data || e.message)
        // Silently fail - use placeholder
      }

      // Fetch featured genre with tracks
      try {
        const genreResponse = await api.get('/soundcloud/featured/genre')
        console.log('[HomePage] Featured genre response:', genreResponse?.data)
        if (genreResponse?.data) {
          setFeaturedGenre(genreResponse.data)
        } else {
          console.warn('[HomePage] Featured genre response is empty')
        }
      } catch (e) {
        console.error('[HomePage] Error fetching featured genre:', e)
        // Silently fail - use placeholder
      }
    }

    fetchFeaturedContent()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const features = [
    {
      icon: BarChart3,
      title: 'Comprehensive Analytics',
      description: 'Deep insights into your listening habits, top artists, and music trends.',
      color: 'from-orange-500 to-orange-600'
    },
    {
      icon: MapPin,
      title: 'Music Taste Map',
      description: 'Discover where people with similar music taste live around the world.',
      color: 'from-orange-500 to-orange-600'
    },
    {
      icon: Users,
      title: 'Music Doppelgänger',
      description: 'Find users with similar music taste and discover new tracks.',
      color: 'from-orange-500 to-orange-600'
    }
  ]

  return (
    <div className="min-h-screen">
      <div className="container mx-auto px-4 py-16">
        <div className="text-center mb-16">
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <h1 className="text-4xl md:text-6xl font-bold mb-6 gradient-text">
              SoundWrapped
            </h1>
            <p className="text-xl md:text-2xl text-white/90 mb-8 max-w-3xl mx-auto font-medium">
              Discover your music journey with personalized insights from SoundCloud. 
              Your taste, beautifully visualized.
            </p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="flex flex-col sm:flex-row gap-4 justify-center mb-12"
          >
            {isAuthenticated ? (
              <Link
                to="/dashboard"
                className="btn-primary text-lg px-8 py-4 inline-flex items-center justify-center group"
              >
                <BarChart3 className="h-5 w-5 mr-2" />
                Go to Dashboard
                <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
              </Link>
            ) : (
              <button
                onClick={() => login('soundcloud')}
                className="btn-secondary text-lg px-8 py-4 inline-flex items-center justify-center group"
              >
                <Headphones className="h-5 w-5 mr-2" />
                Connect to SoundCloud
                <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
              </button>
            )}
          </motion.div>

          {/* Artist of the Day Section */}
          {featuredArtist && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="max-w-6xl mx-auto mb-12"
            >
              <div className="stat-card">
                <h3 className="text-sm font-semibold text-orange-400 uppercase tracking-wide mb-3 text-left">Artist of the Day</h3>
                <div className="flex items-start space-x-6 mb-4">
                  <a
                    href={featuredArtist.permalink_url || `https://soundcloud.com/${featuredArtist.permalink || featuredArtist.username || ''}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex-shrink-0 hover:opacity-80 transition-opacity"
                  >
                    {featuredArtist.avatar_url ? (
                      <img
                        src={featuredArtist.avatar_url}
                        alt={featuredArtist.username || 'Artist'}
                        className="w-24 h-24 rounded-full border-2 border-orange-500/30 hover:border-orange-500/50 transition-colors"
                      />
                    ) : (
                      <div className="w-24 h-24 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full flex items-center justify-center hover:from-orange-600 hover:to-orange-700 transition-colors">
                        <Music className="h-12 w-12 text-white" />
                      </div>
                    )}
                  </a>
                  <div className="flex-1 text-left">
                    <a
                      href={featuredArtist.permalink_url || `https://soundcloud.com/${featuredArtist.permalink || featuredArtist.username || ''}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="block hover:text-orange-400 transition-colors"
                    >
                      <h4 className="text-2xl font-bold text-white mb-2">
                        {(() => {
                          // SoundCloud's display name is typically the username (e.g., "lady in red", "Steve Lacy")
                          // full_name might just be the first name (e.g., "marie"), so prioritize username
                          if (featuredArtist.username) {
                            // Capitalize first letter of each word for better display
                            return featuredArtist.username
                              .split(' ')
                              .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
                              .join(' ');
                          }
                          // Fallback to full_name if username is not available
                          return featuredArtist.full_name || 'Discover New Artists';
                        })()}
                      </h4>
                    </a>
                    <p className="text-sm text-white/80 mb-4">
                      {featuredArtist.description || 'Explore SoundCloud\'s diverse music community'}
                    </p>
                  </div>
                </div>
                
                {/* Popular Tracks from Artist */}
                {featuredArtist.tracks && Array.isArray(featuredArtist.tracks) && featuredArtist.tracks.length > 0 ? (
                  <div className="mt-6">
                    <h5 className="text-sm font-semibold text-orange-400 mb-3 text-left">Popular Tracks</h5>
                    <div className="space-y-3">
                      {featuredArtist.tracks.map((track: any, index: number) => (
                        <a
                          key={track.id || index}
                          href={track.permalink_url || `https://soundcloud.com${track.uri?.replace('soundcloud:', '') || ''}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="flex items-center space-x-4 p-3 bg-black/10 rounded-lg border border-white/5 hover:border-orange-500/30 hover:bg-black/20 transition-all group"
                        >
                          {track.artwork_url ? (
                            <img
                              src={track.artwork_url}
                              alt={track.title || 'Track'}
                              className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                            />
                          ) : (
                            <div className="w-16 h-16 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-lg flex items-center justify-center flex-shrink-0">
                              <Music className="h-8 w-8 text-orange-400" />
                            </div>
                          )}
                          <div className="flex-1 min-w-0">
                            <h6 className="text-white font-medium truncate group-hover:text-orange-400 transition-colors">
                              {track.title || 'Untitled Track'}
                            </h6>
                            <p className="text-sm text-white/70 truncate">
                              {track.user?.username || featuredArtist.username || 'Artist'}
                            </p>
                          </div>
                          <ArrowRight className="h-5 w-5 text-white/50 group-hover:text-orange-400 group-hover:translate-x-1 transition-all flex-shrink-0" />
                        </a>
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="text-sm text-white/60 text-left mt-4">No tracks available for this artist</p>
                )}
              </div>
            </motion.div>
          )}

          {/* Song of the Day */}
          <div className="max-w-6xl mx-auto mb-12">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
              className="stat-card"
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-semibold text-orange-400 uppercase tracking-wide">Song of the Day</h3>
                <Music className="h-4 w-4 text-orange-500" />
              </div>
              <div className="text-center">
                {/* SoundCloud Player Embed */}
                {featuredTrack && (featuredTrack.permalink_url || featuredTrack.uri) ? (
                  <div className="mb-4">
                    <iframe
                      width="100%"
                      height="166"
                      scrolling="no"
                      frameBorder="no"
                      allow="autoplay"
                      src={`https://w.soundcloud.com/player/?url=${encodeURIComponent(
                        featuredTrack.permalink_url || 
                        `https://soundcloud.com${featuredTrack.uri?.replace('soundcloud:', '') || ''}`
                      )}&color=%23ff5500&auto_play=false&hide_related=false&show_comments=true&show_user=true&show_reposts=false&show_teaser=true`}
                      className="rounded-lg"
                    />
                  </div>
                ) : (
                  <div className="py-8 text-white/60">
                    <p>No featured track available</p>
                  </div>
                )}
                
                {isAuthenticated ? (
                  <Link to="/wrapped" className="text-xs text-orange-400 hover:text-orange-300 transition-colors">
                    View Your Top Tracks →
                  </Link>
                ) : (
                  <button
                    onClick={() => login('soundcloud')}
                    className="text-xs text-orange-400 hover:text-orange-300 transition-colors"
                  >
                    Sign in to explore →
                  </button>
                )}
              </div>
            </motion.div>
          </div>

          {/* Genre of the Day Section */}
          {featuredGenre && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.4 }}
              className="max-w-6xl mx-auto mb-12"
            >
              <div className="stat-card">
                <div className="mb-6">
                  <h3 className="text-sm font-semibold text-orange-400 uppercase tracking-wide mb-3 text-left">Genre of the Day</h3>
                  <h4 className="text-2xl font-bold text-white mb-2 capitalize text-left">
                    {featuredGenre.genre || 'Unknown Genre'}
                  </h4>
                  <p className="text-white/80 leading-relaxed text-left">
                    {featuredGenre.description || 'Explore this genre and discover new sounds.'}
                  </p>
                </div>
                
                {featuredGenre.tracks && featuredGenre.tracks.length > 0 ? (
                  <div className="space-y-3">
                    {featuredGenre.tracks.map((track: any, index: number) => (
                      <a
                        key={track.id || index}
                        href={track.permalink_url || `https://soundcloud.com${track.uri || ''}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center space-x-4 p-3 bg-black/10 rounded-lg border border-white/5 hover:border-orange-500/30 hover:bg-black/20 transition-all group"
                      >
                        {track.artwork_url ? (
                          <img
                            src={track.artwork_url}
                            alt={track.title || 'Track'}
                            className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                          />
                        ) : (
                          <div className="w-16 h-16 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-lg flex items-center justify-center flex-shrink-0">
                            <Music className="h-8 w-8 text-orange-400" />
                          </div>
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-base font-semibold text-white truncate group-hover:text-orange-400 transition-colors">
                            {track.title || 'Unknown Track'}
                          </p>
                          <p className="text-sm text-white/70 truncate">
                            {track.user?.username || 'Unknown Artist'}
                          </p>
                        </div>
                        <ArrowRight className="h-5 w-5 text-white/40 group-hover:text-orange-400 group-hover:translate-x-1 transition-all flex-shrink-0" />
                      </a>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-8 text-white/60">
                    <Music className="h-12 w-12 mx-auto mb-3 text-white/40" />
                    <p>No tracks available for this genre</p>
                  </div>
                )}
              </div>
            </motion.div>
          )}

          {/* Popular Now Section */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.5 }}
            className="max-w-6xl mx-auto"
          >
            <div className="stat-card">
              <div className="text-center mb-6">
                <div className="flex items-center justify-center space-x-2 mb-2">
                  <TrendingUp className="h-5 w-5 text-orange-500" />
                  <h3 className="text-xl font-bold text-white">Popular Now</h3>
                </div>
                <p className="text-sm text-white/70">What the SoundCloud community is listening to</p>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                {trendingTracks.length > 0 ? (
                  trendingTracks.map((track: any, index: number) => (
                    <div
                      key={track.id || index}
                      className="p-4 bg-black/10 rounded-lg border border-white/5 hover:border-orange-500/30 transition-all group"
                    >
                      <div className="flex items-center space-x-3 mb-2">
                        {track.artwork_url ? (
                          <img
                            src={track.artwork_url}
                            alt={track.title || 'Track'}
                            className="w-12 h-12 rounded-lg object-cover"
                          />
                        ) : (
                          <div className="w-12 h-12 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-lg flex items-center justify-center">
                            <Music className="h-6 w-6 text-orange-400" />
                          </div>
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-white truncate">{track.title || 'Unknown Track'}</p>
                          <p className="text-xs text-white/60 truncate">
                            {track.user?.username || 'Unknown Artist'}
                          </p>
                        </div>
                      </div>
                      {track.permalink_url && (
                        <a
                          href={track.permalink_url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-xs text-orange-400 hover:text-orange-300 transition-colors inline-flex items-center"
                        >
                          Listen on SoundCloud <ArrowRight className="h-3 w-3 ml-1" />
                        </a>
                      )}
                    </div>
                  ))
                ) : (
                  <div className="col-span-full text-center py-8 text-white/60">
                    <Music className="h-12 w-12 mx-auto mb-3 text-white/40" />
                    <p>No popular tracks available</p>
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        </div>

        {/* Features Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.6 }}
          className="max-w-6xl mx-auto mt-20"
        >
          <h2 className="text-3xl font-bold text-center mb-12 gradient-text">
            What You Can Do
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {features.map((feature, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.7 + index * 0.1 }}
                className="stat-card text-center"
              >
                <div className={`w-16 h-16 bg-gradient-to-r ${feature.color} rounded-full flex items-center justify-center mx-auto mb-4`}>
                  <feature.icon className="h-8 w-8 text-white" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2">{feature.title}</h3>
                <p className="text-white/70">{feature.description}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  )
}

export default HomePage
