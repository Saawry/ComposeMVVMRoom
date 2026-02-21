package com.gadware.driveauthorization.data

data class UserProfile(
    val email: String = "",
    val name: String = "",
    val shopName: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val regDate: Long = 0L,
    val userType: String = "free",
    val status: String = "pending",
    val nextPayDate: Long = 0L
)
