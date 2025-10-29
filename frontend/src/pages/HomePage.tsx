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
  ArrowRight
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
      color: 'from-primary-500 to-primary-600'
    },
    {
      icon: MapPin,
      title: 'Music Taste Map',
      description: 'Discover where people with similar music taste live around the world.',
      color: 'from-soundcloud-500 to-soundcloud-600'
    },
    {
      icon: Sparkles,
      title: 'Personalized Wrapped',
      description: 'Your own Spotify Wrapped-style summary with beautiful visualizations.',
      color: 'from-spotify-500 to-spotify-600'
    },
    {
      icon: Users,
      title: 'Community Insights',
      description: 'Compare your taste with friends and discover new music together.',
      color: 'from-purple-500 to-purple-600'
    }
  ]

  const stats = [
    { label: 'Active Users', value: '10K+', icon: Users },
    { label: 'Tracks Analyzed', value: '1M+', icon: Music },
    { label: 'Cities Mapped', value: '500+', icon: MapPin },
    { label: 'Hours Analyzed', value: '50K+', icon: Clock }
  ]

  return (
    <div>
      {/* Hero Section */}
      <section className="relative overflow-hidden pt-20 pb-32">
        {/* Background Effects */}
        <div className="absolute inset-0 bg-gradient-to-br from-dark-900 via-dark-800 to-dark-900" />
        <div className="absolute inset-0 bg-music-pattern opacity-5" />
        
        {/* Floating Elements */}
        <div className="absolute top-20 left-10 w-20 h-20 bg-primary-500/20 rounded-full blur-xl animate-float" />
        <div className="absolute top-40 right-20 w-32 h-32 bg-soundcloud-500/20 rounded-full blur-xl animate-float-delayed" />
        <div className="absolute bottom-20 left-1/4 w-24 h-24 bg-spotify-500/20 rounded-full blur-xl animate-float" />

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
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
              <p className="text-xl md:text-2xl text-slate-300 mb-8 max-w-3xl mx-auto">
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

            {/* Demo Video/Image Placeholder */}
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.8, delay: 0.4 }}
              className="relative max-w-4xl mx-auto"
            >
              <div className="glass rounded-2xl p-8 shadow-2xl">
                <div className="aspect-video bg-gradient-to-br from-primary-500/20 to-soundcloud-500/20 rounded-xl flex items-center justify-center">
                  <div className="text-center">
                    <div className="w-20 h-20 bg-gradient-to-r from-primary-500 to-soundcloud-500 rounded-full flex items-center justify-center mx-auto mb-4 animate-bounce-gentle">
                      <Music className="h-10 w-10 text-white" />
                    </div>
                    <p className="text-slate-300 text-lg">Interactive Dashboard Preview</p>
                    <p className="text-slate-400 text-sm">Connect your account to see your data</p>
                  </div>
                </div>
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="py-16 bg-gradient-to-r from-dark-800/50 to-dark-700/50">
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
                  <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-r from-primary-500 to-soundcloud-500 rounded-full mb-4">
                    <Icon className="h-8 w-8 text-white" />
                  </div>
                  <div className="text-3xl font-bold gradient-text mb-2">{stat.value}</div>
                  <div className="text-slate-400">{stat.label}</div>
                </motion.div>
              )
            })}
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="text-center mb-16"
          >
            <h2 className="section-title">Why Choose SoundWrapped?</h2>
            <p className="text-xl text-slate-300 max-w-3xl mx-auto">
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
                  <h3 className="text-xl font-semibold text-slate-200 mb-3">{feature.title}</h3>
                  <p className="text-slate-400">{feature.description}</p>
                </motion.div>
              )
            })}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 bg-gradient-to-r from-primary-500/10 via-soundcloud-500/10 to-spotify-500/10">
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
            <p className="text-xl text-slate-300 mb-8">
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
