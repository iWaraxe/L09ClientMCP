package com.coherentsolutions.l09clientmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.ai.mcp.client.enabled=false"
})
class L09ClientMcpApplicationTests {

    @Test
    void contextLoads() {
    }

}
