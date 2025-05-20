package com.example.chinna.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthSettings
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor() {
    
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "AuthService"
    
    // Keep track of resending token
    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null
    
    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class CodeSent(val verificationId: String) : AuthState()
        data class Success(val userId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }
    
    /**
     * Configure Firebase Auth settings for the app - called from Application class
     */
    fun configureFirebaseAuth(context: Context, isAdmin: Boolean = false) {
        // Get the Firebase Auth settings
        val firebaseAuthSettings = auth.firebaseAuthSettings
        
        // For admin users in debug builds, we can optionally disable app verification
        // to provide a consistent testing experience
        if (isAdmin && context.packageName.contains(".debug")) {
            // Set a test phone number and verification code for testing
            val testPhoneNumber = "+919876543210"
            val testVerificationCode = "123456"
            firebaseAuthSettings.setAutoRetrievedSmsCodeForPhoneNumber(testPhoneNumber, testVerificationCode)
        } else {
            // For production, ensure verification is properly enabled
            firebaseAuthSettings.setAppVerificationDisabledForTesting(false)
        }
    }
    
    fun sendOtp(phoneNumber: String, activity: Activity): Flow<AuthState> = callbackFlow {
        trySend(AuthState.Loading)
        
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted: Auto verification completed")
                // Auto-retrieval or instant verification
                signInWithCredential(credential) { result ->
                    when (result) {
                        is AuthState.Success -> trySend(result)
                        is AuthState.Error -> trySend(result)
                        else -> {}
                    }
                }
            }
            
            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "onVerificationFailed: ${e.message}")
                when (e) {
                    is FirebaseTooManyRequestsException -> {
                        trySend(AuthState.Error("Too many requests. Please try again later."))
                    }
                    else -> {
                        trySend(AuthState.Error(e.message ?: "Verification failed"))
                    }
                }
            }
            
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent: Code sent successfully")
                // Save the token for later use
                resendingToken = token
                trySend(AuthState.CodeSent(verificationId))
            }
        }
        
        // Check if we have a resending token from a previous attempt
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        
        // If we have a token, use it to resend the code
        resendingToken?.let {
            optionsBuilder.setForceResendingToken(it)
        }
        
        try {
            PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "verifyPhoneNumber error: ${e.message}")
            trySend(AuthState.Error("Failed to send verification code: ${e.message}"))
        }
        
        awaitClose()
    }
    
    fun verifyOtp(verificationId: String, otp: String): Flow<AuthState> = callbackFlow {
        trySend(AuthState.Loading)
        
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            signInWithCredential(credential) { result ->
                trySend(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp error: ${e.message}")
            trySend(AuthState.Error("Invalid verification code"))
        }
        
        awaitClose()
    }
    
    private fun signInWithCredential(
        credential: PhoneAuthCredential,
        callback: (AuthState) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        Log.d(TAG, "signInWithCredential:success userId=${user.uid}")
                        callback(AuthState.Success(user.uid))
                    } else {
                        Log.w(TAG, "signInWithCredential:success but no user")
                        callback(AuthState.Error("Authentication failed"))
                    }
                } else {
                    val error = task.exception
                    Log.w(TAG, "signInWithCredential:failure", error)
                    when (error) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            callback(AuthState.Error("Invalid verification code"))
                        }
                        else -> {
                            callback(AuthState.Error(error?.message ?: "Authentication failed"))
                        }
                    }
                }
            }
    }
    
    /**
     * Check if current user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Get current user
     */
    fun getCurrentUser() = auth.currentUser
    
    /**
     * Sign out user
     */
    fun signOut() {
        // Reset resending token when signing out
        resendingToken = null
        auth.signOut()
    }
}