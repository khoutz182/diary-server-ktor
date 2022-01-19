package com.khoutz.utils

/**
 * Generate a random string to be used for passwords.
 *
 * TODO: make it do more than just alpha's
 */
fun randomPassword(length: Int): String {
    val charPool = 'a'..'z'
    return (1..length)
        .map { charPool.random() }
        .joinToString(separator = "")
}
