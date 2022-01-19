package com.khoutz.model

import com.khoutz.serializer.InstantSerializer
import com.khoutz.serializer.UUIDSerializer
import com.khoutz.serializer.UserIdSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

object DiaryEntryTable : UUIDTable("diary_entry") {
    val title = text("title")
    val description = text("description").nullable()
    val diaryDate = timestamp("diary_date")
    val user = reference("user", UserTable)
}

class DiaryEntry(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DiaryEntry>(DiaryEntryTable)
    var title by DiaryEntryTable.title
    var description by DiaryEntryTable.description
    var diaryDate by DiaryEntryTable.diaryDate
    var user by User referencedOn DiaryEntryTable.user

    fun dto(): DiaryEntryDTO {
        return DiaryEntryDTO(
            id = id.value,
            title = title,
            description = description,
            diaryDate = diaryDate,
            user = user.dto()
        )
    }
}

@Serializable
data class DiaryEntryDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val title: String,
    val description: String?,
    @Serializable(with = InstantSerializer::class)
    val diaryDate: Instant,
    @Serializable(with = UserIdSerializer::class)
    val user: UserDTO
) {
    /**
     * Key used in the backing object storage service.
     */
    fun getKey(): String {
        return this.id.toString()
    }
}
