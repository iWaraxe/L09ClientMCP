#!/bin/bash

# Test examples for the MCP Client Application

echo "=== Testing Chat Service ==="
echo

echo "1. Health Check:"
curl -s http://localhost:8080/api/chat/health
echo -e "\n"

echo "2. Simple Chat Request:"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Spring AI?"}'
echo -e "\n"

echo "3. Question about MCP:"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Model Context Protocol?"}'
echo -e "\n"

echo "4. Programming Help:"
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "How do I create a REST endpoint in Spring Boot?"}'
echo -e "\n"