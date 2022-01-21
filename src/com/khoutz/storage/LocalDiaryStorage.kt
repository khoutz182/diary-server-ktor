package com.khoutz.storage

import java.io.InputStream

class LocalDiaryStorage(private val baseDir: String) : DiaryStorage {
    override fun store(key: String, data: InputStream, dataLength: Long?) {
        TODO("Not yet implemented")
    }

    override fun retrieve(key: String): InputStream {
        TODO("Not yet implemented")
    }
}
