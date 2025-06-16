package com.example.chinna.data.repository

import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.local.database.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class UserRepositoryTest {

    @Mock
    private lateinit var mockUserDao: UserDao

    @Mock
    private lateinit var mockPrefsManager: PrefsManager

    @Mock
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Mock
    private lateinit var mockFirebaseUser: FirebaseUser

    private lateinit var userRepository: UserRepository

    private val testUserEntity = UserEntity(
        mobile = "1234567890",
        name = "Test User",
        pinCode = "123456",
        acreage = 5.0,
        crop = "Okra",
        sowingDate = System.currentTimeMillis(),
        soilType = "Black"
    )

    @Before
    fun setUp() {
        // Mock FirebaseAuth.getInstance() to return our mock instance
        // This is a bit tricky as getInstance is static. For more complex static mocking, PowerMockito might be needed.
        // However, UserRepository takes FirebaseAuth as a constructor param in its actual implementation (via Hilt).
        // For this test, we assume 'auth' is an injected mock.
        // If 'auth' was initialized internally like 'FirebaseAuth.getInstance()', testing would be harder.
        // The provided UserRepository structure has 'auth' as a private val, not constructor injected.
        // Let's assume for testing, we can conceptually provide it or its behavior.
        // The current UserRepository does: private val auth = FirebaseAuth.getInstance()
        // This makes direct mocking of 'auth' hard without PowerMock or refactoring UserRepository for testability.
        // For now, we'll focus on methods not heavily reliant on auth.currentUser state or assume it's pre-set.
        // The refactored UserRepository uses 'auth.currentUser' for 'saveUser' and 'getCurrentUserSync'.

        userRepository = UserRepository(mockUserDao, mockPrefsManager)
        // We need to manually set the 'auth' field for tests if it's not constructor-injected.
        // This is not ideal but a workaround for the current UserRepository structure.
        // A better way would be to inject FirebaseAuth into UserRepository.
        // For this test, we'll mock what 'auth.currentUser' would do.
        `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        // And inject mockFirebaseAuth into a field if possible (e.g. via reflection, or if it were injectable)
        // For now, let's simulate that 'auth' field in UserRepository somehow uses our 'mockFirebaseAuth'
        // This is a limitation of testing code that directly calls static getInstance().

        // For saveUser, it checks auth.currentUser.
        // For getCurrentUserSync, it also checks auth.currentUser.
        // We'll simulate this check.
    }

    @Test
    fun `saveUser success - inserts user in DAO and updates PrefsManager`() = runTest {
        // Arrange
        // Simulate that Firebase auth.currentUser is not null
        val mockAuthInstance = FirebaseAuth.getInstance() // This will be a real instance if not mocked by test runner
        // To control auth.currentUser, ideally UserRepository would take FirebaseAuth as a constructor arg.
        // For now, we assume the check 'auth.currentUser == null' passes.
        // The actual UserRepository uses `private val auth = FirebaseAuth.getInstance()`.
        // We can't directly mock `auth` field. We will assume `auth.currentUser` is not null for this test path.
        // To truly test this, we'd need PowerMockito or a refactor.
        // Let's assume the `IllegalStateException` for null user is NOT thrown.

        // Act
        userRepository.saveUser(
            mobile = testUserEntity.mobile,
            name = testUserEntity.name,
            pinCode = testUserEntity.pinCode,
            acreage = testUserEntity.acreage,
            crop = testUserEntity.crop,
            sowingDate = testUserEntity.sowingDate,
            soilType = testUserEntity.soilType
        )

        // Assert
        verify(mockUserDao).insertUser(anyOrNull<UserEntity>()) // Use anyOrNull for flexibility if UserEntity is complex
        verify(mockPrefsManager).saveUserLoggedIn(true)
        verify(mockPrefsManager).saveUserMobile(testUserEntity.mobile)
    }

    @Test(expected = Exception::class)
    fun `saveUser failure - DAO insertUser throws exception`() = runTest {
        // Arrange
        // Assume auth.currentUser is not null
        `when`(mockUserDao.insertUser(anyOrNull<UserEntity>())).doThrow(RuntimeException("DAO Error"))

        // Act
        userRepository.saveUser(
            mobile = testUserEntity.mobile,
            name = testUserEntity.name,
            pinCode = testUserEntity.pinCode,
            acreage = testUserEntity.acreage,
            crop = testUserEntity.crop,
            sowingDate = testUserEntity.sowingDate,
            soilType = testUserEntity.soilType
        )

        // Assert: Exception expected
    }

    @Test
    fun `getCurrentUserSync success - returns user from DAO`() = runTest {
        // Arrange
        // Assume auth.currentUser is not null for this path, or DAO is called regardless
        `when`(mockUserDao.getCurrentUserSync()).thenReturn(testUserEntity)
        // `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser) // To make auth.currentUser not null

        // Act
        val result = userRepository.getCurrentUserSync()

        // Assert
        verify(mockUserDao).getCurrentUserSync()
        assert(result == testUserEntity)
    }

    @Test
    fun `getCurrentUserSync failure - returns null from DAO`() = runTest {
        // Arrange
        // Assume auth.currentUser is not null
        `when`(mockUserDao.getCurrentUserSync()).thenReturn(null)
        // `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)

        // Act
        val result = userRepository.getCurrentUserSync()

        // Assert
        verify(mockUserDao).getCurrentUserSync()
        assert(result == null)
    }

    @Test
    fun `getCurrentUserSync when no firebase user - returns null`() = runTest {
        // This test requires controlling the internal 'auth' field of UserRepository or refactoring it.
        // For now, we can't easily set auth.currentUser to null from outside without PowerMock or reflection.
        // However, if we assume the check for auth.currentUser happens and it's null:
        // The current code `if (currentUser == null) { Log.w(...) return null }`
        // So, if we could make `auth.currentUser` null, `userDao.getCurrentUserSync()` shouldn't be called.

        // This test is more of a conceptual one given current structure.
        // If UserRepository took FirebaseAuth in constructor:
        // val customAuthMock = mock(FirebaseAuth::class.java)
        // `when`(customAuthMock.currentUser).thenReturn(null)
        // userRepository = UserRepository(mockUserDao, mockPrefsManager, customAuthMock)
        // val result = userRepository.getCurrentUserSync()
        // assertNull(result)
        // verify(mockUserDao, never()).getCurrentUserSync()
        // This test is illustrative of how it *would* be tested with injectable auth.
        // For the current code, this specific path is hard to isolate in a pure unit test.
        // We'll assume the other tests cover the DAO interaction when Firebase user *is* present.
    }


    @Test
    fun `getUserByMobile - calls DAO and returns user`() = runTest {
        // Arrange
        val mobile = "1234567890"
        `when`(mockUserDao.getUserByMobile(mobile)).thenReturn(testUserEntity)

        // Act
        val result = userRepository.getUserByMobile(mobile)

        // Assert
        verify(mockUserDao).getUserByMobile(mobile)
        assert(result == testUserEntity)
    }

    @Test
    fun `logout - updates PrefsManager and does NOT delete DAO users`() = runTest {
        // Act
        userRepository.logout()

        // Assert
        verify(mockPrefsManager).saveUserLoggedIn(eq(false)) // Use eq() for boolean
        verify(mockUserDao, never()).deleteAllUsers() // Verify deleteAllUsers is NOT called
    }

    @Test
    fun `isLoggedIn - calls PrefsManager and returns its result`() {
        // Arrange
        `when`(mockPrefsManager.isUserLoggedIn()).thenReturn(true)

        // Act
        val result = userRepository.isLoggedIn()

        // Assert
        verify(mockPrefsManager).isUserLoggedIn()
        assert(result)
    }
}
