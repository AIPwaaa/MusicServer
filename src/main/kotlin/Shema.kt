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