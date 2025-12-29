import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { api } from '../services/api'
import toast from 'react-hot-toast'

export interface Track {
  id: string
  title: string
  artist: string
  duration: number
  playCount: number
  reposts: number
  likes: number
  createdAt: string
  artwork?: string
  platform: 'soundcloud' | 'spotify'
}

export interface Artist {
  name: string
  playCount: number
  listeningHours: number
  trackCount: number
}

export interface Playlist {
  id: string
  title: string
  trackCount: number
  likes: number
  createdAt: string
  artwork?: string
}

export interface WrappedData {
  profile: {
    username: string
    accountAgeYears: number
    followers: number
    tracksUploaded: number
    playlistsCreated: number
  }
  topTracks: Array<{
    rank: number
    title: string
    artist: string
    playCount: number
  }>
  topArtists: Array<{
    rank: number
    artist: string
  }>
  topRepostedTracks: Array<{
    title: string
    reposts: number
  }>
  stats: {
    totalListeningHours: number
    likesGiven: number
    tracksUploaded: number
    commentsPosted: number
    booksYouCouldHaveRead: number
  }
  funFact: string
  peakYear: string
  globalTasteComparison: string
  stories: string[]
  undergroundSupportPercentage?: number
  yearInReviewPoetry?: string
  trendsetterScore?: {
    score: number
    badge: string
    description: string
    visionaryTracks: number
    earlyAdopterTracks: number
  }
  repostKingScore?: {
    repostedTracks: number
    trendingTracks: number
    percentage: number
    badge: string
    description: string
  }
  sonicArchetype?: string
}

export interface MusicTasteLocation {
  city: string
  country: string
  similarity: number
  userCount: number
  topGenres: string[]
  coordinates: {
    lat: number
    lng: number
  }
}

interface MusicDataContextType {
  // Data
  tracks: Track[]
  artists: Artist[]
  playlists: Playlist[]
  wrappedData: WrappedData | null
  musicTasteLocations: MusicTasteLocation[]
  
  // Loading states
  isLoadingTracks: boolean
  isLoadingArtists: boolean
  isLoadingPlaylists: boolean
  isLoadingWrapped: boolean
  isLoadingMusicTasteMap: boolean
  
  // Actions
  fetchTracks: () => Promise<void>
  fetchArtists: () => Promise<void>
  fetchPlaylists: () => Promise<void>
  fetchWrappedData: () => Promise<void>
  fetchMusicTasteMap: () => Promise<void>
  refreshAllData: () => Promise<void>
}

const MusicDataContext = createContext<MusicDataContextType | undefined>(undefined)

export const useMusicData = () => {
  const context = useContext(MusicDataContext)
  if (context === undefined) {
    throw new Error('useMusicData must be used within a MusicDataProvider')
  }
  return context
}

interface MusicDataProviderProps {
  children: ReactNode
}

export const MusicDataProvider: React.FC<MusicDataProviderProps> = ({ children }) => {
  // Data state
  const [tracks, setTracks] = useState<Track[]>([])
  const [artists, setArtists] = useState<Artist[]>([])
  const [playlists, setPlaylists] = useState<Playlist[]>([])
  const [wrappedData, setWrappedData] = useState<WrappedData | null>(null)
  const [musicTasteLocations, setMusicTasteLocations] = useState<MusicTasteLocation[]>([])

  // Loading states
  const [isLoadingTracks, setIsLoadingTracks] = useState(false)
  const [isLoadingArtists, setIsLoadingArtists] = useState(false)
  const [isLoadingPlaylists, setIsLoadingPlaylists] = useState(false)
  const [isLoadingWrapped, setIsLoadingWrapped] = useState(false)
  const [isLoadingMusicTasteMap, setIsLoadingMusicTasteMap] = useState(false)

  const fetchTracks = useCallback(async () => {
    try {
      console.log('fetchTracks: Starting...')
      setIsLoadingTracks(true)
      
      // Real API call to get user's tracks
      const response = await api.get('/soundcloud/tracks')
      const tracksData = response.data.map((track: any) => ({
        id: track.id?.toString() || '',
        title: track.title || 'Unknown Track',
        artist: track.user?.username || 'Unknown Artist',
        duration: track.duration || 0,
        // Use user's tracked play count if available, otherwise fall back to global playback_count
        playCount: track.user_play_count || track.playback_count || 0,
        reposts: track.reposts_count || 0,
        likes: track.likes_count || 0,
        createdAt: track.created_at || new Date().toISOString(),
        artwork: track.artwork_url || track.user?.avatar_url || 'https://via.placeholder.com/300x300/ff5500/ffffff?text=Track',
        platform: 'soundcloud' as const
      }))
      // Tracks are already sorted by user's play count from backend
      
      setTracks(tracksData)
      console.log('fetchTracks: Real tracks loaded, count:', tracksData.length)
      
    } catch (error) {
      console.error('Failed to fetch tracks:', error)
      toast.error('Failed to load tracks')
    } finally {
      setIsLoadingTracks(false)
      console.log('fetchTracks: Completed')
    }
  }, [])

  const fetchArtists = useCallback(async () => {
    try {
      console.log('fetchArtists: Starting, tracks count:', tracks.length)
      setIsLoadingArtists(true)
      // This would be calculated from tracks data
      const artistMap = new Map<string, { playCount: number; listeningHours: number; trackCount: number }>()
      
      tracks.forEach(track => {
        const artist = track.artist
        if (artist) {
          const existing = artistMap.get(artist) || { playCount: 0, listeningHours: 0, trackCount: 0 }
          existing.playCount += track.playCount
          existing.listeningHours += track.duration / 1000 / 60 / 60
          existing.trackCount += 1
          artistMap.set(artist, existing)
        }
      })

      const artistsData: Artist[] = Array.from(artistMap.entries()).map(([name, data]) => ({
        name,
        playCount: data.playCount,
        listeningHours: data.listeningHours,
        trackCount: data.trackCount
      })).sort((a, b) => b.listeningHours - a.listeningHours)

      setArtists(artistsData)
      console.log('fetchArtists: Artists calculated, count:', artistsData.length)
    } catch (error) {
      console.error('Failed to fetch artists:', error)
      toast.error('Failed to load artists')
    } finally {
      setIsLoadingArtists(false)
      console.log('fetchArtists: Completed')
    }
  }, [tracks]) // Depends on tracks array

  const fetchPlaylists = useCallback(async () => {
    try {
      setIsLoadingPlaylists(true)
      
      // Real API call to get user's playlists
      const response = await api.get('/soundcloud/playlists')
      const playlistsData = response.data.map((playlist: any) => ({
        id: playlist.id?.toString() || '',
        title: playlist.title || 'Unknown Playlist',
        trackCount: playlist.track_count || 0,
        likes: playlist.likes_count || 0,
        createdAt: playlist.created_at || new Date().toISOString(),
        artwork: playlist.artwork_url || playlist.user?.avatar_url || 'https://via.placeholder.com/300x300/ff6b6b/ffffff?text=Playlist'
      }))
      
      setPlaylists(playlistsData)
      
    } catch (error) {
      console.error('Failed to fetch playlists:', error)
      toast.error('Failed to load playlists')
    } finally {
      setIsLoadingPlaylists(false)
    }
  }, [])

  const fetchWrappedData = useCallback(async () => {
    try {
      console.log('fetchWrappedData called')
      setIsLoadingWrapped(true)
      
      // Real API call to get wrapped data with longer timeout (60 seconds for wrapped)
      const response = await api.get('/soundcloud/wrapped/full', {
        timeout: 60000 // 60 seconds timeout for wrapped endpoint
      })
      const wrappedData = response.data
      
      console.log('Setting wrapped data:', wrappedData)
      setWrappedData(wrappedData)
      
    } catch (error) {
      console.error('Failed to fetch wrapped data:', error)
      toast.error('Failed to load your wrapped data. This may be due to rate limiting - please try again in a few moments.')
    } finally {
      setIsLoadingWrapped(false)
    }
  }, [])

  const fetchMusicTasteMap = useCallback(async () => {
    try {
      setIsLoadingMusicTasteMap(true)
      
      // First, try to update user location (non-blocking)
      try {
        await api.post('/tracking/update-location')
      } catch (e) {
        // Silently fail - location update is optional
      }
      
      // Then fetch the music taste map
      const response = await api.get('/soundcloud/music-taste-map')
      if (response?.data && Array.isArray(response.data)) {
        // Transform backend data to frontend format
        const locations: MusicTasteLocation[] = response.data.map((loc: any) => ({
          city: loc.city || 'Unknown',
          country: loc.country || 'Unknown',
          similarity: typeof loc.similarity === 'number' ? loc.similarity : 0,
          userCount: typeof loc.userCount === 'number' ? loc.userCount : 0,
          topGenres: Array.isArray(loc.topGenres) ? loc.topGenres : [],
          coordinates: loc.coordinates || { lat: 0, lng: 0 }
        }))
        setMusicTasteLocations(locations)
      } else {
        setMusicTasteLocations([])
      }
    } catch (error) {
      console.error('Failed to fetch music taste map data:', error)
      toast.error('Failed to load music taste map')
      setMusicTasteLocations([])
    } finally {
      setIsLoadingMusicTasteMap(false)
    }
  }, [])

  const refreshAllData = useCallback(async () => {
    try {
      console.log('refreshAllData: Starting...')
      // First fetch tracks, then calculate artists from them
      await fetchTracks()
      console.log('refreshAllData: Tracks loaded, fetching artists...')
      await fetchArtists()
      
      // Then fetch other data in parallel
      console.log('refreshAllData: Fetching other data in parallel...')
      await Promise.all([
        fetchPlaylists(),
        fetchWrappedData(),
        fetchMusicTasteMap()
      ])
      console.log('refreshAllData: Completed successfully')
    } catch (error) {
      console.error('refreshAllData: Error occurred:', error)
    }
  }, [fetchTracks, fetchArtists, fetchPlaylists, fetchWrappedData, fetchMusicTasteMap])

  // Auto-fetch artists when tracks change
  useEffect(() => {
    if (tracks.length > 0) {
      fetchArtists()
    }
  }, [tracks])

  const value: MusicDataContextType = {
    tracks,
    artists,
    playlists,
    wrappedData,
    musicTasteLocations,
    isLoadingTracks,
    isLoadingArtists,
    isLoadingPlaylists,
    isLoadingWrapped,
    isLoadingMusicTasteMap,
    fetchTracks,
    fetchArtists,
    fetchPlaylists,
    fetchWrappedData,
    fetchMusicTasteMap,
    refreshAllData
  }

  return (
    <MusicDataContext.Provider value={value}>
      {children}
    </MusicDataContext.Provider>
  )
}
