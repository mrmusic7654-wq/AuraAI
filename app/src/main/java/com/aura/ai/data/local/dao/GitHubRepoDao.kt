package com.aura.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aura.ai.data.local.entities.GitHubRepoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubRepoDao {
    
    @Query("SELECT * FROM github_repos ORDER BY name ASC")
    fun getAllRepos(): Flow<List<GitHubRepoEntity>>
    
    @Query("SELECT * FROM github_repos WHERE fullName = :fullName")
    suspend fun getRepoByFullName(fullName: String): GitHubRepoEntity?
    
    @Query("SELECT * FROM github_repos WHERE fullName = :fullName")
    fun observeRepoByFullName(fullName: String): Flow<GitHubRepoEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepo(repo: GitHubRepoEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(repos: List<GitHubRepoEntity>)
    
    @Query("DELETE FROM github_repos WHERE id = :repoId")
    suspend fun deleteRepo(repoId: Long)
    
    @Query("DELETE FROM github_repos")
    suspend fun clearAllRepos()
}
