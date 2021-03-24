package com.khoutz.module

import com.khoutz.Markdown
import com.khoutz.config.initDb
import com.khoutz.config.s3Bucket
import com.khoutz.config.s3Client
import com.khoutz.model.DiaryEntry
import com.khoutz.model.DiaryEntryTable
import com.khoutz.model.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.*

fun Application.diaryModule() {

    initDb()
    routing {
        authenticate("jwt") {
            diaryController(s3Client(), s3Bucket())
        }
    }
}

fun Application.diaryController(s3Client: S3Client, s3Bucket: String) {
    routing {
        route("/diary") {
            listEntries()
            createEntry(s3Client, s3Bucket)
            getEntry(s3Client, s3Bucket)
        }
    }
}

fun Route.getEntry(s3Client: S3Client, s3Bucket: String) {
    get("/{id}") {
        val diaryId = UUID.fromString(call.parameters["id"]) ?: throw MissingRequestParameterException("id")
        val userId = call.getAuthedUserID()
        val diaryEntry = transaction {
            DiaryEntry.find {
                DiaryEntryTable.id eq diaryId and (DiaryEntryTable.user eq userId)
            }.single().dto()
        }

        s3Client.getObject {
            it.bucket(s3Bucket)
                .key(diaryEntry.getKey())
        }.use { s3ResponseStream ->
            call.respondBytes(ContentType.Application.Markdown, HttpStatusCode.OK) {
                s3ResponseStream.readBytes()
            }
        }
    }
}

fun Route.createEntry(s3Client: S3Client, s3Bucket: String) {
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

        // TODO: Shouldn't require ContentLength header
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong()
            ?: throw MissingRequestParameterException(HttpHeaders.ContentLength)
        s3Client.putObject(
            {
                it.bucket(s3Bucket)
                it.key(entry.getKey())
                it.contentType(ContentType.Application.Markdown.toString())
            },
            RequestBody.fromInputStream(call.receiveStream(), contentLength)
        )
        call.respond(entry)
    }
}

fun Route.listEntries() {
    get("/list/{year}/{month?}/{day?}") {
        val year = call.parameters["year"] ?: throw MissingRequestParameterException("year")
        val month = call.parameters["month"]
        val day = call.parameters["day"]

        val startDate = LocalDate.of(year.toInt(), month?.toInt() ?: 1, day?.toInt() ?: 1).atStartOfDay(ZoneId.systemDefault())
        var endDate = LocalDate.of(year.toInt(), month?.toInt() ?: 12, day?.toInt() ?: 1).atStartOfDay(ZoneId.systemDefault())
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
