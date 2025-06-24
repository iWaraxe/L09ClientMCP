# Testing Guide

This guide covers testing approaches for the L09ClientMCP application, including unit tests, integration tests, and API testing with Postman.

## Test Structure

### Unit Tests
- **Location**: `src/test/java`
- **Coverage**: Service layer, controllers, utilities, and domain logic
- **Framework**: JUnit 5 with Mockito

### Integration Tests  
- **Location**: `src/test/java/integration`
- **Coverage**: End-to-end workflows and external integrations
- **Status**: Some complex integration tests are currently disabled pending Spring context optimization

### API Testing
- **Tool**: Postman collection
- **Location**: `postman/L09ClientMCP-Production-Ready.postman_collection.json`
- **Coverage**: All REST endpoints with comprehensive test scenarios

## Running Tests

### Maven Commands

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=ChatControllerTest

# Run tests with specific profile
./mvnw test -Dspring.profiles.active=test

# Skip tests during build
./mvnw clean package -DskipTests

# Run tests with coverage (if configured)
./mvnw jacoco:report
```

### Test Profiles

- **test**: Default test profile with security disabled and minimal dependencies
- **production-test**: For testing production features (currently experimental)

## Current Test Status

✅ **Passing Tests** (143 tests):
- Unit tests for services, controllers, and utilities
- MCP client functionality tests  
- Function invocation tests
- Server registry and load balancing tests

⚠️ **Skipped Tests** (23 tests):
- Integration tests requiring external servers
- Some production integration tests (complex Spring context setup)

❌ **Known Issues**:
- Production integration tests require additional Spring context configuration
- Load tests are disabled pending optimization

## Postman API Testing

### Setup

1. Import the collection: `postman/L09ClientMCP-Production-Ready.postman_collection.json`
2. Import environment: `postman/Local Development.postman_environment.json` 
3. Start the application: `./mvnw spring-boot:run`
4. Run the collection

### Test Categories

#### 1. Health & Status
- Chat service health check
- MCP status verification  
- Production monitoring endpoints

#### 2. Chat Operations
- Basic chat functionality
- Technical question handling
- Error handling for empty/invalid input

#### 3. MCP Operations
- Tool discovery (list available tools)
- Tool invocation (echo, ping, etc.)
- MCP server status

#### 4. Production Monitoring
- System status endpoints
- Metrics collection
- Health indicators

#### 5. Error Handling & Edge Cases
- Invalid JSON requests
- Nonexistent endpoints
- Large request bodies

#### 6. Performance Tests
- Rapid request handling
- Rate limiting behavior
- Response time validation

### Environment Variables

The Postman collection uses these variables:

```json
{
  "base_url": "http://localhost:8080",
  "large_text": "Generated automatically for testing"
}
```

### Test Assertions

Each request includes comprehensive test assertions:

- **Status Code Validation**: Ensures proper HTTP response codes
- **Response Structure**: Validates JSON response format
- **Performance**: Checks response times are within acceptable limits
- **Content Validation**: Verifies response contains expected data
- **Error Handling**: Tests proper error responses

### Running the Collection

#### Individual Requests
1. Select any request from the collection
2. Click "Send" 
3. Review the test results in the "Test Results" tab

#### Full Collection Run
1. Click the "..." menu next to the collection name
2. Select "Run collection"
3. Choose which requests to include
4. Configure iterations and delays
5. Click "Run L09ClientMCP - Production Ready"

#### Newman (Command Line)
```bash
# Install Newman globally
npm install -g newman

# Run the collection
newman run postman/L09ClientMCP-Production-Ready.postman_collection.json \
  --environment postman/Local\ Development.postman_environment.json \
  --reporters cli,html \
  --reporter-html-export test-results.html
```

## Test Data

### Sample Chat Messages
- "Hello! How are you today?" - Basic greeting
- Technical questions about Spring AI and MCP
- Empty messages for error testing
- Large text blocks for performance testing

### MCP Tool Testing
- Echo tool with various message types
- Ping functionality
- Tool discovery and listing

## Debugging Tests

### Common Issues

1. **Port Conflicts**
   ```bash
   # Check if port 8080 is in use
   lsof -i :8080
   
   # Use different port
   ./mvnw spring-boot:run -Dserver.port=8081
   ```

2. **OpenAI API Key Issues**
   ```bash
   # Set environment variable
   export OPENAI_API_KEY=your-key-here
   
   # Or add to application-test.yml
   spring.ai.openai.api-key: test-key
   ```

3. **Spring Context Issues**
   - Check for missing dependencies in test configuration
   - Verify @ActiveProfiles("test") is set
   - Ensure security is disabled for unit tests

### Test Logging

Enable debug logging for tests:

```yaml
# application-test.yml
logging:
  level:
    com.coherentsolutions.l09clientmcp: DEBUG
    org.springframework.test: DEBUG
```

## Best Practices

### Writing Tests
1. Use descriptive test method names
2. Follow Given-When-Then structure
3. Mock external dependencies
4. Test both success and failure scenarios
5. Validate response timing

### API Testing
1. Test happy path scenarios first
2. Include comprehensive error testing
3. Validate response structure and content
4. Test performance under load
5. Verify security measures

### Continuous Integration
- Tests should run in under 5 minutes
- All tests must pass before merge
- Include both unit and API tests in CI pipeline
- Generate test reports for visibility

This testing approach ensures the L09ClientMCP application is thoroughly validated across all components and ready for production deployment.