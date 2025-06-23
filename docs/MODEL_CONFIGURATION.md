# Model Configuration Guide

## Overview

This guide explains how to configure different AI models and settings for educational demonstrations, particularly to show the limitations that MCP addresses.

## Configuration Files

### `application.yml` (Default)
- **Model**: `gpt-4o-mini` (balanced cost/performance)
- **Temperature**: `0.7` (moderately creative)
- **Max Tokens**: `1000` (reasonable response length)
- **Use Case**: General development and testing

### `application-gpt4.yml` (High Accuracy)
- **Model**: `gpt-4o` (most accurate, slower, more expensive)
- **Temperature**: `0.3` (more consistent responses)
- **Max Tokens**: `1500` (longer detailed responses)
- **Use Case**: When you need the most accurate responses

### `application-demo.yml` (Training Optimized)
- **Model**: `gpt-3.5-turbo` (fast and cost-effective)
- **Temperature**: `0.8` (more varied responses)
- **Max Tokens**: `800` (shorter for easier reading)
- **Use Case**: Training sessions with many API calls

## Switching Models

### Method 1: Using Spring Profiles
```bash
# Use GPT-4 for high accuracy
./mvnw spring-boot:run -Dspring.profiles.active=gpt4

# Use optimized settings for demos
./mvnw spring-boot:run -Dspring.profiles.active=demo

# Use default settings
./mvnw spring-boot:run
```

### Method 2: Environment Variables
```bash
# Override specific settings
export OPENAI_MODEL=gpt-4o
export TEMPERATURE=0.5
./mvnw spring-boot:run
```

### Method 3: Runtime Configuration
Edit `application.yml` and restart the application.

## Teaching Scenarios

### Demonstrating Knowledge Limitations

**Perfect Questions to Show Outdated Knowledge:**

1. **Spring AI Version**:
   ```json
   {"message": "What is the current version of Spring AI and when was it released?"}
   ```
   **Expected Issue**: Will likely mention older versions (0.x) instead of 1.0.0

2. **Recent Technology**:
   ```json
   {"message": "Tell me about the Model Context Protocol. Who developed it and when?"}
   ```
   **Expected Issue**: May attribute to wrong company or give wrong dates

3. **Current Events**:
   ```json
   {"message": "What are the latest features in Java 21 and Spring Boot 3.3?"}
   ```
   **Expected Issue**: May miss recent updates or provide outdated information

### Model Comparison for Teaching

#### **Use gpt-3.5-turbo when:**
- Demonstrating basic AI capabilities
- Showing cost-effective AI integration
- Running many demo requests
- Students are following along with their own API keys

#### **Use gpt-4o when:**
- Demonstrating highest AI capabilities
- Showing complex reasoning
- Important demonstrations where accuracy matters
- Comparing AI model capabilities

#### **Use gpt-4o-mini when:**
- Balanced demonstrations (default choice)
- Regular development work
- Testing and validation

## Configuration Parameters Explained

### Model Selection
```yaml
model: gpt-4o-mini  # Model name from OpenAI
```
**Options**: `gpt-4o`, `gpt-4o-mini`, `gpt-3.5-turbo`

### Temperature (0.0 - 2.0)
```yaml
temperature: 0.7
```
- **0.0-0.3**: Deterministic, consistent responses
- **0.4-0.7**: Balanced creativity and consistency
- **0.8-1.0**: More creative and varied responses
- **1.0+**: Very creative, potentially inconsistent

### Max Tokens
```yaml
max-tokens: 1000
```
Controls response length. Typical values:
- **200-500**: Brief responses
- **500-1000**: Standard responses  
- **1000+**: Detailed responses

## System Message Impact

The current system message explicitly mentions knowledge cutoff:

```java
"You are a helpful AI assistant. Be concise and informative. " +
"Note: Your knowledge has a cutoff date and may not include the most recent information about " +
"software versions, recent developments, or current events. When discussing specific versions " +
"or recent changes, acknowledge this limitation."
```

This helps AI acknowledge its limitations, making the need for MCP more apparent.

## Educational Value

### Before MCP (Current State)
- ❌ **Outdated Information**: Knowledge cutoff limitations
- ❌ **Static Responses**: Same answers regardless of when asked
- ❌ **No Real-time Data**: Cannot access current documentation
- ❌ **Version Mismatches**: May reference wrong software versions

### After MCP (Future State)
- ✅ **Current Information**: Access to real-time data via MCP tools
- ✅ **Dynamic Responses**: Can fetch latest information
- ✅ **Live Documentation**: Access current docs and APIs
- ✅ **Accurate Versions**: Query package registries for current versions

## Cost Considerations

Approximate costs per 1K tokens (as of 2024):
- **gpt-3.5-turbo**: ~$0.001 (very economical for training)
- **gpt-4o-mini**: ~$0.0002 (excellent value)
- **gpt-4o**: ~$0.03 (premium but most capable)

For training sessions with 50+ API calls, consider using `demo` profile with gpt-3.5-turbo.

## Troubleshooting

### Model Not Found Error
```
Error: The model `gpt-x` does not exist
```
**Solution**: Check OpenAI's current model list and update configuration.

### Rate Limit Errors
```
Error: Rate limit exceeded
```
**Solution**: 
- Use slower models (gpt-3.5-turbo)
- Add delays between demo requests
- Upgrade OpenAI plan if needed

### Token Limit Errors
```
Error: This model's maximum context length
```
**Solution**: Reduce `max-tokens` or simplify the request.

## Best Practices

1. **Start with Default**: Use `application.yml` for initial setup
2. **Demo Profile**: Use `demo` profile for training sessions
3. **Cost Awareness**: Monitor API usage during training
4. **Model Comparison**: Show students different model capabilities
5. **Error Handling**: Demonstrate graceful degradation when API fails