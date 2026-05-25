package com.example

import com.example.PendingRegistrationsTable.email
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.mindrot.jbcrypt.BCrypt
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
            // 1. ПРОВЕРКА АВТОРИЗАЦИИ: Получаем ID пользователя из заголовков запроса
            val userId = call.request.headers["X-User-Id"]
            if (userId.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("AUTH_REQUIRED", "Вы должны быть авторизованы"))
                return@post
            }

            // 2. ПРОВЕРКА ЛИМИТА ЗАГРУЗОК (67 треков в неделю)
            val userRow = dbQuery {
                UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()
            }
            if (userRow == null) {
                call.respond(HttpStatusCode.BadRequest, AuthErrorResponse("USER_NOT_FOUND", "Пользователь не найден"))
                return@post
            }

            val currentStatus = userRow[UsersTable.status] ?: "0:${LocalDate.now()}"

            // Парсим статус вида "кол-во:дата" (если там просто "VERIFIED", сбросит в дефолт "0:дата")
            var uploadCount = currentStatus.substringBefore(":", "0").toIntOrNull() ?: 0
            val lastUploadDateStr = currentStatus.substringAfter(":", "")
            val lastUploadDate = try { LocalDate.parse(lastUploadDateStr) } catch (e: Exception) { LocalDate.now() }

            val today = LocalDate.now()
            val daysPassed = ChronoUnit.DAYS.between(lastUploadDate, today)

            // Если прошла неделя (7 дней или больше), сбрасываем счетчик загрузок
            if (daysPassed >= 7) {
                uploadCount = 0
            }

            // Проверяем, не превышен ли лимит в 67 треков
            if (uploadCount >= 67) {
                call.respond(HttpStatusCode.Forbidden, AuthErrorResponse("LIMIT_EXCEEDED", "Превышен лимит загрузок: максимум 67 треков в неделю"))
                return@post
            }

            val multipart = call.receiveMultipart()
            var track: Track? = null
            var isDuplicate = false

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName ?: "track_${UUID.randomUUID()}.mp3"
                    val file = File("src/main/resources/$fileName")

                    // 3. ПРОВЕРКА НА ДУБЛИКАТ: Если файл с таким именем уже физически есть на сервере
                    if (file.exists()) {
                        isDuplicate = true
                        part.dispose()
                        return@forEachPart
                    }

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

                    // Записываем трек в БД
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

            // Если вышли из цикла и поймали дубликат
            if (isDuplicate) {
                call.respond(HttpStatusCode.Conflict, AuthErrorResponse("DUPLICATE_FILE", "Такой трек уже есть на сервере"))
                return@post
            }

            if (track != null) {
                // 4. УВЕЛИЧИВАЕМ СЧЕТЧИК ЗАГРУЗОК И ОБНОВЛЯЕМ СТАТУС В БД
                val newCount = uploadCount + 1
                val newStatus = "$newCount:$today"

                dbQuery {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[status] = newStatus
                    }
                }

                call.respond(HttpStatusCode.Created, track!!)
            } else {
                call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }

        post("/auth/register") {
            val request = call.receive<RegisterRequest>()

            val isUsernameTaken = dbQuery {
                UsersTable.selectAll().where { UsersTable.username eq request.username }.count() > 0
            }
            if (isUsernameTaken) {
                call.respond(HttpStatusCode.Conflict, AuthErrorResponse("USERNAME_TAKEN", "Этот никнейм уже занят"))
                return@post
            }

            val isEmailTaken = dbQuery {
                UsersTable.selectAll().where { UsersTable.email eq request.email }.count() > 0
            }
            if (isEmailTaken) {
                call.respond(HttpStatusCode.Conflict, AuthErrorResponse("EMAIL_TAKEN", "Эта почта уже зарегистрирована"))
                return@post
            }

            val hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt())

            // Сохраняем черновик во временную таблицу. Код пока ставим пустой "000000"
            dbQuery {
                PendingRegistrationsTable.upsert {
                    it[email] = request.email
                    it[username] = request.username
                    it[passwordHash] = hashedPassword
                    it[code] = "000000"
                }
            }

            call.respond(HttpStatusCode.Created, mapOf("message" to "Данные приняты. Запросите код."))
        }


        // 2. ОТПРАВКА / ПОВТОРНАЯ ОТПРАВКА КОДА (Вот тут реальная отправка!)
        // На вход ждет JSON: { "email": "user@mail.com" }
        post("/auth/send-code") {
            val request = call.receive<Map<String, String>>()
            val emailParam = request["email"] ?: ""

            // Проверяем, есть ли вообще черновик для этой почты
            val pendingRow = dbQuery {
                PendingRegistrationsTable.selectAll().where { PendingRegistrationsTable.email eq emailParam }.singleOrNull()
            }

            if (pendingRow == null) {
                call.respond(HttpStatusCode.BadRequest, AuthErrorResponse("REGISTRATION_NOT_FOUND", "Сначала пройдите шаг регистрации"))
                return@post
            }

            // Генерируем новый код
            val newCode = (100000..999999).random().toString()

            // Обновляем код во временной таблице
            dbQuery {
                PendingRegistrationsTable.update({ PendingRegistrationsTable.email eq emailParam }) {
                    it[code] = newCode
                }
            }

            // ЗАПУСКАЕМ НАСТОЯЩУЮ ОТПРАВКУ ПОЧТЫ
            // Используем Thread, чтобы сервер не зависал, пока отправляется письмо
            Thread {
                EmailService.sendVerificationCode(emailParam, newCode)
            }.start()

            call.respond(HttpStatusCode.OK, mapOf("message" to "Код успешно отправлен на почту"))
        }


        // 3. ПРОВЕРКА КОДА (Перенос в основную таблицу)
        post("/auth/verify") {
            val request = call.receive<VerifyRequest>()

            val pendingRow = dbQuery {
                PendingRegistrationsTable.selectAll().where { PendingRegistrationsTable.email eq request.email }.singleOrNull()
            }

            if (pendingRow == null) {
                call.respond(HttpStatusCode.BadRequest, AuthErrorResponse("USER_NOT_FOUND", "Запрос на регистрацию не найден"))
                return@post
            }

            if (pendingRow[PendingRegistrationsTable.code] == request.code) {
                dbQuery {
                    UsersTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[username] = pendingRow[PendingRegistrationsTable.username]
                        it[email] = pendingRow[PendingRegistrationsTable.email]
                        it[passwordHash] = pendingRow[PendingRegistrationsTable.passwordHash]
                        it[status] = "VERIFIED"
                    }
                    PendingRegistrationsTable.deleteWhere { email eq request.email }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "Аккаунт успешно создан и подтвержден"))
            } else {
                call.respond(HttpStatusCode.BadRequest, AuthErrorResponse("INVALID_CODE", "Неверный код подтверждения"))
            }
        }

        // 4. АВТОРИЗАЦИЯ
        post("/auth/login") {
            val request = call.receive<LoginRequest>()

            val userRow = dbQuery {
                UsersTable.selectAll().where {
                    (UsersTable.username eq request.login) or (UsersTable.email eq request.login)
                }.singleOrNull()
            }

            if (userRow == null) {
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("INVALID_CREDENTIALS", "Неверный логин или пароль"))
                return@post
            }

            val dbPasswordHash = userRow[UsersTable.passwordHash]
            if (BCrypt.checkpw(request.password, dbPasswordHash)) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "id" to userRow[UsersTable.id],
                    "username" to userRow[UsersTable.username],
                    "email" to userRow[UsersTable.email]
                ))
            } else {
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("INVALID_CREDENTIALS", "Неверный логин или пароль"))
            }
        }
    }
}