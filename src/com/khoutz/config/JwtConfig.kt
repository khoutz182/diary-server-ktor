package com.khoutz.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.khoutz.model.User
import io.ktor.server.application.Application
import java.util.Date

// https://medium.com/@er.imran4u/kotlin-ktor-with-jwt-authentication-ed78251629c2
// https://github.com/imran4u/ktor-jwt-example/blob/master/src/Application.kt
fun Application.jwtVerifier(): JWTVerifier {
    val issuer = environment.config.property("jwt.issuer").getString()
    val secret = environment.config.property("jwt.secret").getString()

    return JWT.require(algorithm(secret))
        .withIssuer(issuer)
        .build()
}

fun Application.generateJwt(user: User): String {
    val audience = environment.config.property("jwt.audience").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val secret = environment.config.property("jwt.secret").getString()
    val tokenLifeMinutes = environment.config.property("jwt.tokenLifeMinutes").getString().toInt()

    return JWT.create()
        .withSubject(user.id.value.toString())
        .withIssuer(issuer)
        .withClaim("name", user.username)
        .withIssuedAt(Date())
        .withAudience(audience)
        .withExpiresAt(getExpiration(tokenLifeMinutes))
        .sign(algorithm(secret))
}

private fun getExpiration(tokenLifeMinutes: Int): Date = Date(System.currentTimeMillis() + (tokenLifeMinutes * 1000 * 60))
private fun algorithm(secret: String): Algorithm = Algorithm.HMAC512(secret)
