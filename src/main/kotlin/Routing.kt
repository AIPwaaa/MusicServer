package com.example

import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val tracks = listOf(
        Track("1", "Song One", "Artist A", 210000, "https://cdn.muzyet.com/?h=JGraYpdVSCgaw6iDuk0QC8FFhNKhKX7AMb49L1UhoWgFONJRlhBUWcuprUN7UVT9yRtw3oxdkqYuzpAtfy_ik1pprkKdZXALHbmuj7pbZP3EPZrXpHiJuw\\\\", false),
        Track("2", "Song Two", "Artist B", 180000, "http://example.com/song2.mp3", false),
        Track("3", "CANCUN", "Playboi Carti", 240000, "http://10.0.2.2:8080/static/cancun.mp3", false)
    )

    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        staticResources("/static", null)
        get("/tracks") {
            call.respond(tracks)
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}