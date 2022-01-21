package com.khoutz

import com.auth0.jwt.interfaces.Payload
import com.khoutz.model.User
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationPipeline
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.Principal
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.response.respond
import io.mockk.every
import io.mockk.mockk

fun Authentication.Configuration.mock(
    name: String? = null,
    configure: MockAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = MockAuthenticationProvider.Configuration(name).apply(configure).build()
    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val principal = provider.principalProvider(call)

        if (principal == null) {
            // completing context.challenge is necessary,
            // without that authentication will not fail
            // even when the principal is null
            call.respond(UnauthorizedResponse())
            context.challenge.complete()
            return@intercept
        }

        context.principal(principal)
    }
    register(provider)
}

class MockAuthenticationProvider(config: Configuration) : AuthenticationProvider(config) {
    val principalProvider = config.principalProvider

    class Configuration(name: String?) : AuthenticationProvider.Configuration(name) {
        var principalProvider: ApplicationCall.() -> Principal? = { null }

        fun build() = MockAuthenticationProvider(this)

        /**
         * if principalProvider returns null,
         * authentication will fail(401 response will be returned)
         * */
        fun principal(principalProvider: ApplicationCall.() -> Principal?) {
            this.principalProvider = principalProvider
        }
    }
}

fun Application.testAuth(loginUser: User? = null) {
    install(Authentication) {
        mock {
            principal {
                val jwtPrincipal = mockk<JWTPrincipal>()
                val payload = mockk<Payload>()
                every { payload.subject } returns loginUser?.id?.value.toString()
                every { payload.audience } returns listOf(application.environment.config.property("jwt.audience").getString())
                every { jwtPrincipal.payload } returns payload
                jwtPrincipal
            }
        }
    }
}
