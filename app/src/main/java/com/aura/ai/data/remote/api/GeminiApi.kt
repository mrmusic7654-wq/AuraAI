package com.aura.ai.data.remote.api

import com.aura.ai.data.remote.dto.GeminiRequestDto
import com.aura.ai.data.remote.dto.GeminiResponseDto
import retrofit2.http.*

interface GeminiApi {
    
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequestDto,
        @Query("key") apiKey: String? = null
    ): GeminiResponseDto
    
    @POST("v1beta/models/{model}:streamGenerateContent")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Body request: GeminiRequestDto,
        @Query("key") apiKey: String? = null
    ): retrofit2.Response<GeminiResponseDto>
}
