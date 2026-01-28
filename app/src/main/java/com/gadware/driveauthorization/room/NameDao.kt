package com.gadware.driveauthorization.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertName(name: NameEntity)

    @Query("SELECT * FROM names ORDER BY id DESC")
    fun getAllNames(): Flow<List<NameEntity>>
}
