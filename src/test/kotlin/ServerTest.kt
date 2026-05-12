package com.example

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun `test tracks endpoint`() = testApplication {
        val response = client.get("/tracks")
        assertEquals(HttpStatusCode.OK, response.status)
    }

}
