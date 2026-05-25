package com.example

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val url: String,
    val isLocal: Boolean,
    val localUri: String? = null
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val status: String? = null
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class VerifyRequest(
    val email: String,
    val code: String
)

@Serializable
data class AuthErrorResponse(
    val errorCode: String,
    val message: String
)