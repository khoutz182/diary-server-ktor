package com.khoutz.config

import io.ktor.server.application.Application
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

fun Application.s3Client(): S3Client {

    val region = environment.config.property("storage.s3.region").getString()
    val accessKey = environment.config.property("storage.s3.access.key").getString()
    val secretKey = environment.config.property("storage.s3.secret.key").getString()
    val endpointUrl = environment.config.propertyOrNull("storage.s3.endpoint.url")?.getString()

    val credentialsProvider = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey)
    )

    val s3Config = S3Configuration.builder()
        .checksumValidationEnabled(true)
        .build()
    val builder = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider)
        .serviceConfiguration(s3Config)

    endpointUrl?.let {
        builder.endpointOverride(URI.create(it))
    }
    return builder.build()
}

/**
 * S3 Bucket the application is configured to use
 */
fun Application.s3Bucket(): String {
    return environment.config.property("s3.bucket").getString()
}
