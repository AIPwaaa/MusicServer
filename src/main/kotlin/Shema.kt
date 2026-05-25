package com.example // твой пакет

import org.jetbrains.exposed.sql.Table


object TracksTable : Table("tracks") {
    val id = varchar("id", 50)
    val title = varchar("title", 255)
    val artist = varchar("artist", 255)
    val duration = long("duration")
    val url = varchar("url", 500)
    val isLocal = bool("is_local")
    val localUri = varchar("local_uri", 500).nullable()

    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("users") {
    val id = varchar("id", 50)
    val username = varchar("username", 100).uniqueIndex()
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val status = varchar("status", 100).nullable()

    override val primaryKey = PrimaryKey(id)
}

object PendingRegistrationsTable : Table("pending_registrations") {
    val email = varchar("email", 150)
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val code = varchar("code", 6)

    override val primaryKey = PrimaryKey(email)
}