package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "github_repos")
data class GitHubRepoEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val htmlUrl: String,
    val cloneUrl: String,
    val defaultBranch: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String,
    val lastSynced: Long = System.currentTimeMillis()
)
