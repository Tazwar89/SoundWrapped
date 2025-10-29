import React from 'react'
import { LucideIcon } from 'lucide-react'
import { motion } from 'framer-motion'
import { cn } from '../utils/cn'

interface StatCardProps {
  title: string
  value: string
  icon: LucideIcon
  color: string
  change?: string
  changeType?: 'positive' | 'negative' | 'neutral'
}

const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  icon: Icon,
  color,
  change,
  changeType = 'neutral'
}) => {
  return (
    <motion.div
      whileHover={{ scale: 1.02 }}
      className="stat-card group"
    >
      <div className="flex items-center justify-between mb-4">
        <div className={`p-3 rounded-lg bg-gradient-to-r ${color} group-hover:scale-110 transition-transform`}>
          <Icon className="h-6 w-6 text-white" />
        </div>
        {change && (
          <span
            className={cn(
              'text-sm font-medium px-2 py-1 rounded-full',
              changeType === 'positive' && 'text-green-400 bg-green-400/10',
              changeType === 'negative' && 'text-red-400 bg-red-400/10',
              changeType === 'neutral' && 'text-slate-400 bg-slate-400/10'
            )}
          >
            {change}
          </span>
        )}
      </div>
      <div>
        <div className="text-2xl font-bold text-slate-200 mb-1">{value}</div>
        <div className="text-slate-400 text-sm">{title}</div>
      </div>
    </motion.div>
  )
}

export default StatCard
