# Groq API Implementation for AI-Powered Features

## Overview

SoundWrapped uses **Groq API** (OpenAI-compatible) for AI-powered description generation and creative content. Groq provides a free tier with fast inference, making it ideal for generating artist descriptions, genre descriptions, and personalized poetry.

## Why Groq?

- **Free Tier**: Generous free tier with no credit card required
- **Fast Inference**: Ultra-fast response times with optimized models
- **OpenAI-Compatible**: Uses OpenAI API format, making integration straightforward
- **High-Quality Models**: Access to models like `llama-3.3-70b-versatile`

## Implementation

### Model Used
- **Model**: `llama-3.3-70b-versatile`
- **Base URL**: `https://api.groq.com/openai/v1`
- **API Format**: OpenAI-compatible chat completions

### Features Using Groq

#### 1. Artist Descriptions
- **Location**: `SoundWrappedService.getGroqDescription()`
- **Process**: 
  1. Research phase: Fetches data from Wikipedia, Google Knowledge Graph, and SerpAPI
  2. Aggregates research context
  3. Sends single request to Groq with research context
  4. Generates 2-3 sentence description (50-100 words)
- **Token Limit**: 150 tokens max

#### 2. Genre Descriptions
- **Location**: `SoundWrappedService.getFeaturedGenreWithTracks()`
- **Process**: Similar to artist descriptions, generates concise genre descriptions
- **Token Limit**: 150 tokens max

#### 3. Year in Review Poetry
- **Location**: `SoundWrappedService.generateYearInReviewPoetry()`
- **Process**:
  1. Collects user's top tracks and genres
  2. Creates personalized prompt
  3. Generates 4-6 line poem celebrating the user's musical journey
- **Token Limit**: 150 tokens max
- **Temperature**: 0.7 (for creativity)

#### 4. Sonic Archetype (Musical Persona)
- **Location**: `SoundWrappedService.generateSonicArchetype()`
- **Process**:
  1. Analyzes user's top genres, artists, and listening patterns
  2. Creates creative persona prompt
  3. Generates persona title and 2-3 sentence description
- **Token Limit**: 200 tokens max
- **Temperature**: 0.8 (for more creativity)

## Configuration

### Environment Variables

```env
GROQ_API_KEY=your_groq_api_key_here
```

### Application Configuration

```yaml
groq:
  api-key: ${GROQ_API_KEY:}
  base-url: https://api.groq.com/openai/v1
```

## API Request Format

```json
{
  "model": "llama-3.3-70b-versatile",
  "messages": [
    {
      "role": "user",
      "content": "Write a description about [entity]..."
    }
  ],
  "temperature": 0.7,
  "max_tokens": 150
}
```

## Error Handling

- **Invalid API Key**: Detects and logs error, returns null
- **Insufficient Quota**: Handles quota errors gracefully
- **Network Errors**: Comprehensive try-catch with fallback descriptions
- **Empty Responses**: Validates response before returning

## Fallback Mechanisms

If Groq API fails, the system falls back to:
1. **Wikipedia extract** (for artists/genres)
2. **Google Knowledge Graph description** (for entities)
3. **SoundCloud bio** (for artists)
4. **Generic fallback** (default descriptions)

## Rate Limiting

Groq free tier includes:
- Generous rate limits
- Fast response times
- No credit card required

## Migration from OpenAI

The codebase was migrated from OpenAI to Groq due to:
- OpenAI quota limitations
- Cost considerations
- Groq's free tier availability

The migration was straightforward due to OpenAI-compatible API format.

