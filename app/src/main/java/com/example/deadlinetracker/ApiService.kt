package com.example.deadlinetracker.network

import com.example.deadlinetracker.model.Quote
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface untuk Retrofit API Service
 * Menggunakan Quotable API untuk mendapatkan quotes motivasi
 */
interface ApiService {

    /**
     * Get random quote
     * Endpoint: https://api.quotable.io/random
     */
    @GET("random")
    suspend fun getRandomQuote(): Response<Quote>

    /**
     * Get random quote dengan tags tertentu
     * @param tags comma-separated tags (e.g., "inspirational,motivational")
     */
    @GET("random")
    suspend fun getRandomQuoteByTags(
        @Query("tags") tags: String
    ): Response<Quote>

    /**
     * Get quote by max length
     * @param maxLength maximum character length
     */
    @GET("random")
    suspend fun getRandomQuoteByLength(
        @Query("maxLength") maxLength: Int
    ): Response<Quote>
}