import React, { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { MapPin, Users, Globe, TrendingUp, Heart, X } from 'lucide-react'
import { useMusicData } from '../contexts/MusicDataContext'
import { useAuth } from '../contexts/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'
import { cn } from '../utils/cn'

const MusicTasteMapPage: React.FC = () => {
  const { isAuthenticated } = useAuth()
  const { musicTasteLocations, isLoadingMusicTasteMap, fetchMusicTasteMap } = useMusicData()
  const [selectedLocation, setSelectedLocation] = useState<number | null>(null)
  const [hoveredLocation, setHoveredLocation] = useState<number | null>(null)

  useEffect(() => {
    if (isAuthenticated) {
      fetchMusicTasteMap()
    }
  }, [isAuthenticated, fetchMusicTasteMap])

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">Please Log In</h1>
          <p className="text-slate-300 mb-8">Connect your account to view the music taste map</p>
        </div>
      </div>
    )
  }

  const getSimilarityColor = (similarity: number) => {
    if (similarity >= 0.8) return 'from-green-500 to-emerald-500'
    if (similarity >= 0.7) return 'from-yellow-500 to-orange-500'
    if (similarity >= 0.6) return 'from-orange-500 to-red-500'
    return 'from-red-500 to-pink-500'
  }

  const getSimilarityText = (similarity: number) => {
    if (similarity >= 0.8) return 'Very High'
    if (similarity >= 0.7) return 'High'
    if (similarity >= 0.6) return 'Medium'
    return 'Low'
  }

  return (
    <div className="py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className="text-center mb-12"
        >
          <h1 className="text-4xl md:text-5xl font-bold gradient-text mb-4">
            Your Music Taste Map
          </h1>
          <p className="text-xl text-slate-300 max-w-3xl mx-auto">
            Discover where people with similar music taste live around the world. 
            Your musical DNA connects you to listeners across the globe.
          </p>
        </motion.div>

        {/* Stats Overview */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.2 }}
          className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-12"
        >
          <div className="stat-card text-center">
            <Globe className="h-8 w-8 text-primary-400 mx-auto mb-3" />
            <div className="text-2xl font-bold text-slate-200 mb-1">
              {musicTasteLocations.length}
            </div>
            <div className="text-slate-400">Cities Mapped</div>
          </div>
          <div className="stat-card text-center">
            <Users className="h-8 w-8 text-soundcloud-400 mx-auto mb-3" />
            <div className="text-2xl font-bold text-slate-200 mb-1">
              {musicTasteLocations.reduce((acc, loc) => acc + loc.userCount, 0).toLocaleString()}
            </div>
            <div className="text-slate-400">Similar Listeners</div>
          </div>
          <div className="stat-card text-center">
            <TrendingUp className="h-8 w-8 text-spotify-400 mx-auto mb-3" />
            <div className="text-2xl font-bold text-slate-200 mb-1">
              {musicTasteLocations.length > 0 ? Math.round(musicTasteLocations.reduce((acc, loc) => acc + loc.similarity, 0) / musicTasteLocations.length * 100) : 0}%
            </div>
            <div className="text-slate-400">Avg Similarity</div>
          </div>
          <div className="stat-card text-center">
            <Heart className="h-8 w-8 text-pink-400 mx-auto mb-3" />
            <div className="text-2xl font-bold text-slate-200 mb-1">
              {musicTasteLocations.length > 0 ? musicTasteLocations[0].city : 'N/A'}
            </div>
            <div className="text-slate-400">Most Similar City</div>
          </div>
        </motion.div>

        {/* Map Visualization */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, delay: 0.4 }}
          className="stat-card mb-8"
        >
          <h3 className="subsection-title mb-6">Interactive Music Taste Map</h3>
          
          {isLoadingMusicTasteMap ? (
            <LoadingSpinner />
          ) : (
            <div className="relative">
              {/* World Map Background */}
              <div className="w-full h-96 bg-gradient-to-br from-slate-800 to-slate-900 rounded-xl overflow-hidden relative">
                <div className="absolute inset-0 bg-music-pattern opacity-10" />
                
                {/* Map Points */}
                {musicTasteLocations.map((location, index) => {
                  const isSelected = selectedLocation === index
                  const isHovered = hoveredLocation === index
                  
                  return (
                    <motion.div
                      key={location.city}
                      className="absolute transform -translate-x-1/2 -translate-y-1/2 cursor-pointer"
                      style={{
                        left: `${20 + (index * 15) % 60}%`,
                        top: `${30 + (index * 20) % 40}%`
                      }}
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      transition={{ duration: 0.5, delay: index * 0.1 }}
                      whileHover={{ scale: 1.2 }}
                      onClick={() => setSelectedLocation(isSelected ? null : index)}
                      onMouseEnter={() => setHoveredLocation(index)}
                      onMouseLeave={() => setHoveredLocation(null)}
                    >
                      {/* Pulse Ring */}
                      <div className={cn(
                        'absolute inset-0 rounded-full animate-ping',
                        `bg-gradient-to-r ${getSimilarityColor(location.similarity)}`,
                        'opacity-20'
                      )} />
                      
                      {/* Main Point */}
                      <div className={cn(
                        'relative w-6 h-6 rounded-full border-2 border-white shadow-lg',
                        `bg-gradient-to-r ${getSimilarityColor(location.similarity)}`,
                        isSelected && 'ring-4 ring-white/30',
                        isHovered && 'scale-125'
                      )}>
                        <div className="absolute inset-0 rounded-full bg-white/20" />
                      </div>
                      
                      {/* City Label */}
                      <div className={cn(
                        'absolute top-8 left-1/2 transform -translate-x-1/2 whitespace-nowrap',
                        'px-2 py-1 rounded text-xs font-medium text-white',
                        `bg-gradient-to-r ${getSimilarityColor(location.similarity)}`,
                        'opacity-0 group-hover:opacity-100 transition-opacity',
                        (isSelected || isHovered) && 'opacity-100'
                      )}>
                        {location.city}
                      </div>
                    </motion.div>
                  )
                })}
              </div>
              
              {/* Legend */}
              <div className="mt-6 flex flex-wrap justify-center gap-4">
                <div className="flex items-center space-x-2">
                  <div className="w-3 h-3 rounded-full bg-gradient-to-r from-green-500 to-emerald-500" />
                  <span className="text-sm text-slate-400">Very High (80%+)</span>
                </div>
                <div className="flex items-center space-x-2">
                  <div className="w-3 h-3 rounded-full bg-gradient-to-r from-yellow-500 to-orange-500" />
                  <span className="text-sm text-slate-400">High (70-79%)</span>
                </div>
                <div className="flex items-center space-x-2">
                  <div className="w-3 h-3 rounded-full bg-gradient-to-r from-orange-500 to-red-500" />
                  <span className="text-sm text-slate-400">Medium (60-69%)</span>
                </div>
                <div className="flex items-center space-x-2">
                  <div className="w-3 h-3 rounded-full bg-gradient-to-r from-red-500 to-pink-500" />
                  <span className="text-sm text-slate-400">Low (&lt;60%)</span>
                </div>
              </div>
            </div>
          )}
        </motion.div>

        {/* Location Details */}
        {selectedLocation !== null && musicTasteLocations[selectedLocation] && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="stat-card"
          >
            <div className="flex items-start space-x-6">
              {/* Location Info */}
              <div className="flex-1">
                <div className="flex items-center space-x-3 mb-4">
                  <MapPin className="h-6 w-6 text-primary-400" />
                  <h3 className="text-2xl font-bold text-slate-200">
                    {musicTasteLocations[selectedLocation].city}, {musicTasteLocations[selectedLocation].country}
                  </h3>
                </div>
                
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                  <div>
                    <div className="text-sm text-slate-400 mb-1">Similarity Score</div>
                    <div className={cn(
                      'text-2xl font-bold',
                      `bg-gradient-to-r ${getSimilarityColor(musicTasteLocations[selectedLocation].similarity)} bg-clip-text text-transparent`
                    )}>
                      {(musicTasteLocations[selectedLocation].similarity * 100).toFixed(1)}%
                    </div>
                    <div className="text-sm text-slate-400">
                      {getSimilarityText(musicTasteLocations[selectedLocation].similarity)} similarity
                    </div>
                  </div>
                  
                  <div>
                    <div className="text-sm text-slate-400 mb-1">Similar Listeners</div>
                    <div className="text-2xl font-bold text-slate-200">
                      {musicTasteLocations[selectedLocation].userCount.toLocaleString()}
                    </div>
                    <div className="text-sm text-slate-400">people in this city</div>
                  </div>
                  
                  <div>
                    <div className="text-sm text-slate-400 mb-1">Top Genres</div>
                    <div className="flex flex-wrap gap-1">
                      {musicTasteLocations[selectedLocation].topGenres.map((genre) => (
                        <span
                          key={genre}
                          className="px-2 py-1 bg-gradient-to-r from-primary-500/20 to-soundcloud-500/20 text-primary-300 text-xs rounded-full"
                        >
                          {genre}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
                
                <div className="text-slate-300">
                  <p>
                    People in {musicTasteLocations[selectedLocation].city} share a{' '}
                    <span className="font-semibold text-primary-300">
                      {getSimilarityText(musicTasteLocations[selectedLocation].similarity).toLowerCase()}
                    </span>{' '}
                    similarity with your music taste. They particularly enjoy{' '}
                    <span className="font-semibold text-soundcloud-300">
                      {musicTasteLocations[selectedLocation].topGenres.slice(0, 2).join(' and ')}
                    </span>{' '}
                    music, just like you!
                  </p>
                </div>
              </div>
              
              {/* Close Button */}
              <button
                onClick={() => setSelectedLocation(null)}
                className="p-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
              >
                <X className="h-5 w-5 text-slate-400" />
              </button>
            </div>
          </motion.div>
        )}

        {/* All Locations List */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.6 }}
          className="mt-12"
        >
          <h3 className="subsection-title mb-6">All Similar Locations</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {musicTasteLocations.map((location, index) => (
              <motion.div
                key={location.city}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                className={cn(
                  'music-card cursor-pointer group',
                  selectedLocation === index && 'ring-2 ring-primary-500/50'
                )}
                onClick={() => setSelectedLocation(selectedLocation === index ? null : index)}
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center space-x-3">
                    <MapPin className="h-5 w-5 text-primary-400" />
                    <div>
                      <h4 className="font-semibold text-slate-200 group-hover:text-white transition-colors">
                        {location.city}
                      </h4>
                      <p className="text-sm text-slate-400">{location.country}</p>
                    </div>
                  </div>
                  <div className={cn(
                    'px-3 py-1 rounded-full text-xs font-medium',
                    `bg-gradient-to-r ${getSimilarityColor(location.similarity)} text-white`
                  )}>
                    {(location.similarity * 100).toFixed(0)}%
                  </div>
                </div>
                
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-400">Similar Listeners</span>
                    <span className="text-slate-200 font-medium">{location.userCount.toLocaleString()}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-400">Top Genres</span>
                    <div className="flex space-x-1">
                      {location.topGenres.slice(0, 2).map((genre) => (
                        <span key={genre} className="text-xs px-1 py-0.5 bg-white/5 rounded text-slate-300">
                          {genre}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  )
}

export default MusicTasteMapPage
