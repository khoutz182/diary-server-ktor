package com.khoutz.module

import com.khoutz.config.generateJwt
import com.khoutz.config.jwtVerifier
import com.khoutz.model.AuthRequest
import com.khoutz.model.User
import com.khoutz.model.UserTable
import com.khoutz.utils.logger
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import software.amazon.awssdk.http.HttpStatusCode
import java.util.UUID

fun Application.authModule() {
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("jwt") {
            verifier(jwtVerifier())
            realm = jwtRealm
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    routing {
        route("/auth") {
            authenticate()
        }
    }
}

private fun Route.authenticate() {
    post {
        val requestUser = call.receive<AuthRequest>()
        val dbUser = transaction {
            User.find {
                UserTable.username eq requestUser.username
            }.first()
        }
        if (BCrypt.checkpw(requestUser.password, dbUser.password)) {
            val token = call.application.generateJwt(dbUser)
            call.respond(token)
            return@post
        }
        logger().info("Denied access for user $requestUser")
        call.respond(HttpStatusCode.FORBIDDEN)
    }
}

/**
 * Returns an authed users [UUID]. If the request is not authenticated, then the call responds with [UnauthorizedResponse]
 * and null is returned.
 */
suspend fun ApplicationCall.getAuthedUserID(): UUID {
    return authentication.principal<JWTPrincipal>()?.let {
        UUID.fromString(it.payload.subject)
    } ?: run {
        respond(UnauthorizedResponse())
        throw Exception("unauthorized")
    }
}
