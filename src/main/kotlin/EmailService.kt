package com.example

import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailService {
    // ТВОИ ДАННЫЕ GOOGLE
    private val fromEmail = "ilkapidrwithrealis@gmail.com"
    private val passwordApp = "oupc sqac siyz xzfq"

    // НАСТРОЙКИ ДЛЯ GOOGLE SMTP
    private val smtpHost = "smtp.gmail.com"
    private val smtpPort = "465"

    fun sendVerificationCode(toEmail: String, code: String) {
        val properties = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort)
            put("mail.smtp.auth", "true")
            put("mail.smtp.socketFactory.port", smtpPort)
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.ssl.protocols", "TLSv1.2")
        }

        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(fromEmail, passwordApp)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail))
                addRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                subject = "Код подтверждения регистрации"
                setText("Здравствуйте!\n\nВаш шестизначный код для подтверждения регистрации в приложении: $code\n\nЕсли вы не запрашивали этот код, просто проигнорируйте это письмо.")
            }
            Transport.send(message)
            println("=== Письмо успешно отправлено через Gmail на $toEmail ===")
        } catch (e: Exception) {
            e.printStackTrace()
            println("=== Ошибка отправки через Gmail: ${e.message} ===")
        }
    }
}