package com.gadware.driveauthorization.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun isUserRegistered(email: String): Boolean {
        return try {
            val snapshot = firestore.collection("users").document(email).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun registerUser(userProfile: UserProfile): Result<Unit> {
        return try {
            firestore.collection("users").document(userProfile.email).set(userProfile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
