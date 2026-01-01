package com.example.deadlinetracker.model

/**
 * Model untuk Quote dari API
 * API: https://api.quotable.io/random
 */
data class Quote(
    val _id: String,
    val content: String,
    val author: String,
    val tags: List<String>,
    val authorSlug: String,
    val length: Int,
    val dateAdded: String,
    val dateModified: String
)

/**
 * Response wrapper untuk handling API response
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)