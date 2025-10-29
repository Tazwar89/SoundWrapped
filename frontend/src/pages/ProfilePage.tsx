import React from 'react'
import { motion } from 'framer-motion'
import { User, Music, Calendar, Users, Heart, Share2, Settings, LogOut } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { useMusicData } from '../contexts/MusicDataContext'
import { formatNumber } from '../utils/formatters'

const ProfilePage: React.FC = () => {
  const { user, logout } = useAuth()
  const { wrappedData } = useMusicData()

  if (!user) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <h1 className="text-4xl font-bold gradient-text mb-4">No Profile Found</h1>
          <p className="text-slate-300">Please log in to view your profile</p>
        </div>
      </div>
    )
  }

  return (
    <div className="py-8">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Profile Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className="stat-card mb-8"
        >
          <div className="flex flex-col md:flex-row items-center md:items-start space-y-6 md:space-y-0 md:space-x-8">
            {/* Profile Image */}
            <div className="flex-shrink-0">
              {user.profileImage ? (
                <img
                  src={user.profileImage}
                  alt={user.username}
                  className="w-32 h-32 rounded-full object-cover border-4 border-primary-500/20"
                />
              ) : (
                <div className="w-32 h-32 bg-gradient-to-r from-primary-500 to-soundcloud-500 rounded-full flex items-center justify-center">
                  <User className="h-16 w-16 text-white" />
                </div>
              )}
            </div>

            {/* Profile Info */}
            <div className="flex-1 text-center md:text-left">
              <h1 className="text-3xl md:text-4xl font-bold gradient-text mb-2">
                {user.fullName || user.username}
              </h1>
              <p className="text-xl text-slate-300 mb-4">@{user.username}</p>
              
              <div className="flex flex-wrap justify-center md:justify-start gap-6 mb-6">
                <div className="text-center">
                  <div className="text-2xl font-bold text-slate-200">
                    {formatNumber(user.followers)}
                  </div>
                  <div className="text-slate-400">Followers</div>
                </div>
                <div className="text-center">
                  <div className="text-2xl font-bold text-slate-200">
                    {formatNumber(user.following)}
                  </div>
                  <div className="text-slate-400">Following</div>
                </div>
                <div className="text-center">
                  <div className="text-2xl font-bold text-slate-200">
                    {user.accountAgeYears || 'N/A'}
                  </div>
                  <div className="text-slate-400">Years Active</div>
                </div>
              </div>

              <div className="flex flex-wrap justify-center md:justify-start gap-4">
                <button className="btn-primary flex items-center space-x-2">
                  <Share2 className="h-4 w-4" />
                  <span>Share Profile</span>
                </button>
                <button className="px-4 py-2 bg-white/5 hover:bg-white/10 rounded-lg transition-colors flex items-center space-x-2">
                  <Settings className="h-4 w-4" />
                  <span>Settings</span>
                </button>
                <button
                  onClick={logout}
                  className="px-4 py-2 bg-red-500/20 hover:bg-red-500/30 text-red-400 rounded-lg transition-colors flex items-center space-x-2"
                >
                  <LogOut className="h-4 w-4" />
                  <span>Logout</span>
                </button>
              </div>
            </div>
          </div>
        </motion.div>

        {/* Stats Overview */}
        {wrappedData && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8"
          >
            <div className="stat-card text-center">
              <Music className="h-8 w-8 text-primary-400 mx-auto mb-3" />
              <div className="text-2xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.profile.tracksUploaded)}
              </div>
              <div className="text-slate-400">Tracks Uploaded</div>
            </div>
            
            <div className="stat-card text-center">
              <Calendar className="h-8 w-8 text-soundcloud-400 mx-auto mb-3" />
              <div className="text-2xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.profile.playlistsCreated)}
              </div>
              <div className="text-slate-400">Playlists Created</div>
            </div>
            
            <div className="stat-card text-center">
              <Heart className="h-8 w-8 text-pink-400 mx-auto mb-3" />
              <div className="text-2xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.stats.likesGiven)}
              </div>
              <div className="text-slate-400">Likes Given</div>
            </div>
            
            <div className="stat-card text-center">
              <Users className="h-8 w-8 text-spotify-400 mx-auto mb-3" />
              <div className="text-2xl font-bold text-slate-200 mb-1">
                {formatNumber(wrappedData.stats.totalListeningHours)}
              </div>
              <div className="text-slate-400">Hours Listened</div>
            </div>
          </motion.div>
        )}


        {/* Account Details */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.6 }}
          className="stat-card"
        >
          <h3 className="subsection-title mb-6">Account Details</h3>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-400 mb-2">
                Username
              </label>
              <div className="p-3 bg-white/5 rounded-lg text-slate-200">
                @{user.username}
              </div>
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-400 mb-2">
                Full Name
              </label>
              <div className="p-3 bg-white/5 rounded-lg text-slate-200">
                {user.fullName || 'Not provided'}
              </div>
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-400 mb-2">
                Platform
              </label>
              <div className="p-3 bg-white/5 rounded-lg text-slate-200 capitalize">
                {user.platform}
              </div>
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-400 mb-2">
                Account Age
              </label>
              <div className="p-3 bg-white/5 rounded-lg text-slate-200">
                {user.accountAgeYears ? `${user.accountAgeYears} years` : 'Unknown'}
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  )
}

export default ProfilePage
