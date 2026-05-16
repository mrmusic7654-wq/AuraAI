package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class CreateRepoRequestDto(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    val auto_init: Boolean = true,
    val gitignore_template: String? = null,
    val license_template: String? = null
)
