import React from 'react'
import { motion } from 'framer-motion'
import { Play, Heart, Share2, Clock, Upload, UserPlus } from 'lucide-react'
import { formatRelativeTime, formatDurationShort } from '../utils/formatters'

interface ActivityItem {
  type: 'like' | 'upload' | 'follow' | 'repost'
  track?: {
    id: string
    title: string
    user?: {
      username: string
    }
    duration?: number
    created_at?: string
  }
  user?: {
    id: string
    username: string
    full_name?: string
  }
  timestamp: string
}

interface RecentActivityProps {
  activities: ActivityItem[]
}

const RecentActivity: React.FC<RecentActivityProps> = ({ activities }) => {
  console.log('[RecentActivity] Received activities:', activities)
  
  // Transform backend data to frontend format
  const transformedActivities: ActivityItem[] = (activities || []).map((activity: any) => {
    // Prioritize activity timestamp (when activity was performed) over track/user created_at
    const activityTimestamp = activity.timestamp || activity.track?.created_at || activity.user?.created_at || ''
    
    const transformed: ActivityItem = {
      type: activity.type || 'like',
      timestamp: activityTimestamp
    }
    
    if (activity.track) {
      transformed.track = {
        id: activity.track.id?.toString() || '',
        title: activity.track.title || 'Unknown Track',
        user: activity.track.user ? {
          username: activity.track.user.username || 'Unknown Artist'
        } : undefined,
        duration: activity.track.duration || 0,
        created_at: activity.track.created_at || activity.timestamp || ''
      }
    }
    
    if (activity.user) {
      transformed.user = {
        id: activity.user.id?.toString() || '',
        username: activity.user.username || 'Unknown User',
        full_name: activity.user.full_name || undefined
      }
    }
    
    return transformed
  })
  
  console.log('[RecentActivity] Transformed activities:', transformedActivities)
  
  if (transformedActivities.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Play className="h-12 w-12 text-slate-400 mb-4" />
        <p className="text-slate-400">No recent activity</p>
      </div>
    )
  }

  const getActivityIcon = (type: string) => {
    switch (type) {
      case 'like':
        return Heart
      case 'upload':
        return Upload
      case 'follow':
        return UserPlus
      case 'repost':
        return Share2
      default:
        return Play
    }
  }

  const getActivityText = (activity: ActivityItem) => {
    switch (activity.type) {
      case 'like':
        return 'liked'
      case 'upload':
        return 'uploaded'
      case 'follow':
        return 'followed'
      case 'repost':
        return 'reposted'
      default:
        return 'played'
    }
  }

  const getActivityDescription = (activity: ActivityItem) => {
    if (activity.type === 'follow' && activity.user) {
      return `You followed ${activity.user.username}`
    }
    if (activity.track) {
      const artist = activity.track.user?.username || 'Unknown Artist'
      const trackTitle = activity.track.title || 'Unknown Track'
      return `You ${getActivityText(activity)} ${trackTitle} by ${artist}`
    }
    return `You ${getActivityText(activity)} this`
  }

  return (
    <div className="space-y-4">
      {transformedActivities.map((activity, index) => {
        const ActivityIcon = getActivityIcon(activity.type)
        const activityText = getActivityText(activity)
        const description = getActivityDescription(activity)
        
        // Get track/user info
        const title = activity.track?.title || activity.user?.username || 'Unknown'
        const artist = activity.track?.user?.username || activity.user?.full_name || ''
        const duration = activity.track?.duration || 0
        
        return (
          <motion.div
            key={`${activity.type}-${activity.track?.id || activity.user?.id || index}`}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: index * 0.1 }}
            className="flex items-center space-x-4 p-3 rounded-lg hover:bg-white/5 transition-colors group"
          >
            {/* Activity Icon */}
            <div className="flex-shrink-0">
              <div className="w-10 h-10 bg-gradient-to-r from-orange-500/20 to-orange-600/20 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
                <ActivityIcon className="h-5 w-5 text-orange-400" />
              </div>
            </div>

            {/* Track/User Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center space-x-2 mb-1">
                <h4 className="font-medium text-slate-200 truncate group-hover:text-white transition-colors">
                  {title}
                </h4>
                {artist && activity.type !== 'follow' && (
                  <span className="text-xs text-slate-500">by {artist}</span>
                )}
              </div>
              <div className="flex items-center space-x-4 text-sm text-slate-400">
                <span>{description}</span>
                <div className="flex items-center space-x-1">
                  <Clock className="h-3 w-3" />
                  <span>{activity.timestamp ? formatRelativeTime(activity.timestamp) : 'Unknown time'}</span>
                </div>
              </div>
            </div>

            {/* Duration (only for tracks) */}
            {activity.track && duration > 0 && (
              <div className="flex-shrink-0 text-sm text-slate-400">
                {formatDurationShort(duration)}
              </div>
            )}
          </motion.div>
        )
      })}
    </div>
  )
}

export default RecentActivity
