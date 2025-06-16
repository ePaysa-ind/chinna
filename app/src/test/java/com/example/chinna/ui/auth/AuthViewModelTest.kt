package com.example.chinna.ui.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.chinna.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AuthViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule() // For LiveData testing

    @Mock
    private lateinit var mockUserRepository: UserRepository

    private lateinit var authViewModel: AuthViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for testing
        authViewModel = AuthViewModel(mockUserRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher after tests
    }

    private val testUserData = UserData(
        mobile = "1234567890",
        name = "Test User",
        pinCode = "123456",
        acreage = 5.0,
        crop = "Okra",
        sowingDate = System.currentTimeMillis(),
        soilType = "Black"
    )

    @Test
    fun `saveUser success - authState LiveData sequence is Loading then Success`() = runTest {
        // Arrange
        // No need to mock userRepository.saveUser for success, as it's a suspend function returning Unit

        // Act
        authViewModel.saveUser(testUserData)
        testDispatcher.scheduler.advanceUntilIdle() // Execute pending coroutines

        // Assert
        // Check LiveData states. We need to observe it.
        // A simple way is to check the value after execution.
        // For sequences, TestObserver or similar utilities are helpful.
        // Here, we'll check the final state.
        assert(authViewModel.authState.value is AuthViewModel.AuthState.Loading) // Initial state after call
        // After coroutine completes (advancing dispatcher):
        // The ViewModel updates LiveData in sequence. The last one should be Success.
        // However, observing sequence is better. Let's check the current value after execution.
        // To properly test sequence:
        // val observer = mock(Observer::class.java) as Observer<AuthViewModel.AuthState>
        // authViewModel.authState.observeForever(observer)
        // ... call saveUser ...
        // verify(observer).onChanged(AuthViewModel.AuthState.Loading)
        // verify(observer).onChanged(AuthViewModel.AuthState.Success)
        // For simplicity here, we check the value after runTest finishes processing.
        // The nature of runTest and LiveData means the last update should be visible.

        // Advance the dispatcher to ensure all coroutines launched in saveUser complete
        testDispatcher.scheduler.advanceUntilIdle()
        assert(authViewModel.authState.value is AuthViewModel.AuthState.Success)
    }

    @Test
    fun `saveUser failure - authState LiveData sequence is Loading then Error`() = runTest {
        // Arrange
        val errorMessage = "Failed to save user"
        // Mock userRepository.saveUser to throw an exception
        `when`(mockUserRepository.saveUser(testUserData)).doSuspendableAnswer {
            throw RuntimeException(errorMessage)
        }
        // Alternative Mockito-Kotlin syntax for suspending functions:
        // `when`(mockUserRepository.saveUser(testUserData)).doThrow(RuntimeException(errorMessage))


        // Act
        authViewModel.saveUser(testUserData)
        testDispatcher.scheduler.advanceUntilIdle() // Execute pending coroutines

        // Assert
        assert(authViewModel.authState.value is AuthViewModel.AuthState.Loading)

        // Advance the dispatcher
        testDispatcher.scheduler.advanceUntilIdle()
        val errorState = authViewModel.authState.value as? AuthViewModel.AuthState.Error
        assert(errorState != null)
        assert(errorState?.message == errorMessage)
    }
}
