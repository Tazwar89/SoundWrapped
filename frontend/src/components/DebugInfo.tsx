import React from 'react'
import { useAuth } from '../contexts/AuthContext'
import { useMusicData } from '../contexts/MusicDataContext'

const DebugInfo: React.FC = () => {
  const auth = useAuth()
  const musicData = useMusicData()

  return (
    <div className="fixed top-4 right-4 bg-black/80 text-white p-4 rounded-lg text-xs z-50">
      <div>Auth: {auth.isAuthenticated ? 'Yes' : 'No'}</div>
      <div>User: {auth.user?.username || 'None'}</div>
      <div>Loading: {auth.isLoading ? 'Yes' : 'No'}</div>
      <div>Tracks: {musicData.tracks.length}</div>
      <div>Artists: {musicData.artists.length}</div>
    </div>
  )
}

export default DebugInfo
