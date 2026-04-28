package com.aquib.androidperflab.data

interface Repository<T> {
    suspend fun getAll(): List<T>
    suspend fun getById(id: String): T?
}
