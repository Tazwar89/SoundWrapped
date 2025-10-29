import React from 'react'
import { motion } from 'framer-motion'
import { Play, Heart, Share2, Clock } from 'lucide-react'
import { Track } from '../contexts/MusicDataContext'
import { formatRelativeTime, formatDurationShort } from '../utils/formatters'

interface RecentActivityProps {
  tracks: Track[]
}

const RecentActivity: React.FC<RecentActivityProps> = ({ tracks }) => {
  if (tracks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Play className="h-12 w-12 text-slate-400 mb-4" />
        <p className="text-slate-400">No recent activity</p>
      </div>
    )
  }

  const getActivityIcon = (track: Track) => {
    if (track.likes > 0) return Heart
    if (track.reposts > 0) return Share2
    return Play
  }

  const getActivityText = (track: Track) => {
    if (track.likes > 0) return 'liked'
    if (track.reposts > 0) return 'reposted'
    return 'played'
  }

  return (
    <div className="space-y-4">
      {tracks.map((track, index) => {
        const ActivityIcon = getActivityIcon(track)
        const activityText = getActivityText(track)
        
        return (
          <motion.div
            key={track.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: index * 0.1 }}
            className="flex items-center space-x-4 p-3 rounded-lg hover:bg-white/5 transition-colors group"
          >
            {/* Activity Icon */}
            <div className="flex-shrink-0">
              <div className="w-10 h-10 bg-gradient-to-r from-primary-500/20 to-soundcloud-500/20 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
                <ActivityIcon className="h-5 w-5 text-primary-400" />
              </div>
            </div>

            {/* Track Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center space-x-2 mb-1">
                <h4 className="font-medium text-slate-200 truncate group-hover:text-white transition-colors">
                  {track.title}
                </h4>
                <span className="text-xs text-slate-500">by {track.artist}</span>
              </div>
              <div className="flex items-center space-x-4 text-sm text-slate-400">
                <span>You {activityText} this track</span>
                <div className="flex items-center space-x-1">
                  <Clock className="h-3 w-3" />
                  <span>{formatRelativeTime(track.createdAt)}</span>
                </div>
              </div>
            </div>

            {/* Duration */}
            <div className="flex-shrink-0 text-sm text-slate-400">
              {formatDurationShort(track.duration)}
            </div>
          </motion.div>
        )
      })}
    </div>
  )
}

export default RecentActivity
