import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { api } from '../services/api'
import toast from 'react-hot-toast'

export interface User {
  id: string
  username: string
  fullName: string
  followers: number
  following: number
  profileImage?: string
  accountAgeYears?: number
  platform: 'soundcloud' | 'spotify'
}

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (platform: 'soundcloud' | 'spotify') => Promise<void>
  logout: () => void
  refreshUserData: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const useAuth = () => {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

interface AuthProviderProps {
  children: ReactNode
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const isAuthenticated = !!user

  const login = async (platform: 'soundcloud' | 'spotify') => {
    try {
      setIsLoading(true)
      
      if (platform === 'soundcloud') {
        // Redirect to SoundCloud OAuth
        const clientId = '5pRC171gW1jxprhKPRMUJ5mpsCLRfmaM'
        const redirectUri = encodeURIComponent('http://localhost:8081/callback')
        const scope = encodeURIComponent('non-expiring')
        const responseType = 'code'
        
        const authUrl = `https://soundcloud.com/connect?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=${responseType}&scope=${scope}`
        window.location.href = authUrl
      } else if (platform === 'spotify') {
        // Redirect to Spotify OAuth
        const clientId = 'your-spotify-client-id' // You'll need to add this
        const redirectUri = encodeURIComponent('http://localhost:3000/callback/spotify')
        const scope = encodeURIComponent('user-read-private user-read-email user-top-read user-read-recently-played')
        const responseType = 'code'
        
        const authUrl = `https://accounts.spotify.com/authorize?client_id=${clientId}&response_type=${responseType}&redirect_uri=${redirectUri}&scope=${scope}`
        window.location.href = authUrl
      }
    } catch (error) {
      console.error('Login error:', error)
      toast.error('Failed to initiate login')
    } finally {
      setIsLoading(false)
    }
  }

  const logout = () => {
    setUser(null)
    localStorage.removeItem('user')
    toast.success('Logged out successfully')
  }

  const refreshUserData = async () => {
    try {
      const response = await api.get('/soundcloud/profile')
      const profileData = response.data
      
      const userData: User = {
        id: profileData.id?.toString() || '',
        username: profileData.username || '',
        fullName: profileData.full_name || '',
        followers: profileData.followers_count || 0,
        following: profileData.followings_count || 0,
        profileImage: profileData.avatar_url,
        accountAgeYears: profileData.accountAgeYears,
        platform: 'soundcloud'
      }
      
      setUser(userData)
      localStorage.setItem('user', JSON.stringify(userData))
    } catch (error) {
      console.error('Failed to refresh user data:', error)
      // Don't show error toast for this as it might be called frequently
    }
  }

  useEffect(() => {
    const checkAuthStatus = async () => {
      try {
        // Check if user data exists in localStorage
        const savedUser = localStorage.getItem('user')
        if (savedUser) {
          const userData = JSON.parse(savedUser)
          setUser(userData)
        }
        
        // Try to refresh user data from API
        await refreshUserData()
      } catch (error) {
        console.error('Auth check failed:', error)
        // Clear invalid user data
        localStorage.removeItem('user')
        setUser(null)
      } finally {
        setIsLoading(false)
      }
    }

    checkAuthStatus()
  }, [])

  const value: AuthContextType = {
    user,
    isLoading,
    isAuthenticated,
    login,
    logout,
    refreshUserData
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}
