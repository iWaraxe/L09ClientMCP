#!/bin/bash

# Test examples for the MCP Client Application
# This script provides a quick command-line demo of the API functionality

echo "🚀 L09ClientMCP API Test Suite"
echo "=================================="
echo

# Check if application is running
echo "🏥 1. Health Check:"
health_response=$(curl -s -w "%{http_code}" http://localhost:8080/api/chat/health)
http_code="${health_response: -3}"
response_body="${health_response%???}"

if [ "$http_code" = "200" ]; then
    echo "✅ Status: $http_code"
    echo "📄 Response: $response_body"
else
    echo "❌ Health check failed with status: $http_code"
    echo "💡 Make sure the application is running: ./mvnw spring-boot:run"
    exit 1
fi
echo

echo "💬 2. Simple Chat Request:"
echo "📤 Request: What is Spring AI?"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Spring AI?"}' | jq -r '.response // "Error: Could not parse response"'
echo

echo "🔗 3. MCP Concept Question:"
echo "📤 Request: What is Model Context Protocol?"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Model Context Protocol and how does it help AI applications access external tools?"}' | jq -r '.response // "Error: Could not parse response"'
echo

echo "🏗️ 4. Technical Architecture Question:"
echo "📤 Request: Spring Boot best practices"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What are the best practices for creating REST APIs in Spring Boot?"}' | jq -r '.response // "Error: Could not parse response"'
echo

echo "⚠️ 5. Error Handling Test:"
echo "📤 Request: Empty message"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": ""}' | jq -r '.response // "Error: Could not parse response"'
echo

echo "✅ All tests completed!"
echo "💡 For visual testing, import the Postman collection: postman/L09ClientMCP.postman_collection.json"
echo "📚 See docs/VISUAL_TESTING.md for detailed testing guide"