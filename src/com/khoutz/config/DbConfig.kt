package com.khoutz.config

import com.khoutz.model.DiaryEntryTable
import com.khoutz.model.User
import com.khoutz.model.UserTable
import com.khoutz.utils.randomPassword
import io.ktor.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.initDb() {
    val jdbc = environment.config.property("datasource.jdbc.url").getString()
    val driver = environment.config.property("datasource.jdbc.driver").getString()
    val dbUser = environment.config.propertyOrNull("datasource.user")?.getString().orEmpty()
    val dbPassword = environment.config.propertyOrNull("datasource.password")?.getString().orEmpty()

    val createUser = environment.config.property("diary.createUser").getString().toBoolean()

    Database.connect(jdbc, driver, dbUser, dbPassword)
    transaction {
        SchemaUtils.createMissingTablesAndColumns(UserTable, DiaryEntryTable)

        if (createUser) {
            val appUsername = environment.config.property("diary.user").getString()
            val defaultUser = User.find { UserTable.username eq appUsername }.singleOrNull()

            if (defaultUser == null) {
                val generatedPassword = randomPassword(32)
                println("generated password = $generatedPassword")
                User.new {
                    username = appUsername
                    enabled = true
                    password = generatedPassword
                }
            }
        }
    }
    log.info("Initialized Database")
}
