import React, { useEffect, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { 
  Music, 
  Heart, 
  Share2, 
  Clock, 
  Users, 
  Star,
  Calendar,
  Globe,
  BookOpen,
  Sparkles,
  ChevronLeft,
  ChevronRight
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { useMusicData } from '../contexts/MusicDataContext'
import LoadingSpinner from '../components/LoadingSpinner'
import { formatNumber, formatHours } from '../utils/formatters'

const WrappedPage: React.FC = () => {
  const { isAuthenticated } = useAuth()
  const { wrappedData, isLoadingWrapped, fetchWrappedData } = useMusicData()
  const [currentSlide, setCurrentSlide] = useState(0)
  const [isAutoPlaying, setIsAutoPlaying] = useState(true)

  useEffect(() => {
    if (isAuthenticated) {
      fetchWrappedData()
    }
  }, [isAuthenticated, fetchWrappedData])

  useEffect(() => {
    if (!wrappedData || !isAutoPlaying) return

    const interval = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % slides.length)
    }, 5000)

    return () => clearInterval(interval)
  }, [wrappedData, isAutoPlaying])

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">Please Log In</h1>
          <p className="text-slate-300 mb-8">Connect your account to view your wrapped</p>
        </div>
      </div>
    )
  }

  if (isLoadingWrapped) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <LoadingSpinner />
      </div>
    )
  }

  if (!wrappedData) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">No Data Available</h1>
          <p className="text-slate-300 mb-8">We couldn't find any wrapped data for your account</p>
        </div>
      </div>
    )
  }

  const slides = [
    // Welcome Slide
    {
      id: 'welcome',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ duration: 0.8, type: "spring" }}
            className="mb-8"
          >
            <div className="w-32 h-32 bg-gradient-to-r from-primary-500 to-soundcloud-500 rounded-full flex items-center justify-center mx-auto mb-6">
              <Music className="h-16 w-16 text-white" />
            </div>
          </motion.div>
          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="text-5xl md:text-7xl font-bold gradient-text mb-4"
          >
            Your 2024 Wrapped
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.5 }}
            className="text-xl text-slate-300"
          >
            Ready to discover your music story?
          </motion.p>
        </div>
      )
    },
    // Profile Stats
    {
      id: 'profile',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Music Profile
            </h2>
          </motion.div>
          
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6 max-w-4xl mx-auto">
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="stat-card text-center"
            >
              <Users className="h-8 w-8 text-primary-400 mx-auto mb-3" />
              <div className="text-3xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.profile.followers)}
              </div>
              <div className="text-slate-400">Followers</div>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.6, delay: 0.3 }}
              className="stat-card text-center"
            >
              <Music className="h-8 w-8 text-soundcloud-400 mx-auto mb-3" />
              <div className="text-3xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.profile.tracksUploaded)}
              </div>
              <div className="text-slate-400">Tracks</div>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.6, delay: 0.4 }}
              className="stat-card text-center"
            >
              <Calendar className="h-8 w-8 text-spotify-400 mx-auto mb-3" />
              <div className="text-3xl font-bold text-slate-200 mb-1">
                {wrappedData.profile.accountAgeYears}
              </div>
              <div className="text-slate-400">Years Active</div>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.6, delay: 0.5 }}
              className="stat-card text-center"
            >
              <Star className="h-8 w-8 text-pink-400 mx-auto mb-3" />
              <div className="text-3xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.profile.playlistsCreated)}
              </div>
              <div className="text-slate-400">Playlists</div>
            </motion.div>
          </div>
        </div>
      )
    },
    // Top Tracks
    {
      id: 'top-tracks',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Top Tracks
            </h2>
            <p className="text-xl text-slate-300">
              The songs you couldn't stop playing
            </p>
          </motion.div>
          
          <div className="max-w-4xl mx-auto">
            {wrappedData.topTracks.slice(0, 5).map((track, index) => (
              <motion.div
                key={track.title}
                initial={{ opacity: 0, x: -50 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.6, delay: index * 0.2 }}
                className="flex items-center space-x-6 p-4 rounded-xl bg-white/5 hover:bg-white/10 transition-colors mb-4"
              >
                <div className="text-3xl font-bold text-slate-400 w-12">
                  {track.rank}
                </div>
                <div className="flex-1 text-left">
                  <h3 className="text-xl font-semibold text-slate-200 mb-1">
                    {track.title}
                  </h3>
                  <p className="text-slate-400">{track.artist}</p>
                </div>
                <div className="text-right">
                  <div className="text-lg font-semibold text-slate-200">
                    {formatNumber(track.playCount)}
                  </div>
                  <div className="text-sm text-slate-400">plays</div>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )
    },
    // Top Artists
    {
      id: 'top-artists',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Top Artists
            </h2>
            <p className="text-xl text-slate-300">
              The artists who defined your year
            </p>
          </motion.div>
          
          <div className="max-w-4xl mx-auto">
            {wrappedData.topArtists.slice(0, 5).map((artist, index) => (
              <motion.div
                key={artist.artist}
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.6, delay: index * 0.2 }}
                className="flex items-center space-x-6 p-6 rounded-xl bg-gradient-to-r from-primary-500/10 to-soundcloud-500/10 hover:from-primary-500/20 hover:to-soundcloud-500/20 transition-all mb-4"
              >
                <div className="w-16 h-16 bg-gradient-to-r from-primary-500 to-soundcloud-500 rounded-full flex items-center justify-center text-white font-bold text-xl">
                  {artist.artist.charAt(0)}
                </div>
                <div className="flex-1 text-left">
                  <h3 className="text-2xl font-bold text-slate-200 mb-1">
                    #{artist.rank} {artist.artist}
                  </h3>
                  <p className="text-slate-400">Your top artist of the year</p>
                </div>
                <div className="text-right">
                  <div className="text-2xl">🎵</div>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )
    },
    // Listening Stats
    {
      id: 'listening-stats',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Listening Stats
            </h2>
            <p className="text-xl text-slate-300">
              The numbers that tell your story
            </p>
          </motion.div>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-6xl mx-auto">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="stat-card text-center"
            >
              <Clock className="h-12 w-12 text-primary-400 mx-auto mb-4" />
              <div className="text-4xl font-bold text-slate-200 mb-2">
                {formatHours(wrappedData.stats.totalListeningHours)}
              </div>
              <div className="text-slate-400 text-lg">Total Listening Time</div>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
              className="stat-card text-center"
            >
              <Heart className="h-12 w-12 text-pink-400 mx-auto mb-4" />
              <div className="text-4xl font-bold text-slate-200 mb-2">
                {formatNumber(wrappedData.stats.likesGiven)}
              </div>
              <div className="text-slate-400 text-lg">Likes Given</div>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.4 }}
              className="stat-card text-center"
            >
              <BookOpen className="h-12 w-12 text-yellow-400 mx-auto mb-4" />
              <div className="text-4xl font-bold text-slate-200 mb-2">
                {wrappedData.stats.booksYouCouldHaveRead}
              </div>
              <div className="text-slate-400 text-lg">Books You Could Have Read</div>
            </motion.div>
          </div>
        </div>
      )
    },
    // Fun Facts
    {
      id: 'fun-facts',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Fun Facts
            </h2>
            <p className="text-xl text-slate-300">
              The quirky details that make you unique
            </p>
          </motion.div>
          
          <div className="max-w-4xl mx-auto space-y-6">
            <motion.div
              initial={{ opacity: 0, x: -50 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="stat-card text-center"
            >
              <Sparkles className="h-8 w-8 text-yellow-400 mx-auto mb-4" />
              <p className="text-xl text-slate-200">{wrappedData.funFact}</p>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, x: 50 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.4 }}
              className="stat-card text-center"
            >
              <Calendar className="h-8 w-8 text-primary-400 mx-auto mb-4" />
              <p className="text-xl text-slate-200">Your peak year: {wrappedData.peakYear}</p>
            </motion.div>
            
            <motion.div
              initial={{ opacity: 0, x: -50 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.6 }}
              className="stat-card text-center"
            >
              <Globe className="h-8 w-8 text-soundcloud-400 mx-auto mb-4" />
              <p className="text-xl text-slate-200">{wrappedData.globalTasteComparison}</p>
            </motion.div>
          </div>
        </div>
      )
    },
    // Stories
    {
      id: 'stories',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Music Story
            </h2>
            <p className="text-xl text-slate-300">
              The journey of your musical year
            </p>
          </motion.div>
          
          <div className="max-w-4xl mx-auto space-y-6">
            {wrappedData.stories.map((story, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: index * 0.2 }}
                className="stat-card text-left"
              >
                <p className="text-lg text-slate-200 leading-relaxed">{story}</p>
              </motion.div>
            ))}
          </div>
        </div>
      )
    }
  ]

  const nextSlide = () => {
    setCurrentSlide((prev) => (prev + 1) % slides.length)
    setIsAutoPlaying(false)
  }

  const prevSlide = () => {
    setCurrentSlide((prev) => (prev - 1 + slides.length) % slides.length)
    setIsAutoPlaying(false)
  }

  return (
    <div className="min-h-screen py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Controls */}
        <div className="flex justify-between items-center mb-8">
          <button
            onClick={prevSlide}
            className="flex items-center space-x-2 px-4 py-2 bg-white/5 hover:bg-white/10 rounded-lg transition-colors"
          >
            <ChevronLeft className="h-4 w-4" />
            <span>Previous</span>
          </button>
          
          <div className="flex items-center space-x-2">
            <div className="flex space-x-2">
              {slides.map((_, index) => (
                <button
                  key={index}
                  onClick={() => {
                    setCurrentSlide(index)
                    setIsAutoPlaying(false)
                  }}
                  className={`w-3 h-3 rounded-full transition-colors ${
                    index === currentSlide ? 'bg-primary-500' : 'bg-slate-600'
                  }`}
                />
              ))}
            </div>
            <span className="text-sm text-slate-400 ml-4">
              {currentSlide + 1} of {slides.length}
            </span>
          </div>
          
          <button
            onClick={nextSlide}
            className="flex items-center space-x-2 px-4 py-2 bg-white/5 hover:bg-white/10 rounded-lg transition-colors"
          >
            <span>Next</span>
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>

        {/* Slide Content */}
        <div className="relative">
          <AnimatePresence mode="wait">
            <motion.div
              key={currentSlide}
              initial={{ opacity: 0, x: 50 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -50 }}
              transition={{ duration: 0.5 }}
              className="stat-card min-h-[600px] flex items-center justify-center"
            >
              {slides[currentSlide].component}
            </motion.div>
          </AnimatePresence>
        </div>

        {/* Share Button */}
        <div className="text-center mt-8">
          <button className="btn-primary text-lg px-8 py-4 inline-flex items-center space-x-2">
            <Share2 className="h-5 w-5" />
            <span>Share Your Wrapped</span>
          </button>
        </div>
      </div>
    </div>
  )
}

export default WrappedPage
