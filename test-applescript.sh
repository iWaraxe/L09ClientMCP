#!/bin/bash

# Test script for AppleScript MCP integration
# This script demonstrates the stunning desktop integration capabilities

BASE_URL="http://localhost:8080"

echo "🍎 Testing AppleScript MCP Integration"
echo "======================================"

# Check if server is running
echo "1. Checking server health..."
curl -s "${BASE_URL}/api/chat/health" | jq '.' || echo "Server not responding"
echo

# Check AppleScript status
echo "2. Checking AppleScript MCP status..."
curl -s "${BASE_URL}/api/applescript/status" | jq '.' 
echo

# Test battery status via direct endpoint
echo "3. Testing battery status (direct endpoint)..."
curl -s "${BASE_URL}/api/applescript/battery" | jq '.'
echo

# Test battery status via chat interface
echo "4. Testing battery status via chat interface..."
curl -s -X POST "${BASE_URL}/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "What is my Mac battery status?"}' | jq '.'
echo

# Test system notification
echo "5. Sending system notification..."
curl -s -X POST "${BASE_URL}/api/applescript/notification" \
  -H "Content-Type: application/json" \
  -d '{"title": "Spring AI MCP Demo", "message": "AppleScript integration is working!"}' | jq '.'
echo

# Test notification via chat
echo "6. Testing notification via chat interface..."
curl -s -X POST "${BASE_URL}/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "Show me a notification saying Spring AI rocks!"}' | jq '.'
echo

# Test volume control
echo "7. Getting current volume level..."
curl -s "${BASE_URL}/api/applescript/volume" | jq '.'
echo

# Test volume control via chat
echo "8. Testing volume control via chat..."
curl -s -X POST "${BASE_URL}/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "What is my Mac volume level?"}' | jq '.'
echo

# Test system information
echo "9. Getting system information..."
curl -s "${BASE_URL}/api/applescript/system-info" | jq '.'
echo

# Test opening an application
echo "10. Opening Calculator app..."
curl -s -X POST "${BASE_URL}/api/applescript/open-app" \
  -H "Content-Type: application/json" \
  -d '{"appName": "Calculator"}' | jq '.'
echo

# Test multi-server status
echo "11. Checking multi-server MCP status..."
curl -s "${BASE_URL}/api/mcp/status" | jq '.'
echo

# Test available tools
echo "12. Listing available MCP tools..."
curl -s "${BASE_URL}/api/mcp/tools" | jq '.'
echo

echo "🎉 AppleScript MCP testing complete!"
echo
echo "Demo Scenarios to try:"
echo "  • 'What's my battery status?'"
echo "  • 'Show me a notification saying hello'"
echo "  • 'Set volume to 75'"
echo "  • 'Open Safari'"
echo "  • 'Get my system information'"