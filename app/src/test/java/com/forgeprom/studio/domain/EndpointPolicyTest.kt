package com.forgeprom.studio.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointPolicyTest {

    @Test
    fun `accepts https endpoints`() {
        assertNull(EndpointPolicy.validateBaseUrl("https://gateway.example.com/v1"))
        assertNull(EndpointPolicy.validateBaseUrl("  https://api.openai.com/v1  "))
    }

    @Test
    fun `accepts http only for local development hosts`() {
        assertNull(EndpointPolicy.validateBaseUrl("http://localhost:8080/v1"))
        assertNull(EndpointPolicy.validateBaseUrl("http://127.0.0.1:8080/v1"))
        assertNull(EndpointPolicy.validateBaseUrl("http://10.0.2.2:8080/v1"))
        assertNotNull(EndpointPolicy.validateBaseUrl("http://gateway.example.com/v1"))
    }

    @Test
    fun `rejects missing scheme credentials queries and fragments`() {
        assertNotNull(EndpointPolicy.validateBaseUrl("gateway.example.com/v1"))
        assertNotNull(EndpointPolicy.validateBaseUrl("https://user:pass@gateway.example.com/v1"))
        assertNotNull(EndpointPolicy.validateBaseUrl("https://gateway.example.com/v1?key=secret"))
        assertNotNull(EndpointPolicy.validateBaseUrl("https://gateway.example.com/v1#frag"))
        assertNotNull(EndpointPolicy.validateBaseUrl(""))
        assertNotNull(EndpointPolicy.validateBaseUrl("https://gateway example.com/v1"))
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertNotNull(EndpointPolicy.validateBaseUrl("ftp://gateway.example.com/v1"))
        assertNotNull(EndpointPolicy.validateBaseUrl("file:///data/local/tmp"))
    }
}
