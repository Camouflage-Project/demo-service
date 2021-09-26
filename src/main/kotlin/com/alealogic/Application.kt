package com.alealogic

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.alealogic.plugins.*

fun main() {
    embeddedServer(Netty, port = 8085, host = "0.0.0.0") {
        configureRouting()
        configureMonitoring()
        configureSerialization()
    }.start(wait = true)
}
