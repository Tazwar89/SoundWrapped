import React, { useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Download, X } from 'lucide-react'
import { WrappedData } from '../contexts/MusicDataContext'
import { formatNumber } from '../utils/formatters'

interface ShareableStoryCardProps {
  wrappedData: WrappedData
  onClose: () => void
}

const ShareableStoryCard: React.FC<ShareableStoryCardProps> = ({ wrappedData, onClose }) => {
  const cardRef = useRef<HTMLDivElement>(null)
  const [isGenerating, setIsGenerating] = useState(false)

  const downloadCard = async () => {
    if (!cardRef.current) return

    setIsGenerating(true)
    try {
      // Dynamic import of html2canvas to avoid loading it if not needed
      const html2canvas = (await import('html2canvas')).default
      
      const canvas = await html2canvas(cardRef.current, {
        backgroundColor: '#0f172a', // slate-900
        scale: 2, // Higher quality
        logging: false,
        useCORS: true,
      })

      // Convert canvas to blob and download
      canvas.toBlob((blob) => {
        if (!blob) return
        
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `soundwrapped-${wrappedData.profile.username}-${Date.now()}.png`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      }, 'image/png')
    } catch (error) {
      console.error('Error generating card:', error)
      alert('Failed to generate card. Please try again.')
    } finally {
      setIsGenerating(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80">
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.9 }}
        className="relative bg-slate-900 rounded-2xl p-6 max-w-md w-full"
      >
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
        >
          <X className="h-5 w-5 text-slate-400" />
        </button>

        <h3 className="text-2xl font-bold gradient-text mb-4">Share Your Wrapped</h3>
        <p className="text-slate-300 mb-6">Download a shareable card for social media</p>

        {/* Card Preview - 9:16 aspect ratio for Instagram/TikTok stories */}
        <div className="mb-6 flex justify-center">
          <div
            ref={cardRef}
            className="w-[270px] h-[480px] bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 rounded-2xl p-6 flex flex-col justify-between relative overflow-hidden"
            style={{ aspectRatio: '9/16' }}
          >
            {/* Background decoration */}
            <div className="absolute inset-0 opacity-10">
              <div className="absolute top-0 right-0 w-64 h-64 bg-orange-500 rounded-full blur-3xl" />
              <div className="absolute bottom-0 left-0 w-64 h-64 bg-purple-500 rounded-full blur-3xl" />
            </div>

            {/* Content */}
            <div className="relative z-10">
              <div className="text-center mb-6">
                <h2 className="text-3xl font-bold gradient-text mb-2">SoundWrapped</h2>
                <p className="text-slate-300 text-sm">2024</p>
              </div>

              <div className="text-center mb-6">
                <div className="text-5xl font-bold text-white mb-2">
                  {wrappedData.profile.username}
                </div>
                <div className="text-slate-400 text-sm">Your Year in Music</div>
              </div>
            </div>

            <div className="relative z-10 space-y-4">
              {/* Top Track */}
              {wrappedData.topTracks.length > 0 && (
                <div className="bg-white/5 rounded-lg p-4">
                  <div className="text-slate-400 text-xs mb-1">Top Track</div>
                  <div className="text-white font-semibold text-sm truncate">
                    {wrappedData.topTracks[0].title}
                  </div>
                  <div className="text-slate-400 text-xs">
                    by {wrappedData.topTracks[0].artist}
                  </div>
                </div>
              )}

              {/* Stats Grid */}
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-white/5 rounded-lg p-3 text-center">
                  <div className="text-2xl font-bold text-orange-400">
                    {formatNumber(wrappedData.stats.totalListeningHours)}
                  </div>
                  <div className="text-slate-400 text-xs">Hours</div>
                </div>
                <div className="bg-white/5 rounded-lg p-3 text-center">
                  <div className="text-2xl font-bold text-orange-400">
                    {formatNumber(wrappedData.stats.likesGiven)}
                  </div>
                  <div className="text-slate-400 text-xs">Likes</div>
                </div>
              </div>

              {/* Underground Support */}
              {wrappedData.undergroundSupportPercentage !== undefined && (
                <div className="bg-gradient-to-r from-orange-500/20 to-purple-500/20 rounded-lg p-4 text-center">
                  <div className="text-3xl font-bold text-white mb-1">
                    {wrappedData.undergroundSupportPercentage.toFixed(1)}%
                  </div>
                  <div className="text-slate-300 text-xs">
                    Support the Underground
                  </div>
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="relative z-10 text-center">
              <div className="text-slate-500 text-xs">soundwrapped.app</div>
            </div>
          </div>
        </div>

        <button
          onClick={downloadCard}
          disabled={isGenerating}
          className="w-full btn-primary flex items-center justify-center space-x-2 py-3"
        >
          <Download className="h-5 w-5" />
          <span>{isGenerating ? 'Generating...' : 'Download Card'}</span>
        </button>
      </motion.div>
    </div>
  )
}

export default ShareableStoryCard

