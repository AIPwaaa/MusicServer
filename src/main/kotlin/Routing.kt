package com.example

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import com.mpatric.mp3agic.Mp3File
import java.io.File
import java.util.UUID

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
        post("/upload") {
            val multipart = call.receiveMultipart()
            var track: Track? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName ?: "track_${UUID.randomUUID()}.mp3"
                    val file = File("src/main/resources/$fileName")
                    
                    // Create directory if it doesn't exist
                    file.parentFile.mkdirs()
                    
                    part.streamProvider().use { input ->
                        file.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val mp3file = Mp3File(file.absolutePath)
                    val id = UUID.randomUUID().toString()
                    
                    val title = if (mp3file.hasId3v2Tag()) {
                        mp3file.id3v2Tag.title ?: fileName
                    } else if (mp3file.hasId3v1Tag()) {
                        mp3file.id3v1Tag.title ?: fileName
                    } else {
                        fileName
                    }

                    val artist = if (mp3file.hasId3v2Tag()) {
                        mp3file.id3v2Tag.artist ?: "Unknown"
                    } else if (mp3file.hasId3v1Tag()) {
                        mp3file.id3v1Tag.artist ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    val duration = mp3file.lengthInMilliseconds
                    val url = "http://10.0.2.2:8080/static/$fileName"

                    track = Track(
                        id = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        url = url,
                        isLocal = true,
                        localUri = file.absolutePath
                    )

                    dbQuery {
                        TracksTable.insert {
                            it[TracksTable.id] = id
                            it[TracksTable.title] = title
                            it[TracksTable.artist] = artist
                            it[TracksTable.duration] = duration
                            it[TracksTable.url] = url
                            it[TracksTable.isLocal] = true
                            it[TracksTable.localUri] = null
                        }
                    }
                }
                part.dispose()
            }

            if (track != null) {
                call.respond(HttpStatusCode.Created, track!!)
            } else {
                call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}