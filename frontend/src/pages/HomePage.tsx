import React, { useEffect } from 'react'
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

const HomePage: React.FC = () => {
  const { isAuthenticated, login } = useAuth()
  const [searchParams] = useSearchParams()

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
      icon: Sparkles,
      title: 'Personalized Wrapped',
      description: 'Your own SoundCloud Playback-style summary with beautiful visualizations.',
      color: 'from-orange-500 to-orange-600'
    },
    {
      icon: Users,
      title: 'Community Insights',
      description: 'Compare your taste with friends and discover new music together.',
      color: 'from-orange-500 to-orange-600'
    }
  ]

  const stats = [
    { label: 'Active Users', value: '10K+', icon: Users },
    { label: 'Tracks Analyzed', value: '1M+', icon: Music },
    { label: 'Cities Mapped', value: '500+', icon: MapPin },
    { label: 'Hours Analyzed', value: '50K+', icon: Clock }
  ]

  return (
    <div className="relative">
      {/* Hero Section */}
      <section className="relative overflow-hidden pt-20 pb-32 min-h-screen">
        <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8 }}
              className="mb-8"
            >
              <h1 className="text-5xl md:text-7xl font-bold mb-6">
                <span className="gradient-text">SoundWrapped</span>
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

            {/* Music Discovery Sections */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-6xl mx-auto mb-12">
              {/* Artist of the Day */}
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.2 }}
                className="stat-card"
              >
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-semibold text-orange-400 uppercase tracking-wide">Artist of the Day</h3>
                  <Star className="h-4 w-4 text-orange-500" />
                </div>
                <div className="text-center">
                  <div className="w-20 h-20 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full flex items-center justify-center mx-auto mb-3">
                    <Music className="h-10 w-10 text-white" />
                  </div>
                  <h4 className="text-lg font-bold text-white mb-2">Discover New Artists</h4>
                  <p className="text-sm text-white/70 mb-4">Explore SoundCloud's diverse music community</p>
                  {isAuthenticated ? (
                    <Link to="/dashboard" className="text-xs text-orange-400 hover:text-orange-300 transition-colors">
                      View Your Top Artists →
                    </Link>
                  ) : (
                    <button
                      onClick={() => login('soundcloud')}
                      className="text-xs text-orange-400 hover:text-orange-300 transition-colors"
                    >
                      Sign in to discover →
                    </button>
                  )}
                </div>
              </motion.div>

              {/* Song of the Day */}
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
                  <div className="w-20 h-20 bg-gradient-to-r from-orange-500 to-orange-600 rounded-lg flex items-center justify-center mx-auto mb-3 shadow-lg shadow-orange-500/30">
                    <Headphones className="h-10 w-10 text-white" />
                  </div>
                  <h4 className="text-lg font-bold text-white mb-2">Featured Track</h4>
                  <p className="text-sm text-white/70 mb-4">Discover trending sounds on SoundCloud</p>
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

              {/* Genre of the Day */}
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.4 }}
                className="stat-card"
              >
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-semibold text-orange-400 uppercase tracking-wide">Genre of the Day</h3>
                  <Sparkles className="h-4 w-4 text-orange-500" />
                </div>
                <div className="text-center">
                  <div className="w-20 h-20 bg-gradient-to-r from-orange-500 to-orange-600 rounded-xl flex items-center justify-center mx-auto mb-3">
                    <Sparkles className="h-10 w-10 text-white" />
                  </div>
                  <h4 className="text-lg font-bold text-white mb-2">Explore Genres</h4>
                  <p className="text-sm text-white/70 mb-4">Dive into new musical styles and sounds</p>
                  {isAuthenticated ? (
                    <Link to="/dashboard" className="text-xs text-orange-400 hover:text-orange-300 transition-colors">
                      See Your Genres →
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

            {/* Popular Now Section */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.5 }}
              className="max-w-6xl mx-auto"
            >
              <div className="stat-card">
                <div className="flex items-center justify-between mb-6">
                  <div>
                    <h3 className="text-xl font-bold text-white mb-1">Popular Now</h3>
                    <p className="text-sm text-white/70">What the SoundCloud community is listening to</p>
                  </div>
                  <TrendingUp className="h-6 w-6 text-orange-500" />
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  {[1, 2, 3, 4].map((item) => (
                    <div
                      key={item}
                      className="p-4 bg-black/10 rounded-lg border border-white/5 hover:border-orange-500/30 transition-all group"
                    >
                      <div className="flex items-center space-x-3 mb-2">
                        <div className="w-12 h-12 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-lg flex items-center justify-center">
                          <Music className="h-6 w-6 text-orange-400" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-white truncate">Track Title {item}</p>
                          <p className="text-xs text-white/60 truncate">Artist Name</p>
                        </div>
                      </div>
                      <div className="flex items-center justify-between text-xs text-white/50">
                        <span>🎵 Trending</span>
                        <span>→</span>
                      </div>
                    </div>
                  ))}
                </div>
                {isAuthenticated ? (
                  <div className="mt-6 text-center">
                    <Link
                      to="/dashboard"
                      className="inline-flex items-center space-x-2 text-orange-400 hover:text-orange-300 transition-colors text-sm font-medium"
                    >
                      <span>View Your Music Stats</span>
                      <ArrowRight className="h-4 w-4" />
                    </Link>
                  </div>
                ) : (
                  <div className="mt-6 text-center">
                    <button
                      onClick={() => login('soundcloud')}
                      className="inline-flex items-center space-x-2 text-orange-400 hover:text-orange-300 transition-colors text-sm font-medium"
                    >
                      <span>Sign in to see your personalized stats</span>
                      <ArrowRight className="h-4 w-4" />
                    </button>
                  </div>
                )}
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="relative z-10 py-16">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            {stats.map((stat, index) => {
              const Icon = stat.icon
              return (
                <motion.div
                  key={stat.label}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.6, delay: index * 0.1 }}
                  className="text-center"
                >
                  <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full mb-4">
                    <Icon className="h-8 w-8 text-white" />
                  </div>
                  <div className="text-3xl font-bold gradient-text mb-2">{stat.value}</div>
                  <div className="text-white/80">{stat.label}</div>
                </motion.div>
              )
            })}
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="relative z-10 py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="text-center mb-16"
          >
            <h2 className="section-title">Why Choose SoundWrapped?</h2>
            <p className="text-xl text-white/90 max-w-3xl mx-auto font-medium">
              Get deeper insights into your music taste with our comprehensive analytics and beautiful visualizations.
            </p>
          </motion.div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
            {features.map((feature, index) => {
              const Icon = feature.icon
              return (
                <motion.div
                  key={feature.title}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.6, delay: index * 0.1 }}
                  className="stat-card group"
                >
                  <div className={`inline-flex items-center justify-center w-12 h-12 bg-gradient-to-r ${feature.color} rounded-lg mb-4 group-hover:scale-110 transition-transform`}>
                    <Icon className="h-6 w-6 text-white" />
                  </div>
                  <h3 className="text-xl font-semibold text-white mb-3">{feature.title}</h3>
                  <p className="text-white/80">{feature.description}</p>
                </motion.div>
              )
            })}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="relative z-10 py-20">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
          >
            <h2 className="text-4xl md:text-5xl font-bold mb-6">
              Ready to Discover Your
              <span className="gradient-text"> Music Story?</span>
            </h2>
            <p className="text-xl text-white/90 mb-8 font-medium">
              Connect your SoundCloud account and get started with your personalized music insights.
            </p>
            {!isAuthenticated && (
              <div className="flex justify-center">
                <button
                  onClick={() => login('soundcloud')}
                  className="btn-secondary text-lg px-8 py-4 inline-flex items-center justify-center group"
                >
                  <Headphones className="h-5 w-5 mr-2" />
                  Connect to SoundCloud
                  <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
                </button>
              </div>
            )}
          </motion.div>
        </div>
      </section>
    </div>
  )
}

export default HomePage
