package com.khoutz

import com.auth0.jwt.JWT
import com.khoutz.config.generateJwt
import com.khoutz.config.initDb
import com.khoutz.model.DiaryEntry
import com.khoutz.model.User
import com.khoutz.model.UserTable
import com.khoutz.module.defaultModule
import com.khoutz.module.diaryController
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.Application
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

const val TEST_USERNAME = "testUser"
const val BUCKET = "bucket"
val ENTRY_ID: UUID = UUID.randomUUID()

class ApplicationTest {

    private val s3Client = mockk<S3Client>()

    @Before
    fun setUp() {
        val inputStream = AbortableInputStream.create(this.javaClass.getResourceAsStream("/diary_payload.md"))
        every { s3Client.getObject(any() as Consumer<GetObjectRequest.Builder>) } returns ResponseInputStream(
            GetObjectResponse.builder().build(),
            inputStream
        )
    }

    @Test
    fun metrics() {
        withTestApplication({ defaultModule(true) }) {
            handleRequest(HttpMethod.Get, "/metrics").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `list diaries - yyyy`() {
        withTestApplication({ setupTest() }) {
            authRequest(HttpMethod.Get, "/diary/list/${Year.now()}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                response.content?.let {
                    assertContains(it, Year.now().toString())
                } ?: run {
                    fail("content is null")
                }
            }
        }
    }

    @Test
    fun `list diaries - yyyy-mm`() {
        withTestApplication({
            setupTest()
        }) {
            val now = LocalDate.now()
            authRequest(HttpMethod.Get, "/diary/list/${now.year}/${now.monthValue}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                response.content?.let {
                    assertContains(it, ENTRY_ID)
                } ?: run {
                    fail("null content")
                }
            }
        }
    }

    @Test
    fun `get diary entry`() {
        mockedTestApplication {
            val response = client.get("/diary/$ENTRY_ID")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(response.headers[HttpHeaders.ContentType], ContentType.Application.Markdown.toString())
            assertTrue(response.contentLength()!! > 0)
        }
    }

    @Test
    fun `create diary entry`() {
        val now = LocalDate.now()
        val diaryBytes = this.javaClass.getResourceAsStream("/diary_payload.md")?.readBytes() ?: fail("unable to load mocked payload")
        val queryParams = mapOf("title" to "test")
            .map { "${it.key.encodeURLParameter()}=${it.value.encodeURLParameter()}" }
            .joinToString(separator = "&")

        every {
            s3Client.putObject(any() as Consumer<PutObjectRequest.Builder>, any() as RequestBody)
        } returns PutObjectResponse.builder().build()

        withTestApplication({ setupTest() }) {
            authRequest(HttpMethod.Post, "/diary/${now.year}/${now.monthValue}/${now.dayOfMonth}?$queryParams") {
                addHeader(HttpHeaders.ContentLength, "${diaryBytes.size}")
                setBody(diaryBytes)
//                call.authentication.principal = authenticatedPrincipal()
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
            verify {
                s3Client.putObject(any() as Consumer<PutObjectRequest.Builder>, any() as RequestBody)
            }
        }
    }
}

fun mockedTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
    testApplication {
        environment {
            config = ApplicationConfig("test-application.conf")
        }
        application {
            initDb()
            val user = transaction {
                val testUser = User.find { UserTable.username eq TEST_USERNAME }.single()
                DiaryEntry.findById(ENTRY_ID) ?: run {
                    DiaryEntry.new(ENTRY_ID) {
                        title = "demo"
                        description = "test"
                        diaryDate = Instant.now()
                        user = testUser
                    }
                }
                testUser
            }
            // authentication {
            //     jwt {
            //         validate { credential ->
            //             credential.
            //         }
            //     }
            //     mock {
            //         principal {
            //             val jwtPrincipal = mockk<JWTPrincipal>()
            //             val payload = mockk<Payload>()
            //             every { payload.subject } returns user.id.value.toString()
            //             every { payload.audience } returns listOf(application.environment.config.property("jwt.audience").getString())
            //             every { jwtPrincipal.payload } returns payload
            //             jwtPrincipal
            //         }
            //     }
            // }
        }
        block()
    }
}

fun Application.setupTest() {
    defaultModule(testing = true)
    initDb()
    diaryController()

    transaction {
        DiaryEntry.findById(ENTRY_ID) ?: run {
            DiaryEntry.new(ENTRY_ID) {
                title = "demo"
                description = "test"
                diaryDate = Instant.now()
                user = User.find { UserTable.username eq TEST_USERNAME }.single()
            }
        }
    }
}

fun <T> assertContains(searchText: String, value: T, message: String? = null) where T : CharSequence {
    assertTrue(searchText.contains(value), message ?: "Expected to find $value, but not found in $searchText")
}

fun <T> assertContains(searchText: String, value: T, message: String? = null) {
    assertContains(searchText, value.toString(), message)
}

/**
 * Off the edge of the map by a bit. Handy dandy wrapper for the [handleRequest] method that adds an authentication
 * [Principal].
 */
fun TestApplicationEngine.authRequest(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    val authedSetup: TestApplicationRequest.() -> Unit = {
        val user = transaction {
            User.find { UserTable.username eq TEST_USERNAME }.single()
        }
        call.authentication.principal = JWTPrincipal(JWT.decode(call.application.generateJwt(user)))
        setup()
    }
    return handleRequest(method, uri, authedSetup)
}
