package com.example

import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        staticResources("/static", null)
        get("/tracks") {
            val tracks = dbQuery {
                TracksTable.selectAll().map { row ->
                    Track(
                        id = row[TracksTable.id],
                        title = row[TracksTable.title],
                        artist = row[TracksTable.artist],
                        duration = row[TracksTable.duration],
                        url = row[TracksTable.url],
                        isLocal = row[TracksTable.isLocal],
                        localUri = row[TracksTable.localUri]
                    )
                }
            }
            call.respond(tracks)
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}