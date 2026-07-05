package com.yugidex.app.data

import retrofit2.http.*

interface YgoApi {
    @GET("cardsetsinfo.php") suspend fun bySetCode(@Query("setcode") code: String): SetCard
    @GET("cardinfo.php") suspend fun byId(@Query("id") id: Long): CardResponse
    @GET("cardinfo.php") suspend fun byName(@Query("name") name: String): CardResponse
    @GET("cardinfo.php") suspend fun fuzzy(@Query("fname") name: String): CardResponse
}

interface YugidexApi {
    @POST("api/auth/register") suspend fun register(@Body body: Credentials): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: Credentials): AuthResponse
    @POST("api/auth/logout") suspend fun logout(@Header("Authorization") bearer: String)
    @GET("api/cards") suspend fun inventory(@Header("Authorization") bearer: String): InventoryResponse
    @POST("api/cards/sync") suspend fun sync(@Header("Authorization") bearer: String, @Body body: SyncBody): SyncResponse
    @GET("api/card-details") suspend fun details(@Query("id") id: Long, @Query("name") name: String): Card
}
