import { useState, useCallback } from 'react'

interface RetryOptions {
  maxRetries?: number
  retryDelay?: number
  onRetry?: (attempt: number) => void
}

/**
 * Custom hook for retrying failed operations with exponential backoff.
 * Useful for API calls that might fail due to network issues.
 */
export function useRetry<T>(
  operation: () => Promise<T>,
  options: RetryOptions = {}
) {
  const { maxRetries = 3, retryDelay = 1000, onRetry } = options
  const [isRetrying, setIsRetrying] = useState(false)
  const [retryCount, setRetryCount] = useState(0)

  const executeWithRetry = useCallback(async (): Promise<T> => {
    let lastError: Error | null = null
    
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        if (attempt > 0) {
          setIsRetrying(true)
          setRetryCount(attempt)
          onRetry?.(attempt)
          
          // Exponential backoff: delay = retryDelay * 2^(attempt-1)
          const delay = retryDelay * Math.pow(2, attempt - 1)
          await new Promise(resolve => setTimeout(resolve, delay))
        }
        
        const result = await operation()
        setIsRetrying(false)
        setRetryCount(0)
        return result
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error))
        
        // Don't retry on 4xx errors (client errors)
        if (error && typeof error === 'object' && 'response' in error) {
          const status = (error as any).response?.status
          if (status >= 400 && status < 500) {
            throw lastError
          }
        }
        
        // If this was the last attempt, throw the error
        if (attempt === maxRetries) {
          setIsRetrying(false)
          setRetryCount(0)
          throw lastError
        }
      }
    }
    
    setIsRetrying(false)
    setRetryCount(0)
    throw lastError || new Error('Operation failed after retries')
  }, [operation, maxRetries, retryDelay, onRetry])

  return {
    executeWithRetry,
    isRetrying,
    retryCount
  }
}

