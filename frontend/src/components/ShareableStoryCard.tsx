import React, { useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Download, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { WrappedData } from '../contexts/MusicDataContext'
import { formatNumber } from '../utils/formatters'

interface ShareableStoryCardProps {
  wrappedData: WrappedData
  onClose: () => void
}

type CardType = 'summary' | 'listening' | 'top-track' | 'top-artist' | 'underground' | 'trendsetter' | 'repost' | 'archetype'
type ColorTheme = 'orange' | 'blue' | 'purple' | 'green' | 'red' | 'pink'
type FontSize = 'small' | 'medium' | 'large'

interface CardCustomization {
  cardType: CardType
  colorTheme: ColorTheme
  fontSize: FontSize
}

const ShareableStoryCard: React.FC<ShareableStoryCardProps> = ({ wrappedData, onClose }) => {
  const cardRef = useRef<HTMLDivElement>(null)
  const [isGenerating, setIsGenerating] = useState(false)
  const [customization, setCustomization] = useState<CardCustomization>({
    cardType: 'summary',
    colorTheme: 'orange',
    fontSize: 'medium'
  })

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
      const errorMessage = error instanceof Error ? error.message : 'Failed to generate card. Please try again.'
      toast.error(errorMessage, {
        icon: '❌',
        duration: 4000
      })
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
        <p className="text-slate-300 mb-4">Choose a card type and download for social media</p>

        {/* Card Type Selector */}
        <div className="mb-4">
          <label className="text-sm text-slate-300 mb-2 block">Card Type</label>
          <div className="flex flex-wrap gap-2">
            {[
              { id: 'summary', label: 'Summary' },
              { id: 'listening', label: 'Listening' },
              { id: 'top-track', label: 'Top Track' },
              { id: 'top-artist', label: 'Top Artist' },
              { id: 'underground', label: 'Underground' },
              { id: 'trendsetter', label: 'Trendsetter' },
              { id: 'repost', label: 'Repost' },
              { id: 'archetype', label: 'Archetype' }
            ].map((type) => (
              <button
                key={type.id}
                onClick={() => setCustomization({ ...customization, cardType: type.id as CardType })}
                className={`px-3 py-1 rounded-lg text-sm transition-colors ${
                  customization.cardType === type.id
                    ? 'bg-orange-500 text-white'
                    : 'bg-white/5 text-slate-300 hover:bg-white/10'
                }`}
              >
                {type.label}
              </button>
            ))}
          </div>
        </div>

        {/* Color Theme Selector */}
        <div className="mb-4">
          <label className="text-sm text-slate-300 mb-2 block">Color Theme</label>
          <div className="flex flex-wrap gap-2">
            {[
              { id: 'orange', label: 'Orange', color: 'bg-orange-500' },
              { id: 'blue', label: 'Blue', color: 'bg-blue-500' },
              { id: 'purple', label: 'Purple', color: 'bg-purple-500' },
              { id: 'green', label: 'Green', color: 'bg-green-500' },
              { id: 'red', label: 'Red', color: 'bg-red-500' },
              { id: 'pink', label: 'Pink', color: 'bg-pink-500' }
            ].map((theme) => (
              <button
                key={theme.id}
                onClick={() => setCustomization({ ...customization, colorTheme: theme.id as ColorTheme })}
                className={`px-3 py-1 rounded-lg text-sm transition-colors flex items-center gap-2 ${
                  customization.colorTheme === theme.id
                    ? 'bg-white/20 text-white border-2 border-white/50'
                    : 'bg-white/5 text-slate-300 hover:bg-white/10'
                }`}
              >
                <div className={`w-3 h-3 rounded-full ${theme.color}`} />
                {theme.label}
              </button>
            ))}
          </div>
        </div>

        {/* Font Size Selector */}
        <div className="mb-4">
          <label className="text-sm text-slate-300 mb-2 block">Font Size</label>
          <div className="flex gap-2">
            {[
              { id: 'small', label: 'Small' },
              { id: 'medium', label: 'Medium' },
              { id: 'large', label: 'Large' }
            ].map((size) => (
              <button
                key={size.id}
                onClick={() => setCustomization({ ...customization, fontSize: size.id as FontSize })}
                className={`px-4 py-2 rounded-lg text-sm transition-colors flex-1 ${
                  customization.fontSize === size.id
                    ? 'bg-orange-500 text-white'
                    : 'bg-white/5 text-slate-300 hover:bg-white/10'
                }`}
              >
                {size.label}
              </button>
            ))}
          </div>
        </div>

        {/* Card Preview - 9:16 aspect ratio for Instagram/TikTok stories */}
        <div className="mb-6 flex justify-center">
          <div
            ref={cardRef}
            className="w-[270px] h-[480px] rounded-2xl p-8 flex flex-col justify-between relative overflow-hidden"
            style={{ aspectRatio: '9/16' }}
          >
            {/* Render different card types based on selection */}
            {customization.cardType === 'summary' && (
              <>
                {/* Background - Dynamic gradient based on theme */}
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="absolute inset-0 opacity-20">
                  <div className={`absolute top-0 right-0 w-64 h-64 ${customization.colorTheme === 'orange' ? 'bg-orange-500' : customization.colorTheme === 'blue' ? 'bg-blue-500' : customization.colorTheme === 'purple' ? 'bg-purple-500' : customization.colorTheme === 'green' ? 'bg-green-500' : customization.colorTheme === 'red' ? 'bg-red-500' : 'bg-pink-500'} rounded-full blur-3xl`} />
                  <div className={`absolute bottom-0 left-0 w-64 h-64 ${customization.colorTheme === 'orange' ? 'bg-purple-500' : customization.colorTheme === 'blue' ? 'bg-indigo-500' : customization.colorTheme === 'purple' ? 'bg-pink-500' : customization.colorTheme === 'green' ? 'bg-teal-500' : customization.colorTheme === 'red' ? 'bg-orange-500' : 'bg-purple-500'} rounded-full blur-3xl`} />
                </div>

                {/* Content */}
                <div className="relative z-10 flex flex-col h-full justify-between">
                  <div className="text-center">
                    <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-2`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                      2024
                    </div>
                    <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} font-bold text-white mb-1`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                      {wrappedData.profile.username}
                    </div>
                    <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-slate-300`}>Your Year in Music</div>
                  </div>

                  <div className="space-y-4">
                    {wrappedData.topTracks.length > 0 && (
                      <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4 border border-white/20">
                        <div className="text-slate-300 text-xs mb-1 uppercase tracking-wider">Top Track</div>
                        <div className="text-white font-bold text-lg leading-tight">
                          {wrappedData.topTracks[0].title}
                        </div>
                        <div className="text-slate-400 text-sm mt-1">
                          by {wrappedData.topTracks[0].artist}
                        </div>
                      </div>
                    )}

                    <div className="grid grid-cols-2 gap-3">
                      <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4 text-center border border-white/20">
                        <div className="text-4xl font-black text-orange-400 mb-1" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                          {wrappedData.stats.totalListeningHours.toFixed(2).replace(/\.?0+$/, '')}
                        </div>
                        <div className="text-slate-300 text-xs uppercase tracking-wider">Hours</div>
                      </div>
                      <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4 text-center border border-white/20">
                        <div className="text-4xl font-black text-orange-400 mb-1" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                          {formatNumber(wrappedData.stats.likesGiven)}
                        </div>
                        <div className="text-slate-300 text-xs uppercase tracking-wider">Likes</div>
                      </div>
                    </div>

                    {wrappedData.undergroundSupportPercentage !== undefined && (
                      <div className="bg-gradient-to-br from-purple-600/40 to-pink-600/40 backdrop-blur-sm rounded-xl p-5 text-center border border-purple-500/30">
                        <div className="text-5xl font-black text-white mb-2" style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                          {wrappedData.undergroundSupportPercentage.toFixed(1)}%
                        </div>
                        <div className="text-white text-sm font-semibold">Support the Underground</div>
                      </div>
                    )}
                  </div>

                  <div className="text-center">
                    <div className="text-slate-500 text-xs">soundwrapped.app</div>
                  </div>
                </div>
              </>
            )}

            {customization.cardType === 'listening' && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-4`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.stats.totalListeningHours.toFixed(2).replace(/\.?0+$/, '')}
                  </div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} font-bold text-white mb-8`}>Hours Listening</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80`}>Your Year in Music</div>
                  <div className="absolute bottom-8 text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}

            {customization.cardType === 'top-track' && wrappedData.topTracks.length > 0 && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center px-6">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-4 uppercase tracking-widest`}>Top Track</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} font-black text-white mb-3 leading-tight`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.topTracks[0].title}
                  </div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} text-white/90 mb-8`}>by {wrappedData.topTracks[0].artist}</div>
                  <div className="text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}

            {customization.cardType === 'top-artist' && wrappedData.topArtists.length > 0 && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center px-6">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-4 uppercase tracking-widest`}>Top Artist</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-8`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.topArtists[0].artist}
                  </div>
                  <div className="text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}

            {customization.cardType === 'underground' && wrappedData.undergroundSupportPercentage !== undefined && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center px-6">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-4 uppercase tracking-widest`}>Support the Underground</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-4`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.undergroundSupportPercentage.toFixed(1)}%
                  </div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} text-white/90 mb-8`}>of your listening time went to artists with fewer than 5,000 followers</div>
                  <div className="text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}

            {customization.cardType === 'trendsetter' && wrappedData.trendsetterScore && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center px-6">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-4 uppercase tracking-widest`}>The Trendsetter</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-3`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.trendsetterScore.badge}
                  </div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} text-white/90 mb-2`}>Score: {wrappedData.trendsetterScore.score}</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-8`}>{wrappedData.trendsetterScore.description}</div>
                  <div className="text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}

            {customization.cardType === 'repost' && wrappedData.repostKingScore && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center px-6">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-4 uppercase tracking-widest`}>The Repost King/Queen</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-3`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.repostKingScore.badge}
                  </div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'title')} font-black text-white mb-2`}>{wrappedData.repostKingScore.trendingTracks}</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/90 mb-2`}>of {wrappedData.repostKingScore.repostedTracks} reposts became trending</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} text-white font-bold mb-8`}>{wrappedData.repostKingScore.percentage.toFixed(1)}% success rate</div>
                  <div className="text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}

            {customization.cardType === 'archetype' && wrappedData.sonicArchetype && (
              <>
                <div className={`absolute inset-0 ${getGradientClasses(customization.colorTheme)}`} />
                <div className="relative z-10 flex flex-col h-full justify-center items-center text-center px-6">
                  <div className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/80 mb-4 uppercase tracking-widest`}>Your Sonic Archetype</div>
                  <div className={`${getFontSizeClasses(customization.fontSize, 'subtitle')} font-black text-white mb-6 leading-tight`} style={{ fontFamily: 'system-ui, -apple-system, sans-serif' }}>
                    {wrappedData.sonicArchetype.split('\n')[0]}
                  </div>
                  {wrappedData.sonicArchetype.split('\n').slice(1).map((line, i) => (
                    <div key={i} className={`${getFontSizeClasses(customization.fontSize, 'body')} text-white/90 mb-2`}>{line}</div>
                  ))}
                  <div className="absolute bottom-8 text-white/60 text-xs">soundwrapped.app</div>
                </div>
              </>
            )}
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

