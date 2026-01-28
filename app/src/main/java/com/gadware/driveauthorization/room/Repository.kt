package com.gadware.driveauthorization.room

import kotlinx.coroutines.flow.Flow

class NameRepository(private val dao: NameDao) {

    val names: Flow<List<NameEntity>> = dao.getAllNames()

    suspend fun addName(name: String) {
        dao.insertName(NameEntity(name = name))
    }
}
