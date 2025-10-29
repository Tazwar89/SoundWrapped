import React from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Home, ArrowLeft, Music } from 'lucide-react'

const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8 }}
          className="mb-8"
        >
          <div className="w-32 h-32 bg-gradient-to-r from-primary-500 to-soundcloud-500 rounded-full flex items-center justify-center mx-auto mb-6">
            <Music className="h-16 w-16 text-white" />
          </div>
          <h1 className="text-6xl md:text-8xl font-bold gradient-text mb-4">404</h1>
          <h2 className="text-2xl md:text-3xl font-semibold text-slate-200 mb-4">
            Page Not Found
          </h2>
          <p className="text-slate-300 mb-8 max-w-md mx-auto">
            Looks like this page got lost in the music. Let's get you back to the rhythm!
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.3 }}
          className="flex flex-col sm:flex-row gap-4 justify-center"
        >
          <Link
            to="/"
            className="btn-primary inline-flex items-center space-x-2"
          >
            <Home className="h-4 w-4" />
            <span>Go Home</span>
          </Link>
          <button
            onClick={() => window.history.back()}
            className="px-6 py-3 bg-white/5 hover:bg-white/10 rounded-lg transition-colors inline-flex items-center space-x-2"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Go Back</span>
          </button>
        </motion.div>
      </div>
    </div>
  )
}

export default NotFoundPage
