package com.example.chinna.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    
    sealed class AuthState {
        object Loading : AuthState()
        object Success : AuthState()
        data class Error(val message: String) : AuthState()
    }
    
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState
    
    fun saveUser(userData: UserData) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                userRepository.saveUser(
                    mobile = userData.mobile,
                    name = userData.name,
                    pinCode = userData.pinCode,
                    acreage = userData.acreage,
                    crop = userData.crop,
                    sowingDate = userData.sowingDate,
                    soilType = userData.soilType
                )
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to save user data")
            }
        }
    }
}