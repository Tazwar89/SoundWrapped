import React, { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Menu, X, Music, User, BarChart3, MapPin, Home } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { cn } from '../utils/cn'

const Header: React.FC = () => {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)
  const { user, isAuthenticated, logout } = useAuth()
  const location = useLocation()

  const navigation = [
    { name: 'Home', href: '/', icon: Home },
    { name: 'Dashboard', href: '/dashboard', icon: BarChart3 },
    { name: 'Wrapped', href: '/wrapped', icon: Music },
    { name: 'Music Map', href: '/music-taste-map', icon: MapPin },
  ]

  const isActive = (path: string) => location.pathname === path

  return (
    <header className="sticky top-0 z-50 bg-black/95 backdrop-blur-md border-b border-white/10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* Logo */}
          <Link to="/" className="flex items-center space-x-2 group">
            <div className="relative">
              <Music className="h-8 w-8 text-orange-500 group-hover:text-orange-400 transition-colors" />
              <div className="absolute -top-1 -right-1 h-3 w-3 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full animate-pulse" />
            </div>
            <span className="text-xl font-bold gradient-text">SoundWrapped</span>
          </Link>

          {/* Desktop Navigation */}
          <nav className="hidden md:flex space-x-8">
            {navigation.map((item) => {
              const Icon = item.icon
              return (
                <Link
                  key={item.name}
                  to={item.href}
                  onMouseEnter={() => {
                    // Prefetch route on hover
                    if (item.href === '/dashboard') {
                      import('../pages/DashboardPage')
                    } else if (item.href === '/wrapped') {
                      import('../pages/WrappedPage')
                    } else if (item.href === '/music-taste-map') {
                      import('../pages/MusicTasteMapPage')
                    }
                  }}
                  className={cn(
                    'flex items-center space-x-2 px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200',
                    isActive(item.href)
                      ? 'bg-orange-500/20 text-orange-400'
                      : 'text-slate-300 hover:text-white hover:bg-white/5'
                  )}
                >
                  <Icon className="h-4 w-4" />
                  <span>{item.name}</span>
                </Link>
              )
            })}
          </nav>

          {/* User Menu / Auth Buttons */}
          <div className="flex items-center space-x-4">
            {isAuthenticated ? (
              <div className="flex items-center space-x-4">
                {/* User Profile */}
                <Link
                  to="/profile"
                  className="flex items-center space-x-2 px-3 py-2 rounded-lg hover:bg-white/5 transition-colors"
                >
                  {user?.profileImage ? (
                    <img
                      src={user.profileImage}
                      alt={user.username}
                      className="h-8 w-8 rounded-full object-cover"
                    />
                  ) : (
                    <div className="h-8 w-8 rounded-full bg-gradient-to-r from-orange-500 to-orange-600 flex items-center justify-center">
                      <User className="h-4 w-4 text-white" />
                    </div>
                  )}
                  <span className="hidden sm:block text-sm font-medium text-slate-200">
                    {user?.username}
                  </span>
                </Link>

                {/* Logout Button */}
                <button
                  onClick={logout}
                  className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white hover:bg-white/5 rounded-lg transition-colors"
                >
                  Logout
                </button>
              </div>
            ) : (
              <div className="flex items-center space-x-2">
                <Link
                  to="/"
                  className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white transition-colors"
                >
                  Login
                </Link>
              </div>
            )}

            {/* Mobile menu button */}
            <button
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="md:hidden p-2 rounded-lg text-slate-300 hover:text-white hover:bg-white/5 transition-colors"
            >
              {isMobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Navigation */}
      <AnimatePresence>
        {isMobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="md:hidden bg-black/95 backdrop-blur-md border-t border-white/10"
          >
            <div className="px-4 py-4 space-y-2">
              {navigation.map((item) => {
                const Icon = item.icon
                return (
                  <Link
                    key={item.name}
                    to={item.href}
                    onMouseEnter={() => {
                      // Prefetch route on hover
                      if (item.href === '/dashboard') {
                        import('../pages/DashboardPage')
                      } else if (item.href === '/wrapped') {
                        import('../pages/WrappedPage')
                      } else if (item.href === '/music-taste-map') {
                        import('../pages/MusicTasteMapPage')
                      }
                    }}
                    onClick={() => setIsMobileMenuOpen(false)}
                    className={cn(
                      'flex items-center space-x-3 px-3 py-3 rounded-lg text-sm font-medium transition-all duration-200',
                      isActive(item.href)
                        ? 'bg-orange-500/20 text-orange-400'
                        : 'text-slate-300 hover:text-white hover:bg-white/5'
                    )}
                  >
                    <Icon className="h-5 w-5" />
                    <span>{item.name}</span>
                  </Link>
                )
              })}
              
              {isAuthenticated && (
                <div className="pt-4 border-t border-white/10">
                  <button
                    onClick={() => {
                      logout()
                      setIsMobileMenuOpen(false)
                    }}
                    className="w-full flex items-center space-x-3 px-3 py-3 rounded-lg text-sm font-medium text-slate-300 hover:text-white hover:bg-white/5 transition-colors"
                  >
                    <X className="h-5 w-5" />
                    <span>Logout</span>
                  </button>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  )
}

export default Header
