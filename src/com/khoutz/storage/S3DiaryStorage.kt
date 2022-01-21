package com.khoutz.storage

import com.khoutz.Markdown
import io.ktor.http.ContentType
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import java.io.InputStream

class S3DiaryStorage(private val s3Client: S3Client, private val s3Bucket: String) : DiaryStorage {
    override fun store(key: String, data: InputStream, dataLength: Long?) {
        s3Client.putObject(
            {
                it.bucket(s3Bucket)
                it.key(key)
                it.contentType(ContentType.Application.Markdown.toString())
            },
            RequestBody.fromInputStream(data, dataLength ?: throw Exception("Content length required by s3 storage backend"))
        )
    }

    override fun retrieve(key: String): InputStream {
        return s3Client.getObject {
            it.bucket(s3Bucket)
                .key(key)
        }
    }
}
