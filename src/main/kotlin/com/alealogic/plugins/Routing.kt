package com.alealogic.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager

fun Application.configureRouting() {
    install(AutoHeadResponse)

    val config: Config = ConfigFactory.load()
    val proxyUrl: String = config.getString("proxyUrl")
    val targetUrl: String = config.getString("targetUrl")
    val numberOfCalls: Int = config.getInt("numberOfCalls")
    val destinationServerDelayMs: Long = config.getLong("destinationServerDelayMs")

    val directClient = HttpClient(CIO) {
        engine { httpsConfig() }
    }

    val proxyClient = HttpClient(CIO) {
        engine {
            httpsConfig()
            proxy = ProxyBuilder.http(proxyUrl)
        }
    }

    routing {
        get("/proxied") {
            timedAggregateResponse(call, numberOfCalls) {
                proxyClient.request(targetUrl) {
                    header("Singleproxy-API-key", config.getString("singleProxyApiKey"))
                }
            }
        }

        get("/direct") {
            timedAggregateResponse(call, numberOfCalls) { directClient.request(targetUrl) }
        }

        //mock destination server
        get("/destination-server") {
            delay(destinationServerDelayMs)
            call.respondText("${UUID.randomUUID()} from ${call.request.origin.remoteHost}")
        }
    }
}

suspend fun timedAggregateResponse(call: ApplicationCall, numberOfCalls: Int, requestBlock: suspend () -> String) =
    coroutineScope {
        val start = System.currentTimeMillis()

        val deferredResponses: List<Deferred<String>> = (0 until numberOfCalls).map { async { requestBlock() } }
        val responses = deferredResponses.awaitAll()

        val end = System.currentTimeMillis()

        call.respondText { "durationInMillis=${end - start}\n\n${responses.joinToString("\n")}" }
    }

val httpsConfig: CIOEngineConfig.() -> Unit = {
    https {
        trustManager = object : X509TrustManager {
            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

            override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
        }
    }
}
