package com.khoutz.model

import com.khoutz.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.EntityChangeType
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.toEntity
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

abstract class BasePasswordTable(name: String) : UUIDTable(name) {
    val password = varchar("password", 60)
}

abstract class BasePasswordEntity(id: EntityID<UUID>, table: BasePasswordTable) : UUIDEntity(id) {
    var password by table.password
}

/**
 * Encodes a password before persisting to the db.
 */
abstract class PasswordEncodingEntityClass<E : BasePasswordEntity>(table: UUIDTable) : UUIDEntityClass<E>(table) {
    init {
        EntityHook.subscribe { action ->
            if (action.changeType == EntityChangeType.Created) {
                val entity = action.toEntity(this)
                entity?.password = BCrypt.hashpw(entity?.password, BCrypt.gensalt())
            }
        }
    }
}

object UserTable : BasePasswordTable("user") {
    val username = varchar("username", 128).uniqueIndex()
    val enabled = bool("enabled")
}

class User(id: EntityID<UUID>) : BasePasswordEntity(id, UserTable) {
    companion object : PasswordEncodingEntityClass<User>(UserTable)
    var username by UserTable.username
    var enabled by UserTable.enabled

    fun dto(): UserDTO {
        return UserDTO(
            id = id.value,
            username = username,
            password = password,
            enabled = enabled
        )
    }
}

@Serializable
data class UserDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String,
    val password: String,
    val enabled: Boolean
)

@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)
