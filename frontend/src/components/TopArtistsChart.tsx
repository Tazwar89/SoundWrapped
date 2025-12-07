import React from 'react'
import { motion } from 'framer-motion'
import { Music, Clock, TrendingUp } from 'lucide-react'
import { Artist } from '../contexts/MusicDataContext'
import { formatNumber, formatHours } from '../utils/formatters'
import { generateGradient } from '../utils/formatters'

interface TopArtistsChartProps {
  artists: Artist[]
}

const TopArtistsChart: React.FC<TopArtistsChartProps> = ({ artists }) => {
  if (artists.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Music className="h-12 w-12 text-slate-400 mb-4" />
        <p className="text-slate-400">No artists available</p>
      </div>
    )
  }

  const maxHours = Math.max(...artists.map(artist => artist.listeningHours))

  return (
    <div className="space-y-4">
      {artists.map((artist, index) => {
        const hoursPercentage = maxHours > 0 ? (artist.listeningHours / maxHours) * 100 : 0
        const gradientColor = generateGradient(artist.name)
        
        return (
          <motion.div
            key={artist.name}
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.5, delay: index * 0.1 }}
            className="music-card group"
          >
            <div className="flex items-center space-x-4">
              {/* Rank & Avatar */}
              <div className="flex items-center space-x-3">
                <div className="flex-shrink-0 w-8 h-8 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full flex items-center justify-center text-white font-bold text-sm">
                  {index + 1}
                </div>
                <div 
                  className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold text-lg"
                  style={{ backgroundColor: gradientColor }}
                >
                  {artist.name.charAt(0).toUpperCase()}
                </div>
              </div>

              {/* Artist Info */}
              <div className="flex-1 min-w-0">
                <h4 className="font-medium text-slate-200 truncate group-hover:text-white transition-colors">
                  {artist.name}
                </h4>
                <div className="flex items-center space-x-4 mt-1 text-sm text-slate-400">
                  <div className="flex items-center space-x-1">
                    <Music className="h-3 w-3" />
                    <span>{artist.trackCount} tracks</span>
                  </div>
                  <div className="flex items-center space-x-1">
                    <TrendingUp className="h-3 w-3" />
                    <span>{formatNumber(artist.playCount)} plays</span>
                  </div>
                </div>
                
                {/* Progress Bar */}
                <div className="mt-2">
                  <div className="flex items-center justify-between text-xs text-slate-500 mb-1">
                    <span>{formatHours(artist.listeningHours)}</span>
                    <span>{hoursPercentage.toFixed(1)}% of total</span>
                  </div>
                  <div className="w-full bg-slate-700 rounded-full h-1.5">
                    <motion.div
                      className="h-1.5 rounded-full"
                      style={{ backgroundColor: gradientColor }}
                      initial={{ width: 0 }}
                      animate={{ width: `${hoursPercentage}%` }}
                      transition={{ duration: 1, delay: index * 0.1 }}
                    />
                  </div>
                </div>
              </div>

              {/* Hours Badge */}
              <div className="flex-shrink-0">
                <div className="bg-gradient-to-r from-orange-500/20 to-orange-600/20 px-3 py-1 rounded-full">
                  <div className="flex items-center space-x-1 text-orange-300">
                    <Clock className="h-3 w-3" />
                    <span className="text-xs font-medium">{formatHours(artist.listeningHours)}</span>
                  </div>
                </div>
              </div>
            </div>
          </motion.div>
        )
      })}
    </div>
  )
}

export default TopArtistsChart
