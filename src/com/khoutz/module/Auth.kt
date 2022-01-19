package com.khoutz.module

import com.khoutz.config.generateJwt
import com.khoutz.config.jwtVerifier
import com.khoutz.model.AuthRequest
import com.khoutz.model.User
import com.khoutz.model.UserTable
import com.khoutz.utils.logger
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UnauthorizedResponse
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
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

fun Route.authenticate() {
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
suspend fun ApplicationCall.getAuthedUserID(): UUID? {
    return authentication.principal<JWTPrincipal>()?.let {
        UUID.fromString(it.payload.subject)
    } ?: run {
        respond(UnauthorizedResponse())
        return null
    }
}
