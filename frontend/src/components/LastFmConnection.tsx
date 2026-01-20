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
    
    // Listen for messages from the callback popup window
    const handleMessage = (event: MessageEvent) => {
      console.log('[LastFmConnection] 📨 Received message:', event.data, 'from origin:', event.origin, 'expected origin:', window.location.origin)
      
      // Accept messages from same origin (callback page is on same origin)
      // Also accept from localhost:3000 explicitly for development
      const allowedOrigins = [
        window.location.origin,
        'http://localhost:3000',
        'https://localhost:3000'
      ]
      
      if (!allowedOrigins.includes(event.origin)) {
        console.log('[LastFmConnection] ⚠️ Ignoring message from different origin:', event.origin)
        return
      }
      
      if (event.data?.type === 'lastfm_callback') {
        console.log('[LastFmConnection] ✅ Processing callback message:', event.data)
        if (event.data.connected) {
          toast.success(`Last.fm connected successfully${event.data.username ? ` as ${event.data.username}` : ''}!`, { icon: '✅' })
          // Wait a bit for backend to finish saving, then check status
          setTimeout(() => {
            checkConnectionStatus()
          }, 1000)
        } else {
          const errorMsg = event.data.error ? `: ${event.data.error}` : ''
          toast.error(`Failed to connect Last.fm account${errorMsg}`, { icon: '❌' })
          checkConnectionStatus()
        }
      } else {
        console.log('[LastFmConnection] ⚠️ Message type not recognized:', event.data?.type)
      }
    }
    
    window.addEventListener('message', handleMessage)
    console.log('[LastFmConnection] Message listener registered')
    
    return () => {
      window.removeEventListener('message', handleMessage)
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
      const callbackUrl = response.data.callbackUrl
      
      if (!authUrl) {
        toast.error('No authorization URL received from server', { icon: '❌' })
        setIsConnecting(false)
        return
      }
      
      // Log the auth URL for debugging
      console.log('[LastFmConnection] ========================================')
      console.log('[LastFmConnection] 🔗 Opening Last.fm auth URL')
      console.log('[LastFmConnection] Full URL:', authUrl)
      console.log('[LastFmConnection] Callback URL from backend:', callbackUrl || 'not provided')
      console.log('[LastFmConnection] Expected callback URL: http://localhost:8080/api/lastfm/callback')
      
      // Verify callback URL is in the auth URL
      if (authUrl.includes('cb=')) {
        const cbMatch = authUrl.match(/cb=([^&]+)/)
        if (cbMatch) {
          const decodedCb = decodeURIComponent(cbMatch[1])
          console.log('[LastFmConnection] ✅ Callback URL in auth URL (decoded):', decodedCb)
          if (decodedCb !== 'http://localhost:8080/api/lastfm/callback') {
            console.error('[LastFmConnection] ❌ CALLBACK URL MISMATCH!')
            console.error('[LastFmConnection] Expected: http://localhost:8080/api/lastfm/callback')
            console.error('[LastFmConnection] Got:', decodedCb)
            console.error('[LastFmConnection] ⚠️ This MUST match exactly in Last.fm app settings!')
          } else {
            console.log('[LastFmConnection] ✅ Callback URL matches expected value')
          }
        }
      } else {
        console.error('[LastFmConnection] ❌ No callback URL (cb parameter) found in auth URL!')
        console.error('[LastFmConnection] Last.fm will NOT redirect without this parameter!')
      }
      console.log('[LastFmConnection] ========================================')
      
      // For localhost, use full-page redirect instead of popup to avoid browser security issues
      // Last.fm redirects to localhost callbacks can be blocked in popups
      const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
      
      if (isLocalhost) {
        console.log('[LastFmConnection] Using full-page redirect for localhost (popup redirects may be blocked)')
        console.log('[LastFmConnection] ⚠️ IMPORTANT: After authorizing on Last.fm, check:')
        console.log('[LastFmConnection] 1. What URL are you on? (Check the address bar)')
        console.log('[LastFmConnection] 2. Are you on Last.fm still, or did it redirect?')
        console.log('[LastFmConnection] 3. If redirected, what is the full URL?')
        console.log('[LastFmConnection] 4. Check backend terminal for callback endpoint logs')
        // Store the auth URL in sessionStorage so we can check it after redirect
        sessionStorage.setItem('lastfm_auth_initiated', 'true')
        // Redirect the main window
        window.location.href = authUrl
        return // Don't continue with popup logic
      }
      
      // For production/non-localhost, use popup
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

      // Poll for callback completion (window will redirect to callback page)
      let lastUrl = authUrl
      const pollInterval = setInterval(async () => {
        try {
          if (authWindow?.closed) {
            clearInterval(pollInterval)
            setIsConnecting(false)
            console.log('[LastFmConnection] Auth window closed, checking connection status')
            console.log('[LastFmConnection] Last known URL before close:', lastUrl)
            // Wait a bit for backend to process, then check status
            // Always check status when window closes as fallback (in case message wasn't received)
            setTimeout(async () => {
              console.log('[LastFmConnection] Checking connection status after window closed (fallback)')
              await checkConnectionStatus()
            }, 2000) // Increased delay to ensure backend has processed
            return
          }
          
          // Try to check the current URL of the popup (may fail due to cross-origin)
          try {
            const currentUrl = authWindow.location.href
            if (currentUrl !== lastUrl) {
              console.log('[LastFmConnection] 🔄 Popup URL changed:', currentUrl)
              lastUrl = currentUrl
              
              // Check if we're on the callback page
              if (currentUrl.includes('/lastfm/callback') || currentUrl.includes('/api/lastfm/callback')) {
                console.log('[LastFmConnection] ✅ Popup navigated to callback URL!')
                // Don't close yet, let the callback page handle it
              } else if (currentUrl === 'about:blank') {
                console.warn('[LastFmConnection] ⚠️ Popup navigated to about:blank - this usually means a redirect failed')
                console.warn('[LastFmConnection] Possible causes:')
                console.warn('[LastFmConnection] 1. Callback URL in Last.fm app settings doesn\'t match')
                console.warn('[LastFmConnection] 2. Last.fm rejected the callback URL')
                console.warn('[LastFmConnection] 3. Browser blocked the redirect')
                // Wait a bit to see if it redirects properly
                setTimeout(() => {
                  try {
                    const finalUrl = authWindow.location.href
                    if (finalUrl !== 'about:blank') {
                      console.log('[LastFmConnection] Popup recovered, new URL:', finalUrl)
                    } else {
                      console.error('[LastFmConnection] ❌ Popup stuck on about:blank - Last.fm redirect failed')
                    }
                  } catch (e) {
                    // Window might be closed or cross-origin
                  }
                }, 2000)
              }
            }
          } catch (e) {
            // Cross-origin error is expected when on Last.fm domain
            // This is normal and not an error - it means we're on Last.fm's domain
            // Only log if it's not the expected cross-origin error
            if (!e.message || !e.message.includes('cross-origin')) {
              console.log('[LastFmConnection] Popup check (cross-origin expected):', e.message || 'Unknown error')
            }
          }
        } catch (error) {
          // Window might be closed
          console.error('[LastFmConnection] Error polling window:', error)
        }
      }, 500) // Check more frequently

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

