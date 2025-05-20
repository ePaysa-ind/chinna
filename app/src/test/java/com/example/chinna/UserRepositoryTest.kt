package com.example.chinna

import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.local.database.UserEntity
import com.example.chinna.data.repository.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class UserRepositoryTest {
    
    @Mock
    private lateinit var userDao: UserDao
    
    @Mock
    private lateinit var prefsManager: PrefsManager
    
    private lateinit var userRepository: UserRepository
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        userRepository = UserRepository(userDao, prefsManager)
    }
    
    @Test
    fun `saveUser should save user with all fields including soilType`() = runTest {
        // Arrange
        val mobile = "9876543210"
        val name = "Test User"
        val village = "Test Village"
        val acreage = 5.0
        val crop = "Cotton"
        val sowingDate = 1234567890L
        val soilType = "Black"
        
        // Act
        userRepository.saveUser(
            mobile = mobile,
            name = name,
            village = village,
            acreage = acreage,
            crop = crop,
            sowingDate = sowingDate,
            soilType = soilType
        )
        
        // Assert
        verify(userDao).insertUser(argThat { user ->
            user.mobile == mobile &&
            user.name == name &&
            user.village == village &&
            user.acreage == acreage &&
            user.crop == crop &&
            user.sowingDate == sowingDate &&
            user.soilType == soilType
        })
        verify(prefsManager).saveUserLoggedIn(true)
        verify(prefsManager).saveUserMobile(mobile)
    }
    
    @Test
    fun `getCurrentUser should return user from dao`() = runTest {
        // Arrange
        val expectedUser = UserEntity(
            mobile = "1234567890",
            name = "John Doe",
            village = "Test Village",
            acreage = 3.5,
            crop = "Rice",
            sowingDate = 1234567890L,
            soilType = "Red"
        )
        `when`(userDao.getCurrentUserSync()).thenReturn(expectedUser)
        
        // Act
        val result = userRepository.getCurrentUserSync()
        
        // Assert
        assert(result == expectedUser)
        verify(userDao).getCurrentUserSync()
    }
    
    @Test
    fun `isLoggedIn should check prefsManager`() {
        // Arrange
        `when`(prefsManager.isUserLoggedIn()).thenReturn(true)
        
        // Act
        val result = userRepository.isLoggedIn()
        
        // Assert
        assert(result == true)
        verify(prefsManager).isUserLoggedIn()
    }
    
    @Test
    fun `logout should clear data`() = runTest {
        // Act
        userRepository.logout()
        
        // Assert
        verify(userDao).deleteAllUsers()
        verify(prefsManager).clearAll()
    }
}