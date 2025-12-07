import React from 'react'
import { motion } from 'framer-motion'
import { Play, Heart, Share2, Music } from 'lucide-react'
import { Track } from '../contexts/MusicDataContext'
import { formatNumber, formatDurationShort } from '../utils/formatters'

interface TopTracksChartProps {
  tracks: Track[]
}

const TopTracksChart: React.FC<TopTracksChartProps> = ({ tracks }) => {
  if (tracks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Music className="h-12 w-12 text-slate-400 mb-4" />
        <p className="text-slate-400">No tracks available</p>
      </div>
    )
  }

  const maxPlays = Math.max(...tracks.map(track => track.playCount))

  return (
    <div className="space-y-3">
      {tracks.map((track, index) => {
        const playPercentage = maxPlays > 0 ? (track.playCount / maxPlays) * 100 : 0
        
        return (
          <motion.div
            key={track.id}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.5, delay: index * 0.1 }}
            className="music-card group"
          >
            <div className="flex items-center space-x-4">
              {/* Rank */}
              <div className="flex-shrink-0 w-8 h-8 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full flex items-center justify-center text-white font-bold text-sm">
                {index + 1}
              </div>

              {/* Track Info */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center space-x-2 mb-1">
                  <h4 className="font-medium text-slate-200 truncate group-hover:text-white transition-colors">
                    {track.title}
                  </h4>
                  {track.platform === 'spotify' && (
                    <div className="w-2 h-2 bg-spotify-500 rounded-full" />
                  )}
                </div>
                <p className="text-sm text-slate-400 truncate">{track.artist}</p>
                
                {/* Progress Bar */}
                <div className="mt-2">
                  <div className="flex items-center justify-between text-xs text-slate-500 mb-1">
                    <span>{formatNumber(track.playCount)} plays</span>
                    <span>{formatDurationShort(track.duration)}</span>
                  </div>
                  <div className="w-full bg-slate-700 rounded-full h-1.5">
                    <motion.div
                      className="h-1.5 bg-gradient-to-r from-orange-500 to-orange-600 rounded-full"
                      initial={{ width: 0 }}
                      animate={{ width: `${playPercentage}%` }}
                      transition={{ duration: 1, delay: index * 0.1 }}
                    />
                  </div>
                </div>
              </div>

              {/* Stats */}
              <div className="flex items-center space-x-4 text-slate-400">
                <div className="flex items-center space-x-1">
                  <Heart className="h-4 w-4" />
                  <span className="text-xs">{formatNumber(track.likes)}</span>
                </div>
                <div className="flex items-center space-x-1">
                  <Share2 className="h-4 w-4" />
                  <span className="text-xs">{formatNumber(track.reposts)}</span>
                </div>
                <button className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors group-hover:bg-orange-500/20">
                  <Play className="h-4 w-4" />
                </button>
              </div>
            </div>
          </motion.div>
        )
      })}
    </div>
  )
}

export default TopTracksChart
