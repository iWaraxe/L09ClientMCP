package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.production.audit.AuditLogger;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(McpController.class)
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class McpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpClientService mcpClientService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public McpClientService mcpClientService() {
            return mock(McpClientService.class);
        }
        
        @Bean
        @Primary
        public AuditLogger auditLogger() {
            return mock(AuditLogger.class);
        }
    }

    @Test
    void testGetStatusWhenMcpDisabled() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(false);
        when(mcpClientService.isHealthy()).thenReturn(true);
        when(mcpClientService.getStatus()).thenReturn("MCP disabled - running in baseline mode");

        mockMvc.perform(get("/api/mcp/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.status").value("MCP disabled - running in baseline mode"))
                .andExpect(jsonPath("$.message").value("MCP is disabled"));
    }

    @Test
    void testGetStatusWhenMcpEnabled() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.isHealthy()).thenReturn(true);
        when(mcpClientService.getStatus()).thenReturn("MCP enabled - Type: STDIO, Timeout: 30s, Echo Server: connected (2 tools)");

        mockMvc.perform(get("/api/mcp/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.message").value("MCP is operational"));
    }

    @Test
    void testListToolsWhenMcpDisabled() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isEmpty())
                .andExpect(jsonPath("$.message").value("MCP is disabled - no tools available"));
    }

    @Test
    void testListToolsWhenMcpEnabled() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(true);
        
        List<McpTool> mockTools = List.of(
            McpTool.builder()
                .name("echo")
                .description("Echoes back the provided message")
                .inputSchema(Map.of("type", "object"))
                .build(),
            McpTool.builder()
                .name("ping")
                .description("Simple ping tool")
                .inputSchema(Map.of("type", "object"))
                .build()
        );
        
        when(mcpClientService.listTools()).thenReturn(mockTools);

        mockMvc.perform(get("/api/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isArray())
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.tools[0].name").value("echo"))
                .andExpect(jsonPath("$.tools[1].name").value("ping"));
    }

    @Test
    void testInvokeToolWhenMcpDisabled() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(false);

        Map<String, Object> arguments = Map.of("message", "test");

        mockMvc.perform(post("/api/mcp/tools/echo/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arguments)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("MCP is disabled"));
    }

    @Test
    void testInvokeToolSuccess() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(true);
        
        McpToolResult mockResult = McpToolResult.builder()
            .success(true)
            .content("Echo: Hello World")
            .metadata(Map.of("timestamp", 123456789L))
            .build();
            
        when(mcpClientService.invokeTool(eq("echo"), any())).thenReturn(mockResult);

        Map<String, Object> arguments = Map.of("message", "Hello World");

        mockMvc.perform(post("/api/mcp/tools/echo/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arguments)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content").value("Echo: Hello World"))
                .andExpect(jsonPath("$.tool").value("echo"))
                .andExpect(jsonPath("$.metadata.timestamp").value(123456789L));
    }

    @Test
    void testInvokeToolWithIllegalState() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.invokeTool(eq("echo"), any()))
            .thenThrow(new IllegalStateException("Echo server not connected"));

        Map<String, Object> arguments = Map.of("message", "test");

        mockMvc.perform(post("/api/mcp/tools/echo/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arguments)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Echo server not connected"));
    }

    @Test
    void testPingWhenMcpDisabled() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/mcp/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("MCP is disabled - ping not available"));
    }

    @Test
    void testPingSuccess() throws Exception {
        when(mcpClientService.isEnabled()).thenReturn(true);
        
        McpToolResult mockResult = McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(Map.of("timestamp", 123456789L))
            .build();
            
        when(mcpClientService.invokeTool(eq("ping"), any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/mcp/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("pong"))
                .andExpect(jsonPath("$.metadata.timestamp").value(123456789L));
    }
}