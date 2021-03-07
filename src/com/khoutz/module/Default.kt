package com.khoutz.module

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.metrics.micrometer.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json

/**
 * Contains standard features to be used everywhere or be made globally, outside of any application logic.
 */
@JvmOverloads
fun Application.defaultModule(testing: Boolean = false) {

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            ProcessorMetrics()
        )
        routing {
            get("/metrics") {
                call.respond(appMicrometerRegistry.scrape())
            }
        }
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

    install(ContentNegotiation) {
        json(Json {
            if (testing) {
                prettyPrint = true
            }
        })
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            log.error("Error: ", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
