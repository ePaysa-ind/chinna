package com.example.chinna.data.repository

import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.local.database.UserEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val prefsManager: PrefsManager
) {
    
    suspend fun saveUser(
        mobile: String,
        name: String,
        village: String,
        acreage: Double,
        crop: String,
        sowingDate: Long,
        soilType: String
    ) {
        val user = UserEntity(
            mobile = mobile,
            name = name,
            village = village,
            acreage = acreage,
            crop = crop,
            sowingDate = sowingDate,
            soilType = soilType
        )
        
        userDao.insertUser(user)
        prefsManager.saveUserLoggedIn(true)
        prefsManager.saveUserMobile(mobile)
    }
    
    fun getCurrentUser(): Flow<UserEntity?> = userDao.getCurrentUser()
    
    suspend fun getCurrentUserSync(): UserEntity? = userDao.getCurrentUserSync()
    
    suspend fun getUserByMobile(mobile: String): UserEntity? = userDao.getUserByMobile(mobile)
    
    suspend fun logout() {
        userDao.deleteAllUsers()
        prefsManager.clearAll()
    }
    
    fun isLoggedIn(): Boolean = prefsManager.isUserLoggedIn()
}