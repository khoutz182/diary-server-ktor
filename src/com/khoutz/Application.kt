package com.khoutz

import io.ktor.http.ContentType

// Misc Constants
val ContentType.Application.Markdown: ContentType
    get() = ContentType("text", "markdown")

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)
