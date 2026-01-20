import React, { lazy, Suspense } from 'react'
import { Outlet } from 'react-router-dom'
import Header from './Header'
import Footer from './Footer'

// Lazy load heavy WebGL background component
const WebGLBackground = lazy(() => import('./WebGLBackground'))

interface LayoutProps {
  children?: React.ReactNode
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  return (
    <div className="min-h-screen flex flex-col relative" style={{ background: 'transparent' }}>
      <Suspense fallback={null}>
        <WebGLBackground />
      </Suspense>
      <div className="relative z-10" style={{ background: 'transparent' }}>
        <Header />
        <main className="flex-1" style={{ background: 'transparent' }}>
          {children || <Outlet />}
        </main>
        <Footer />
      </div>
    </div>
  )
}

export default Layout
