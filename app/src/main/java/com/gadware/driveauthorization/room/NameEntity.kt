package com.gadware.driveauthorization.room


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "names")
data class NameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)
