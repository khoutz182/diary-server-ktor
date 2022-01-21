package com.khoutz.storage

import com.khoutz.config.s3Bucket
import com.khoutz.config.s3Client
import io.ktor.server.application.Application
import java.io.InputStream

interface DiaryStorage {

    fun store(key: String, data: InputStream, dataLength: Long? = null)

    fun retrieve(key: String): InputStream
}

class DiaryStorageImpl(private val diaryStorages: List<DiaryStorage>) : DiaryStorage {
    override fun store(key: String, data: InputStream, dataLength: Long?) {
        diaryStorages.forEach {
            it.store(key, data, dataLength)
        }
    }

    override fun retrieve(key: String): InputStream {
        val storage = diaryStorages.firstOrNull { it is LocalDiaryStorage } ?: diaryStorages.first()
        return storage.retrieve(key)
    }
}

fun Application.storage(): DiaryStorage {
    val storages = mutableListOf<DiaryStorage>()
    if (environment.config.keys().contains("storage.s3.endpoint.url")) {
        storages.add(S3DiaryStorage(s3Client(), s3Bucket()))
    }

    if (environment.config.keys().contains("storage.local.basePath")) {
        storages.add(LocalDiaryStorage(environment.config.property("storage.local.basePath").getString()))
    }
    return DiaryStorageImpl(diaryStorages = storages.toList())
}
