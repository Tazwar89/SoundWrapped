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
  platform: 'soundcloud'
}

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (platform: 'soundcloud') => Promise<void>
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

  const login = async (platform: 'soundcloud') => {
    try {
      setIsLoading(true)
      
      // For demo purposes, simulate successful authentication
      // In production, this would redirect to SoundCloud OAuth
      const mockUser = {
        id: 'demo-user-123',
        username: 'Demo User',
        platform: 'soundcloud' as const,
        avatar: 'https://via.placeholder.com/150/ff5500/ffffff?text=SC',
        followers: 1250,
        following: 890
      }
      
      setUser(mockUser)
      localStorage.setItem('user', JSON.stringify(mockUser))
      
      // Simulate API success
      toast.success('Successfully connected to SoundCloud!')
      
      // In production, this would be the real OAuth flow:
      // const clientId = '5pRC171gW1jxprhKPRMUJ5mpsCLRfmaM'
      // const redirectUri = encodeURIComponent('http://localhost:8080/callback')
      // const scope = 'non-expiring'
      // const responseType = 'code'
      // const authUrl = `https://soundcloud.com/connect?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=${responseType}&scope=${scope}`
      // window.location.href = authUrl
      
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
      // For demo purposes, use mock data instead of API call
      const mockUserData: User = {
        id: 'demo-user-123',
        username: 'Demo User',
        fullName: 'Demo User',
        followers: 1250,
        following: 890,
        profileImage: 'https://via.placeholder.com/150/ff5500/ffffff?text=SC',
        accountAgeYears: 3,
        platform: 'soundcloud'
      }
      
      setUser(mockUserData)
      localStorage.setItem('user', JSON.stringify(mockUserData))
      
      // In production, this would be the real API call:
      // const response = await api.get('/soundcloud/profile')
      // const profileData = response.data
      // ... rest of the API logic
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
        } else {
          // Only refresh user data if no saved user exists
          await refreshUserData()
        }
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
