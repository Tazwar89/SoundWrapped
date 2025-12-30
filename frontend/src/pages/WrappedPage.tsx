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
  ChevronRight,
  X,
  Twitter,
  Facebook,
  Linkedin,
  Link as LinkIcon,
  Copy,
  Check,
  Download
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { useMusicData } from '../contexts/MusicDataContext'
import LoadingSpinner from '../components/LoadingSpinner'
import ShareableStoryCard from '../components/ShareableStoryCard'
import { formatNumber, formatHours } from '../utils/formatters'

const WrappedPage: React.FC = () => {
  const { isAuthenticated } = useAuth()
  const { wrappedData, isLoadingWrapped, fetchWrappedData } = useMusicData()
  const [currentSlide, setCurrentSlide] = useState(0)
  const [isAutoPlaying, setIsAutoPlaying] = useState(true)
  const [isShareModalOpen, setIsShareModalOpen] = useState(false)
  const [isStoryCardOpen, setIsStoryCardOpen] = useState(false)
  const [copiedToClipboard, setCopiedToClipboard] = useState(false)

  useEffect(() => {
    if (isAuthenticated) {
      console.log('Fetching wrapped data...')
      fetchWrappedData()
    }
  }, [isAuthenticated, fetchWrappedData])

  useEffect(() => {
    console.log('Wrapped data updated:', wrappedData)
  }, [wrappedData])

  // Define slides array - needs to be accessible for useEffect
  const slides = wrappedData ? [
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
            <div className="w-32 h-32 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full flex items-center justify-center mx-auto mb-6">
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
                key={`${track.title}-${track.artist}-${index}`}
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
                className="flex items-center space-x-6 p-6 rounded-xl bg-gradient-to-r from-orange-500/10 to-orange-600/10 hover:from-orange-500/20 hover:to-orange-600/20 transition-all mb-4"
              >
                <div className="w-16 h-16 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full flex items-center justify-center text-white font-bold text-xl">
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
    // Support the Underground
    {
      id: 'underground-support',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Support the Underground
            </h2>
            <p className="text-xl text-slate-300">
              Your dedication to independent artists
            </p>
          </motion.div>
          
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="max-w-2xl mx-auto"
          >
            <div className="stat-card text-center">
              <div className="text-7xl font-bold gradient-text mb-4">
                {wrappedData.undergroundSupportPercentage?.toFixed(1) || '0.0'}%
              </div>
              <div className="text-slate-300 text-xl mb-4">
                of your listening time went to artists with fewer than 5,000 followers
              </div>
              <div className="text-slate-400 text-lg">
                {wrappedData.undergroundSupportPercentage && wrappedData.undergroundSupportPercentage >= 50 
                  ? "🎵 You're a true champion of the underground scene!"
                  : wrappedData.undergroundSupportPercentage && wrappedData.undergroundSupportPercentage >= 25
                  ? "🎸 You have great taste in discovering new artists!"
                  : "🎶 Every artist starts somewhere—thanks for supporting them!"}
              </div>
            </div>
          </motion.div>
        </div>
      )
    },
    // Year in Review Poetry
    {
      id: 'year-in-review',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Year in Music
            </h2>
            <p className="text-xl text-slate-300">
              A poetic reflection of your musical journey
            </p>
          </motion.div>
          
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="max-w-3xl mx-auto"
          >
            <div className="stat-card text-center p-8">
              <Sparkles className="h-12 w-12 text-yellow-400 mx-auto mb-6" />
              <div className="text-lg md:text-xl text-slate-200 leading-relaxed whitespace-pre-line">
                {wrappedData.yearInReviewPoetry || "Your musical year was a symphony of discovery, each track a note in the melody of your year."}
              </div>
            </div>
          </motion.div>
        </div>
      )
    },
    // Phase 2: The Trendsetter (Early Adopter) Score
    {
      id: 'trendsetter',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              The Trendsetter
            </h2>
            <p className="text-xl text-slate-300">
              Your early adopter score
            </p>
          </motion.div>
          
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="max-w-3xl mx-auto"
          >
            <div className="stat-card text-center p-8">
              <div className="text-6xl font-bold gradient-text mb-4">
                {wrappedData.trendsetterScore?.badge || 'Listener'}
              </div>
              <div className="text-3xl font-semibold text-slate-200 mb-4">
                Score: {wrappedData.trendsetterScore?.score || 0}
              </div>
              <div className="text-lg text-slate-300 mb-6 leading-relaxed">
                {wrappedData.trendsetterScore?.description || "Keep exploring to discover your trendsetter potential!"}
              </div>
              {wrappedData.trendsetterScore && (wrappedData.trendsetterScore.visionaryTracks || 0) > 0 && (
                <div className="text-slate-400 text-sm">
                  🎯 {wrappedData.trendsetterScore.visionaryTracks} visionary tracks • {wrappedData.trendsetterScore.earlyAdopterTracks || 0} early discoveries
                </div>
              )}
            </div>
          </motion.div>
        </div>
      )
    },
    // Phase 2: The Repost King/Queen
    {
      id: 'repost-king',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              The Repost King/Queen
            </h2>
            <p className="text-xl text-slate-300">
              Your repost influence
            </p>
          </motion.div>
          
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="max-w-3xl mx-auto"
          >
            <div className="stat-card text-center p-8">
              <div className="text-6xl font-bold gradient-text mb-4">
                {wrappedData.repostKingScore?.badge || 'Listener'}
              </div>
              <div className="text-4xl font-semibold text-slate-200 mb-2">
                {wrappedData.repostKingScore?.trendingTracks || 0}
              </div>
              <div className="text-lg text-slate-300 mb-2">
                of {wrappedData.repostKingScore?.repostedTracks || 0} reposts became trending
              </div>
              {wrappedData.repostKingScore && wrappedData.repostKingScore.repostedTracks > 0 && (
                <div className="text-2xl font-bold text-orange-400 mb-6">
                  {wrappedData.repostKingScore.percentage.toFixed(1)}% success rate
                </div>
              )}
              <div className="text-lg text-slate-300 leading-relaxed">
                {wrappedData.repostKingScore?.description || "Start reposting tracks you love to become a Repost King/Queen!"}
              </div>
            </div>
          </motion.div>
        </div>
      )
    },
    // Phase 2: The Sonic Archetype
    {
      id: 'sonic-archetype',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Sonic Archetype
            </h2>
            <p className="text-xl text-slate-300">
              Your musical persona
            </p>
          </motion.div>
          
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="max-w-3xl mx-auto"
          >
            <div className="stat-card text-center p-8">
              <Sparkles className="h-12 w-12 text-purple-400 mx-auto mb-6" />
              <div className="text-lg md:text-xl text-slate-200 leading-relaxed whitespace-pre-line">
                {wrappedData.sonicArchetype || "The Musical Explorer - You're on a journey through sound, discovering new artists and genres with an open heart and curious ears."}
              </div>
            </div>
          </motion.div>
        </div>
      )
    },
    // Music Age (Old Soul / Young at Heart)
    {
      id: 'music-age',
      component: (
        <div className="text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="mb-8"
          >
            <h2 className="text-4xl md:text-6xl font-bold gradient-text mb-6">
              Your Music Age
            </h2>
            <p className="text-xl text-slate-300">
              Are you an old soul or young at heart?
            </p>
          </motion.div>
          
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.3 }}
            className="max-w-3xl mx-auto"
          >
            <div className="stat-card text-center p-8">
              <Clock className="h-12 w-12 text-amber-400 mx-auto mb-6" />
              <div className="text-lg md:text-xl text-slate-200 leading-relaxed whitespace-pre-line">
                {wrappedData.musicAge || "The Timeless Listener - Your music taste spans generations, finding beauty in both classic melodies and modern beats."}
              </div>
            </div>
          </motion.div>
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
  ] : []

  useEffect(() => {
    if (!wrappedData || !isAutoPlaying) return

    const interval = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % slides.length)
    }, 5000)

    return () => clearInterval(interval)
  }, [wrappedData, isAutoPlaying, slides.length])

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">Please Log In</h1>
          <p className="text-slate-300 mb-8">Connect your account to view your wrapped</p>
        </div>
      </div>
    )
  }

  if (isLoadingWrapped) {
    return (
      <div className="flex items-center justify-center py-20">
        <LoadingSpinner />
      </div>
    )
  }

  if (!wrappedData) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">No Data Available</h1>
          <p className="text-slate-300 mb-8">We couldn't find any wrapped data for your account</p>
        </div>
      </div>
    )
  }

  const nextSlide = () => {
    setCurrentSlide((prev) => (prev + 1) % slides.length)
    setIsAutoPlaying(false)
  }

  const prevSlide = () => {
    setCurrentSlide((prev) => (prev - 1 + slides.length) % slides.length)
    setIsAutoPlaying(false)
  }

  const getShareUrl = () => {
    return window.location.href
  }

  const getShareText = () => {
    return `Check out my 2024 SoundWrapped! 🎵`
  }

  const handleShare = (platform: string) => {
    const url = encodeURIComponent(getShareUrl())
    const text = encodeURIComponent(getShareText())
    
    let shareUrl = ''
    
    switch (platform) {
      case 'twitter':
        shareUrl = `https://twitter.com/intent/tweet?text=${text}&url=${url}`
        break
      case 'facebook':
        shareUrl = `https://www.facebook.com/sharer/sharer.php?u=${url}`
        break
      case 'linkedin':
        shareUrl = `https://www.linkedin.com/sharing/share-offsite/?url=${url}`
        break
      case 'copy':
        navigator.clipboard.writeText(getShareUrl())
        setCopiedToClipboard(true)
        setTimeout(() => setCopiedToClipboard(false), 2000)
        return
      default:
        return
    }
    
    if (shareUrl) {
      window.open(shareUrl, '_blank', 'width=600,height=400')
    }
  }

  console.log('WrappedPage rendering, wrappedData:', wrappedData, 'isLoadingWrapped:', isLoadingWrapped, 'isAuthenticated:', isAuthenticated)
  
  return (
    <div className="py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Controls */}
        <div className="flex justify-between items-center mb-8">
          <button
            onClick={prevSlide}
            className="flex items-center space-x-2 px-4 py-2 bg-gradient-to-r from-orange-500/20 to-orange-600/20 hover:from-orange-500/30 hover:to-orange-600/30 border border-orange-500/30 rounded-lg transition-all text-white"
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
                    index === currentSlide ? 'bg-orange-500' : 'bg-black'
                  }`}
                />
              ))}
            </div>
            <span className="text-sm text-orange-300 font-medium ml-4">
              {currentSlide + 1} of {slides.length}
            </span>
          </div>
          
          <button
            onClick={nextSlide}
            className="flex items-center space-x-2 px-4 py-2 bg-gradient-to-r from-orange-500/20 to-orange-600/20 hover:from-orange-500/30 hover:to-orange-600/30 border border-orange-500/30 rounded-lg transition-all text-white"
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

        {/* Share Buttons */}
        <div className="text-center mt-8 space-x-4">
          <button 
            onClick={() => setIsStoryCardOpen(true)}
            className="btn-primary text-lg px-8 py-4 inline-flex items-center space-x-2"
          >
            <Download className="h-5 w-5" />
            <span>Download Story Card</span>
          </button>
          <button 
            onClick={() => setIsShareModalOpen(true)}
            className="btn-secondary text-lg px-8 py-4 inline-flex items-center space-x-2"
          >
            <Share2 className="h-5 w-5" />
            <span>Share Link</span>
          </button>
        </div>
      </div>

      {/* Share Modal */}
      <AnimatePresence>
        {isShareModalOpen && (
          <>
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsShareModalOpen(false)}
              className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50"
            />
            
            {/* Modal */}
            <motion.div
              initial={{ opacity: 0, scale: 0.9, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.9, y: 20 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-4"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="bg-slate-900 rounded-2xl shadow-2xl max-w-md w-full p-6 border border-slate-700">
                {/* Header */}
                <div className="flex items-center justify-between mb-6">
                  <h3 className="text-2xl font-bold gradient-text">Share Your Wrapped</h3>
                  <button
                    onClick={() => setIsShareModalOpen(false)}
                    className="p-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
                  >
                    <X className="h-5 w-5 text-slate-400" />
                  </button>
                </div>

                {/* Share Options */}
                <div className="grid grid-cols-2 gap-4">
                  {/* Twitter */}
                  <button
                    onClick={() => handleShare('twitter')}
                    className="flex flex-col items-center justify-center p-6 rounded-xl bg-white/5 hover:bg-white/10 transition-all border border-slate-700 hover:border-blue-500/50 group"
                  >
                    <div className="w-12 h-12 rounded-full bg-blue-500/20 flex items-center justify-center mb-3 group-hover:bg-blue-500/30 transition-colors">
                      <Twitter className="h-6 w-6 text-blue-400" />
                    </div>
                    <span className="text-slate-200 font-medium">Twitter</span>
                  </button>

                  {/* Facebook */}
                  <button
                    onClick={() => handleShare('facebook')}
                    className="flex flex-col items-center justify-center p-6 rounded-xl bg-white/5 hover:bg-white/10 transition-all border border-slate-700 hover:border-blue-600/50 group"
                  >
                    <div className="w-12 h-12 rounded-full bg-blue-600/20 flex items-center justify-center mb-3 group-hover:bg-blue-600/30 transition-colors">
                      <Facebook className="h-6 w-6 text-blue-500" />
                    </div>
                    <span className="text-slate-200 font-medium">Facebook</span>
                  </button>

                  {/* LinkedIn */}
                  <button
                    onClick={() => handleShare('linkedin')}
                    className="flex flex-col items-center justify-center p-6 rounded-xl bg-white/5 hover:bg-white/10 transition-all border border-slate-700 hover:border-blue-700/50 group"
                  >
                    <div className="w-12 h-12 rounded-full bg-blue-700/20 flex items-center justify-center mb-3 group-hover:bg-blue-700/30 transition-colors">
                      <Linkedin className="h-6 w-6 text-blue-600" />
                    </div>
                    <span className="text-slate-200 font-medium">LinkedIn</span>
                  </button>

                  {/* Copy Link */}
                  <button
                    onClick={() => handleShare('copy')}
                    className="flex flex-col items-center justify-center p-6 rounded-xl bg-white/5 hover:bg-white/10 transition-all border border-slate-700 hover:border-orange-500/50 group"
                  >
                    <div className={`w-12 h-12 rounded-full flex items-center justify-center mb-3 transition-colors ${
                      copiedToClipboard 
                        ? 'bg-green-500/30' 
                        : 'bg-orange-500/20 group-hover:bg-orange-500/30'
                    }`}>
                      {copiedToClipboard ? (
                        <Check className="h-6 w-6 text-green-400" />
                      ) : (
                        <Copy className="h-6 w-6 text-orange-400" />
                      )}
                    </div>
                    <span className="text-slate-200 font-medium">
                      {copiedToClipboard ? 'Copied!' : 'Copy Link'}
                    </span>
                  </button>
                </div>

                {/* Share URL Display */}
                <div className="mt-6 p-4 bg-black/30 rounded-lg border border-slate-700">
                  <div className="flex items-center space-x-2 text-sm text-slate-400 mb-2">
                    <LinkIcon className="h-4 w-4" />
                    <span>Share URL</span>
                  </div>
                  <div className="text-slate-200 text-sm break-all font-mono">
                    {getShareUrl()}
                  </div>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* Story Card Modal */}
      {isStoryCardOpen && wrappedData && (
        <ShareableStoryCard
          wrappedData={wrappedData}
          onClose={() => setIsStoryCardOpen(false)}
        />
      )}
    </div>
  )
}

export default WrappedPage
