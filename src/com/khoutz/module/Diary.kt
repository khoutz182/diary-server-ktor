package com.khoutz.module

import com.khoutz.Markdown
import com.khoutz.config.initDb
import com.khoutz.model.DiaryEntry
import com.khoutz.model.DiaryEntryTable
import com.khoutz.model.User
import com.khoutz.storage.storage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.request.header
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

fun Application.diaryModule() {
    initDb()

    routing {
        authenticate("jwt") {
            diaryController()
        }
    }
}

fun Application.diaryController() {
    routing {
        route("/diary") {
            listEntries()
            createEntry()
            getEntry()
        }
    }
}

fun Route.getEntry() {
    get("/{id}") {
        val diaryId = UUID.fromString(call.parameters["id"]) ?: throw MissingRequestParameterException("id")
        val userId = call.getAuthedUserID()
        val diaryEntry = transaction {
            DiaryEntry.find {
                DiaryEntryTable.id eq diaryId and (DiaryEntryTable.user eq userId)
            }.single().dto()
        }
        application.storage().retrieve(diaryEntry.getKey()).use {
            call.respondBytes(ContentType.Application.Markdown, HttpStatusCode.OK) {
                it.readBytes()
            }
        }
    }
}

fun Route.createEntry() {
    post("/{year}/{month}/{day}") {
        val userUUID = call.getAuthedUserID() ?: return@post
        val year = call.parameters["year"] ?: throw MissingRequestParameterException("year")
        val month = call.parameters["month"] ?: throw MissingRequestParameterException("month")
        val day = call.parameters["day"] ?: throw MissingRequestParameterException("day")
        val pathDate = LocalDate.of(year.toInt(), month.toInt(), day.toInt())

        val entry = transaction {
            DiaryEntry.new {
                user = User[userUUID]
                diaryDate = pathDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                title = call.parameters["title"] ?: throw MissingRequestParameterException("title")
                description = call.parameters["description"]
            }.dto()
        }

        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong()
            ?: throw MissingRequestParameterException(HttpHeaders.ContentLength)
        application.storage().store(
            entry.getKey(),
            call.receiveStream(),
            contentLength
        )
        call.respond(entry)
    }
}

fun Route.listEntries() {
    get("/list/{year}/{month?}/{day?}") {
        val year = call.parameters["year"]?.toInt() ?: throw MissingRequestParameterException("year")
        val month = call.parameters["month"]?.toInt()
        val day = call.parameters["day"]

        val startDate = LocalDate.of(year, month ?: 1, day?.toInt() ?: 1).atStartOfDay(ZoneId.systemDefault())
        var endDate = LocalDate.of(year, month ?: 12, day?.toInt() ?: 1).atStartOfDay(ZoneId.systemDefault())
        if (day == null) {
            endDate = endDate.with(TemporalAdjusters.lastDayOfMonth())
        }

        val userId = call.getAuthedUserID() ?: return@get

        val diaryEntries = transaction {
            DiaryEntry.find {
                DiaryEntryTable.diaryDate.between(startDate.toInstant(), endDate.toInstant()) and (DiaryEntryTable.user eq userId)
            }.map {
                it.dto()
            }
        }
        call.respond(diaryEntries)
    }
}
