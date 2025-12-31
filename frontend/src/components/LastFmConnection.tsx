import React, { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Music, Check, X, RefreshCw, ExternalLink, Info } from 'lucide-react'
import { api } from '../services/api'
import toast from 'react-hot-toast'

interface LastFmStatus {
  connected: boolean
  username?: string
  lastSyncAt?: string
}

const LastFmConnection: React.FC = () => {
  const [status, setStatus] = useState<LastFmStatus | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isConnecting, setIsConnecting] = useState(false)
  const [isSyncing, setIsSyncing] = useState(false)

  useEffect(() => {
    checkConnectionStatus()
    
    // Check for Last.fm callback in URL params
    const urlParams = new URLSearchParams(window.location.search)
    const lastfmConnected = urlParams.get('lastfm_connected')
    const error = urlParams.get('error')
    const username = urlParams.get('username')
    
    if (lastfmConnected === 'true') {
      toast.success(`Last.fm connected successfully${username ? ` as ${username}` : ''}!`, { icon: '✅' })
      // Clear URL params
      window.history.replaceState({}, '', window.location.pathname)
      checkConnectionStatus()
    } else if (lastfmConnected === 'false') {
      const errorMsg = error ? `: ${error}` : ''
      toast.error(`Failed to connect Last.fm account${errorMsg}`, { icon: '❌' })
      // Clear URL params
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [])

  const checkConnectionStatus = async () => {
    try {
      setIsLoading(true)
      const response = await api.get('/lastfm/status')
      setStatus(response.data)
    } catch (error: any) {
      console.error('Failed to check Last.fm status:', error)
      setStatus({ connected: false })
    } finally {
      setIsLoading(false)
    }
  }

  const handleConnect = async () => {
    try {
      setIsConnecting(true)
      const response = await api.get('/lastfm/auth-url')
      
      // Check for error in response
      if (response.data.error) {
        toast.error(response.data.error || 'Failed to get Last.fm authorization URL', { icon: '❌' })
        setIsConnecting(false)
        return
      }
      
      const authUrl = response.data.authUrl
      if (!authUrl) {
        toast.error('No authorization URL received from server', { icon: '❌' })
        setIsConnecting(false)
        return
      }
      
      // Open Last.fm auth page in new window
      const authWindow = window.open(
        authUrl,
        'LastFmAuth',
        'width=600,height=700,scrollbars=yes'
      )

      if (!authWindow) {
        toast.error('Popup blocked. Please allow popups for this site.', { icon: '❌' })
        setIsConnecting(false)
        return
      }

      // Poll for callback completion (window will redirect to dashboard)
      const pollInterval = setInterval(async () => {
        try {
          if (authWindow?.closed) {
            clearInterval(pollInterval)
            setIsConnecting(false)
            // Check if connection was successful (URL params will be checked by useEffect)
            await checkConnectionStatus()
          }
        } catch (error) {
          // Window might be closed
        }
      }, 1000)

      // Cleanup interval after 5 minutes (timeout)
      setTimeout(() => {
        clearInterval(pollInterval)
        if (authWindow && !authWindow.closed) {
          authWindow.close()
        }
        setIsConnecting(false)
      }, 300000) // 5 minutes

    } catch (error: any) {
      console.error('Failed to initiate Last.fm connection:', error)
      const errorMessage = error.response?.data?.error || error.message || 'Failed to connect Last.fm account'
      toast.error(errorMessage, { icon: '❌' })
      setIsConnecting(false)
    }
  }

  const handleDisconnect = async () => {
    try {
      await api.post('/lastfm/disconnect')
      setStatus({ connected: false })
      toast.success('Last.fm disconnected')
    } catch (error: any) {
      console.error('Failed to disconnect Last.fm:', error)
      toast.error('Failed to disconnect Last.fm account')
    }
  }

  const handleSync = async () => {
    try {
      setIsSyncing(true)
      await api.post('/lastfm/sync')
      toast.success('Sync triggered! Your listening data will be updated shortly.')
      await checkConnectionStatus()
    } catch (error: any) {
      console.error('Failed to trigger sync:', error)
      toast.error('Failed to trigger sync')
    } finally {
      setIsSyncing(false)
    }
  }

  if (isLoading) {
    return (
      <div className="stat-card">
        <div className="flex items-center justify-center py-4">
          <RefreshCw className="h-5 w-5 animate-spin text-orange-400" />
        </div>
      </div>
    )
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="stat-card"
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Music className="h-5 w-5 text-orange-400" />
          <h3 className="text-lg font-semibold text-white">Last.fm Scrobbling</h3>
        </div>
        {status?.connected ? (
          <span className="flex items-center gap-1 text-green-400 text-sm">
            <Check className="h-4 w-4" />
            Connected
          </span>
        ) : (
          <span className="flex items-center gap-1 text-gray-400 text-sm">
            <X className="h-4 w-4" />
            Not Connected
          </span>
        )}
      </div>

      {status?.connected ? (
        <div className="space-y-4">
          <div>
            <p className="text-sm text-white/70 mb-1">Connected as:</p>
            <p className="text-white font-medium">{status.username}</p>
          </div>

          {status.lastSyncAt && (
            <div>
              <p className="text-sm text-white/70 mb-1">Last synced:</p>
              <p className="text-white/80 text-sm">
                {new Date(status.lastSyncAt).toLocaleString()}
              </p>
            </div>
          )}

          <div className="flex gap-2">
            <button
              onClick={handleSync}
              disabled={isSyncing}
              className="btn-secondary flex-1 flex items-center justify-center gap-2"
            >
              <RefreshCw className={`h-4 w-4 ${isSyncing ? 'animate-spin' : ''}`} />
              {isSyncing ? 'Syncing...' : 'Sync Now'}
            </button>
            <button
              onClick={handleDisconnect}
              className="btn-secondary px-4"
            >
              Disconnect
            </button>
          </div>

          <div className="bg-blue-500/10 border border-blue-500/20 rounded-lg p-3">
            <div className="flex items-start gap-2">
              <Info className="h-4 w-4 text-blue-400 mt-0.5 flex-shrink-0" />
              <div className="text-xs text-blue-200">
                <p className="font-medium mb-1">How it works:</p>
                <ul className="list-disc list-inside space-y-1 text-blue-300/80">
                  <li>Web Scrobbler automatically detects SoundCloud playback (it's a built-in connector)</li>
                  <li>Your SoundCloud plays are scrobbled to your Last.fm account</li>
                  <li>SoundWrapped syncs your Last.fm data every 15 minutes</li>
                  <li>Works across all browsers and devices where you have Web Scrobbler installed</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-white/70">
            Connect your Last.fm account to automatically track your SoundCloud listening activity.
            Works across all browsers and devices!
          </p>

          <button
            onClick={handleConnect}
            disabled={isConnecting}
            className="btn-primary w-full flex items-center justify-center gap-2"
          >
            {isConnecting ? (
              <>
                <RefreshCw className="h-4 w-4 animate-spin" />
                Connecting...
              </>
            ) : (
              <>
                <Music className="h-4 w-4" />
                Connect Last.fm
              </>
            )}
          </button>

          <div className="bg-orange-500/10 border border-orange-500/20 rounded-lg p-3">
            <div className="flex items-start gap-2">
              <Info className="h-4 w-4 text-orange-400 mt-0.5 flex-shrink-0" />
              <div className="text-xs text-orange-200">
                <p className="font-medium mb-1">Setup Instructions:</p>
                <ol className="list-decimal list-inside space-y-1 text-orange-300/80">
                  <li>Install <a href="https://webscrobbler.com" target="_blank" rel="noopener noreferrer" className="underline">Web Scrobbler</a> extension <ExternalLink className="h-3 w-3 inline" /> (Chrome, Firefox, Safari, Edge)</li>
                  <li>In Web Scrobbler settings, go to <strong>Accounts</strong> and connect your <strong>Last.fm</strong> account</li>
                  <li>SoundCloud is automatically supported (no need to connect it separately - it's in the Connectors list)</li>
                  <li>Click "Connect Last.fm" above to link your Last.fm account to SoundWrapped</li>
                  <li>Start listening on SoundCloud.com - your plays will be automatically scrobbled to Last.fm!</li>
                </ol>
              </div>
            </div>
          </div>
        </div>
      )}
    </motion.div>
  )
}

export default LastFmConnection

