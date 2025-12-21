# OpenAI API Implementation for Artist/Genre Descriptions

## Overview

SoundWrapped uses **OpenAI's Function Calling** feature to generate context-aware descriptions for "Artist of the Day" and "Genre of the Day". This implementation follows a **Retrieval Augmented Generation (RAG)** approach where OpenAI conducts research before generating descriptions.

## How It Works

### 1. Function Calling Architecture

The system uses OpenAI's **function calling** (also called "tools") feature, which allows the AI model to request external information when needed. This is more efficient than always doing research upfront.

### 2. The Process Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Step 1: Define Search Functions as Tools                      │
│ - search_wikipedia(search_term)                               │
│ - search_google_knowledge_graph(search_term)                  │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 2: Send Initial Prompt to OpenAI                         │
│ "Write about [Artist/Genre]. Use search functions to         │
│  research from Wikipedia and Google Knowledge Graph."         │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 3: OpenAI Decides to Call Functions                     │
│ Model analyzes the prompt and decides:                       │
│ - "I need to search for 'Pooh Shiesty music artist'"        │
│ - Calls search_wikipedia() and/or                            │
│ - Calls search_google_knowledge_graph()                      │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 4: Execute Function Calls                               │
│ Backend executes the requested searches:                     │
│ - Queries Wikipedia API                                      │
│ - Queries Google Knowledge Graph API                         │
│ - Returns results to OpenAI                                  │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 5: OpenAI Generates Final Description                    │
│ Model receives research results and synthesizes:             │
│ - A 3-5 sentence paragraph (100-200 words)                   │
│ - Context-aware and accurate                                 │
│ - Focused on music context                                   │
└─────────────────────────────────────────────────────────────┘
```

### 3. Technical Implementation

#### Function Definitions

Two search functions are defined as "tools" for OpenAI:

1. **`search_wikipedia`**
   - **Purpose**: Search Wikipedia for artist/genre information
   - **Parameter**: `search_term` (string) - with "music artist" or "music genre" appended
   - **Returns**: Wikipedia article summary or "No information found"

2. **`search_google_knowledge_graph`**
   - **Purpose**: Search Google Knowledge Graph for structured data
   - **Parameter**: `search_term` (string) - with "music artist" or "music genre" appended
   - **Returns**: Knowledge Graph description or "No information found"

#### API Request Structure

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user",
      "content": "Write about Pooh Shiesty. Use search functions..."
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "search_wikipedia",
        "description": "Search Wikipedia...",
        "parameters": {
          "type": "object",
          "properties": {
            "search_term": {
              "type": "string",
              "description": "Search term with 'music artist' appended"
            }
          },
          "required": ["search_term"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "search_google_knowledge_graph",
        ...
      }
    }
  ],
  "tool_choice": "auto",
  "temperature": 0.7,
  "max_tokens": 250
}
```

#### Conversation Loop

The implementation uses a **conversation loop** (max 5 iterations) to handle function calling:

1. **Iteration 1**: Send prompt → Model requests function calls
2. **Iteration 2**: Execute functions → Send results back → Model generates description
3. **Iteration 3+**: If needed, model can request more searches or refine

#### Response Handling

- **Function Calls Detected**: Execute searches, add results to conversation, continue loop
- **Final Response**: Extract text content, validate length (>30 chars), check for "no information" indicators
- **Error Handling**: Log detailed errors, check API key validity, handle rate limits

### 4. Key Features

#### Context-Aware Search
- All search terms automatically append "music artist" or "music genre"
- Prevents ambiguity (e.g., "wave" music genre vs. physics wave)

#### Dynamic Research
- Model decides when and what to search
- Can request multiple searches if needed
- More efficient than always searching upfront

#### Quality Control
- Validates response length (30-1000 characters)
- Checks for "no information" indicators
- Truncates at sentence boundaries if too long
- Returns `null` if quality checks fail (no fallback to generic descriptions)

### 5. Configuration

- **Model**: `gpt-4o-mini` (cost-effective, fast)
- **Temperature**: `0.7` (balanced creativity/accuracy)
- **Max Tokens**: `250` (ensures ~100-200 word paragraphs)
- **API Key**: Loaded from `.env` file via Dotenv library

### 6. Error Handling

- **API Key Issues**: Checks multiple sources (@Value, system property, environment variable)
- **HTTP Errors**: Detailed logging with status codes and response bodies
- **Function Execution Errors**: Graceful fallback with error messages
- **JSON Parsing Errors**: Robust parsing with fallback search terms

## Benefits of This Approach

1. **Accuracy**: Research-backed descriptions from authoritative sources
2. **Efficiency**: Only searches when model determines it's needed
3. **Flexibility**: Model can refine searches based on initial results
4. **Context-Aware**: Ensures music-specific information (not generic definitions)
5. **Cost-Effective**: Uses `gpt-4o-mini` and only makes necessary API calls

## Current Limitations

- Maximum 5 iterations to prevent infinite loops
- Requires valid API keys for OpenAI, Wikipedia, and Google Knowledge Graph
- No caching of descriptions (generated fresh each time, but Artist/Genre of the Day is cached)
- Falls back to hardcoded descriptions for genres if OpenAI fails

