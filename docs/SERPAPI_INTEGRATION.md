# SerpAPI Integration for SoundWrapped

## Overview

[SerpAPI](https://serpapi.com/use-cases/web-search-api) is a powerful web search API that provides structured search results from Google, Bing, DuckDuckGo, Yahoo, and other search engines. Integrating SerpAPI into SoundWrapped would significantly enhance the research capabilities for generating artist and genre descriptions.

## Why SerpAPI is Valuable for SoundWrapped

### Current Implementation
SoundWrapped currently uses:
1. **Wikipedia API** - For article summaries
2. **Google Knowledge Graph API** - For structured entity data

### SerpAPI Advantages

#### 1. **Comprehensive Data in One Call**
SerpAPI can extract multiple data types from a single search:
- **Knowledge Graph**: Same data as Google Knowledge Graph API
- **Answer Box**: Quick facts and definitions
- **Organic Results**: Top search results from various sources
- **Related Questions**: "People also ask" questions
- **Images**: Artist photos, album artwork
- **Videos**: Music videos, interviews
- **News**: Latest news about artists

#### 2. **More Reliable & Up-to-Date**
- Real-time search results (not cached)
- Multiple search engines (Google, Bing, DuckDuckGo)
- Latest information about artists (new releases, tours, etc.)

#### 3. **Simplified Architecture**
- **Option A**: Replace Google Knowledge Graph API with SerpAPI (can extract KG data)
- **Option B**: Add SerpAPI as a third search function alongside Wikipedia and Google KG
- **Option C**: Use SerpAPI as primary search, with Wikipedia as fallback

#### 4. **Better Context for OpenAI**
- Multiple sources in one response = richer context
- Answer boxes provide quick, accurate facts
- Related questions help understand what users want to know

## Integration Strategy

### Recommended Approach: Add SerpAPI as Third Search Function

**Benefits:**
- Keeps existing Wikipedia and Google KG functions (redundancy)
- Adds comprehensive web search capability
- OpenAI can choose the best source(s) for each query

### Implementation Plan

#### 1. Add SerpAPI Search Function to OpenAI Tools

```java
// Define SerpAPI search function
Map<String, Object> serpApiFunction = new HashMap<>();
serpApiFunction.put("name", "search_web");
serpApiFunction.put("description", "Search the web using Google for comprehensive information about a music artist or music genre. Returns knowledge graph, answer boxes, organic results, and related questions. Always append 'music artist' or 'music genre' to the search term for better context.");
Map<String, Object> serpApiParams = new HashMap<>();
serpApiParams.put("type", "object");
Map<String, Object> serpApiProperties = new HashMap<>();
Map<String, Object> searchTermProp = new HashMap<>();
searchTermProp.put("type", "string");
searchTermProp.put("description", "The search term to look up. For artists, append 'music artist'. For genres, append 'music genre'.");
serpApiProperties.put("search_term", searchTermProp);
serpApiParams.put("properties", serpApiProperties);
serpApiParams.put("required", java.util.Arrays.asList("search_term"));
serpApiFunction.put("parameters", serpApiParams);
```

#### 2. Implement SerpAPI Search Method

```java
/**
 * Searches the web using SerpAPI for comprehensive information.
 * Extracts knowledge graph, answer box, organic results, and related questions.
 * 
 * @param searchTerm The search term (with "music artist" or "music genre" appended)
 * @return Formatted search results combining knowledge graph, answer box, and top organic results
 */
private String getSerpAPIDescription(String searchTerm) {
    try {
        String encodedQuery = java.net.URLEncoder.encode(searchTerm, "UTF-8");
        String serpApiUrl = "https://serpapi.com/search.json" +
            "?q=" + encodedQuery +
            "&api_key=" + serpApiKey +
            "&engine=google" +
            "&num=5"; // Get top 5 organic results
        
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            serpApiUrl,
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            return null;
        }
        
        StringBuilder result = new StringBuilder();
        
        // Extract Knowledge Graph (if available)
        @SuppressWarnings("unchecked")
        Map<String, Object> knowledgeGraph = (Map<String, Object>) responseBody.get("knowledge_graph");
        if (knowledgeGraph != null) {
            String description = (String) knowledgeGraph.get("description");
            if (description != null && !description.trim().isEmpty()) {
                result.append("Knowledge Graph: ").append(description).append("\n\n");
            }
            
            // Get extended description if available
            @SuppressWarnings("unchecked")
            Map<String, Object> descriptionSource = (Map<String, Object>) knowledgeGraph.get("description_source");
            if (descriptionSource != null) {
                String link = (String) descriptionSource.get("link");
                if (link != null) {
                    result.append("Source: ").append(link).append("\n\n");
                }
            }
        }
        
        // Extract Answer Box (if available)
        @SuppressWarnings("unchecked")
        Map<String, Object> answerBox = (Map<String, Object>) responseBody.get("answer_box");
        if (answerBox != null) {
            String answer = (String) answerBox.get("answer");
            if (answer != null && !answer.trim().isEmpty()) {
                result.append("Quick Facts: ").append(answer).append("\n\n");
            }
            
            String snippet = (String) answerBox.get("snippet");
            if (snippet != null && !snippet.trim().isEmpty()) {
                result.append("Details: ").append(snippet).append("\n\n");
            }
        }
        
        // Extract top organic results (snippets)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> organicResults = (List<Map<String, Object>>) responseBody.get("organic_results");
        if (organicResults != null && !organicResults.isEmpty()) {
            result.append("Additional Information:\n");
            int count = Math.min(3, organicResults.size()); // Get top 3 results
            for (int i = 0; i < count; i++) {
                Map<String, Object> resultItem = organicResults.get(i);
                String snippet = (String) resultItem.get("snippet");
                if (snippet != null && !snippet.trim().isEmpty()) {
                    result.append("- ").append(snippet).append("\n");
                }
            }
        }
        
        String finalResult = result.toString().trim();
        return finalResult.isEmpty() ? null : finalResult;
        
    } catch (Exception e) {
        System.err.println("SerpAPI search failed for '" + searchTerm + "': " + e.getMessage());
        e.printStackTrace();
        return null;
    }
}
```

#### 3. Update OpenAI Prompt

Update the prompt to mention SerpAPI as an additional research source:

```java
prompt = String.format(
    "Write an informative paragraph (approximately 3-5 sentences, 100-200 words) about the music artist '%s'. " +
    "Use the search functions to research information from Wikipedia, Google Knowledge Graph, and web search (SerpAPI). " +
    "Always append 'music artist' to your search terms (e.g., search for '%s music artist'). " +
    "The web search function provides comprehensive results including knowledge graph, answer boxes, and top search results. " +
    "Include: their background and career, musical style and genre, notable works or achievements, " +
    "and their influence on music. Write in an engaging, informative style suitable for a music discovery platform. " +
    "Write exactly one paragraph - do not use multiple paragraphs.",
    entityName, entityName
);
```

## Configuration

### Add to `application.yml`

```yaml
serpapi:
  api-key: ${SERPAPI_API_KEY:}
```

### Add to `.env` file

```env
SERPAPI_API_KEY=your_serpapi_api_key_here
```

### Add to `.envs.example`

```env
SERPAPI_API_KEY=your_serpapi_api_key_here
```

### Update `docker-compose.yml`

Add environment variable to backend service:

```yaml
backend:
  environment:
    - SERPAPI_API_KEY=${SERPAPI_API_KEY}
```

## Benefits of This Integration

### 1. **More Comprehensive Research**
- Single API call can return knowledge graph, answer boxes, and organic results
- Multiple sources in one response = better context for OpenAI

### 2. **Better for Obscure Artists/Genres**
- Web search can find information even when Wikipedia/KG don't have entries
- Can find information from music blogs, news sites, etc.

### 3. **Real-Time Information**
- Latest news about artists (new releases, tours, collaborations)
- Current trends and popularity

### 4. **Cost Efficiency**
- Free tier: 250 searches/month (good for testing)
- Can potentially replace Google Knowledge Graph API (one less API key needed)
- Pay-per-use pricing for production

### 5. **Flexibility**
- OpenAI can choose when to use SerpAPI vs. Wikipedia vs. Google KG
- Can use SerpAPI for comprehensive search, Wikipedia for detailed articles

## Alternative: Replace Google Knowledge Graph with SerpAPI

**Pros:**
- One less API key to manage
- SerpAPI can extract knowledge graph data from Google searches
- More comprehensive results

**Cons:**
- Lose direct Google Knowledge Graph API access
- SerpAPI has rate limits (250/month free, then paid)

**Recommendation:** Start by adding SerpAPI as a third function, then evaluate if Google KG is still needed.

## Implementation Steps

1. **Get SerpAPI Key** (from user)
2. **Add Configuration** - Update `application.yml`, `.env`, `.envs.example`
3. **Add Dependency** - No special dependency needed (uses RestTemplate)
4. **Implement `getSerpAPIDescription()` method**
5. **Add SerpAPI function to OpenAI tools**
6. **Update prompts** to mention web search
7. **Test** with various artists and genres
8. **Monitor** API usage and costs

## Example SerpAPI Response Structure

```json
{
  "knowledge_graph": {
    "title": "Pooh Shiesty",
    "type": "Rapper",
    "description": "Lontrell Donell Williams Jr., known professionally as Pooh Shiesty, is an American rapper...",
    "description_source": {
      "link": "https://en.wikipedia.org/wiki/Pooh_Shiesty"
    }
  },
  "answer_box": {
    "answer": "American rapper from Memphis, Tennessee",
    "snippet": "Pooh Shiesty is known for his drill-influenced style..."
  },
  "organic_results": [
    {
      "title": "Pooh Shiesty - Wikipedia",
      "snippet": "Lontrell Donell Williams Jr., known professionally as Pooh Shiesty...",
      "link": "https://en.wikipedia.org/wiki/Pooh_Shiesty"
    },
    {
      "title": "Pooh Shiesty - Latest News",
      "snippet": "Latest album release and tour dates...",
      "link": "https://example.com/news"
    }
  ],
  "related_questions": [
    {
      "question": "What is Pooh Shiesty known for?",
      "snippet": "Pooh Shiesty is known for..."
    }
  ]
}
```

## Cost Considerations

- **Free Tier**: 250 searches/month
- **Paid Plans**: Starting at $50/month for 5,000 searches
- **Usage**: For "Artist of the Day" and "Genre of the Day" (2 searches/day), free tier covers ~125 days

## Next Steps

1. **User provides SerpAPI key**
2. **Implement integration** following the plan above
3. **Test with various artists/genres**
4. **Monitor API usage**
5. **Evaluate if Google Knowledge Graph API is still needed**

## References

- [SerpAPI Documentation](https://serpapi.com/use-cases/web-search-api)
- [SerpAPI Java Client](https://github.com/serpapi/serpapi-java)
- [SerpAPI Pricing](https://serpapi.com/pricing)

