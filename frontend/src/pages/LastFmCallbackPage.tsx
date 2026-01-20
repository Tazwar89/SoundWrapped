import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Callback page for Last.fm OAuth.
 * This page receives the OAuth callback, sends a message to the parent window,
 * and then closes itself.
 */
const LastFmCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams()
  const connected = searchParams.get('lastfm_connected')
  const username = searchParams.get('username')
  const error = searchParams.get('error')
  
  // Also check for direct Last.fm token parameter (if Last.fm redirects directly here)
  const token = searchParams.get('token')

  useEffect(() => {
    console.log('[LastFmCallback] 🔔 Callback page loaded!')
    console.log('[LastFmCallback] URL params:', { connected, username, error, token })
    console.log('[LastFmCallback] Full URL:', window.location.href)
    console.log('[LastFmCallback] window.opener:', window.opener ? 'exists' : 'null')
    console.log('[LastFmCallback] window.opener.closed:', window.opener ? window.opener.closed : 'N/A')
    
    // Check if this is a full-page redirect (no opener) or popup (has opener)
    const isFullPageRedirect = !window.opener
    
    // If we have a token from Last.fm but no connection status, the backend callback wasn't hit
    // This means Last.fm redirected directly to the frontend (which shouldn't happen, but handle it)
    if (token && !connected) {
      console.warn('[LastFmCallback] ⚠️ Received token from Last.fm but backend callback not processed. This should not happen.')
      console.warn('[LastFmCallback] Token:', token.substring(0, 10) + '...')
    }
    
    if (isFullPageRedirect) {
      // Full-page redirect: redirect back to dashboard with status
      console.log('[LastFmCallback] Full-page redirect detected, redirecting to dashboard')
      const params = new URLSearchParams()
      if (connected === 'true') {
        params.set('lastfm_connected', 'true')
        if (username) params.set('username', username)
      } else {
        params.set('lastfm_connected', 'false')
        if (error) params.set('error', error)
      }
      
      setTimeout(() => {
        window.location.href = `/dashboard?${params.toString()}`
      }, 2000) // Show message for 2 seconds before redirecting
    } else {
      // Popup: send message to parent window
      const message = {
        type: 'lastfm_callback',
        connected: connected === 'true',
        username: username || null,
        error: error || null,
      }
      console.log('[LastFmCallback] 📤 Sending message to parent:', message, 'origin:', window.location.origin)
      
      // Try multiple times to ensure message is sent
      const sendMessage = () => {
        try {
          window.opener.postMessage(message, window.location.origin)
          console.log('[LastFmCallback] ✅ Message sent to same origin')
        } catch (e) {
          console.warn('[LastFmCallback] Failed to send to same origin:', e)
        }
        
        // Also try sending to '*' origin as fallback
        try {
          window.opener.postMessage(message, '*')
          console.log('[LastFmCallback] ✅ Message sent with wildcard origin')
        } catch (e) {
          console.warn('[LastFmCallback] Failed to send with wildcard origin:', e)
        }
      }
      
      // Send immediately
      sendMessage()
      
      // Send again after a short delay (in case first one didn't work)
      setTimeout(sendMessage, 300)
      setTimeout(sendMessage, 600)

      // Close the popup window after a delay
      setTimeout(() => {
        console.log('[LastFmCallback] 🔒 Closing popup window')
        window.close()
      }, 2000) // Increased delay to ensure message is sent
    }
  }, [connected, username, error, token])

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-900 text-white">
      <div className="text-center">
        {connected === 'true' ? (
          <>
            <div className="text-4xl mb-4">✅</div>
            <h1 className="text-2xl font-bold mb-2">Last.fm Connected!</h1>
            {username && <p className="text-slate-300">Connected as {username}</p>}
            <p className="text-sm text-slate-400 mt-4">This window will close automatically...</p>
          </>
        ) : (
          <>
            <div className="text-4xl mb-4">❌</div>
            <h1 className="text-2xl font-bold mb-2">Connection Failed</h1>
            {error && <p className="text-slate-300">{error}</p>}
            <p className="text-sm text-slate-400 mt-4">This window will close automatically...</p>
          </>
        )}
      </div>
    </div>
  )
}

export default LastFmCallbackPage

