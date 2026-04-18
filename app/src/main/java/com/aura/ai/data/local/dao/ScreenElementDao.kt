package com.aura.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aura.ai.data.local.entities.ScreenElementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenElementDao {
    
    @Query("SELECT * FROM screen_elements_cache WHERE elementSignature = :signature")
    suspend fun getElement(signature: String): ScreenElementEntity?
    
    @Query("SELECT * FROM screen_elements_cache WHERE packageName = :packageName ORDER BY lastSeen DESC")
    fun getElementsForPackage(packageName: String): Flow<List<ScreenElementEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElement(element: ScreenElementEntity)
    
    @Query("DELETE FROM screen_elements_cache WHERE lastSeen < :beforeTimestamp")
    suspend fun deleteOldElements(beforeTimestamp: Long)
}
