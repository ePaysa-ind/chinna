package com.example.chinna.ui.auth

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.chinna.R
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.By


// It's good practice to define test constants
object TestAuthCredentials {
    const val TEST_MOBILE_NEW_USER = "6505551111" // Use a number not already in Firebase for new user
    const val TEST_MOBILE_EXISTING_USER = "6505553434" // Pre-configured test number in Firebase
    const val TEST_OTP = "123456" // Pre-configured OTP for TEST_MOBILE_EXISTING_USER
    const val INVALID_OTP = "000000"
    const val USER_NAME = "Test User"
    const val PIN_CODE = "500081"
    const val ACREAGE = "5.5"
    const val CROP = "Okra" // Ensure this is in the dropdown
    const val SOIL_TYPE = "Black" // Ensure this is in the dropdown
}

@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class AuthFlowTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var activityRule = ActivityScenarioRule(AuthActivityUpdated::class.java)

    private lateinit var device: UiDevice


    @Before
    fun setUp() {
        hiltRule.inject()
        // Clear SharedPreferences and sign out from Firebase to ensure a clean state for each test.
        // This is crucial for hermetic tests.
        FirebaseAuth.getInstance().signOut()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val sessionPrefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        sessionPrefs.edit().clear().apply()

        // For Room DB, you'd ideally clear it too. This might involve accessing the DB instance
        // which could be done via Hilt test modules or a static accessor if available.
        // e.g., MyDatabase.getInstance(context).clearAllTables()
        // For this example, we'll assume DB state doesn't interfere or is handled by test logic.

        // Initialize UiDevice instance
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)

        // Close the "Data Storage Notice" dialog if it appears
        // This uses UiAutomator because Espresso might have trouble with dialogs shown very early.
        // Give it a short timeout to appear.
        val understandButton = device.wait(Until.findObject(By.text("I Understand")), 3000)
        understandButton?.click()
        device.wait(Until.gone(By.text("I Understand")), 2000) // Wait for dialog to disappear
    }


    @Test
    fun testNewUserRegistrationFlow() {
        // 1. Enter Mobile Number
        onView(withId(R.id.et_mobile)).perform(typeText(TestAuthCredentials.TEST_MOBILE_NEW_USER), closeSoftKeyboard())
        onView(withId(R.id.btn_continue)).perform(click())

        // Wait for user details layout to be visible
        onView(withId(R.id.user_details_layout_root)).check(matches(isDisplayed()))

        // 2. Enter User Details
        onView(withId(R.id.et_name)).perform(typeText(TestAuthCredentials.USER_NAME), closeSoftKeyboard())
        onView(withId(R.id.et_pincode)).perform(typeText(TestAuthCredentials.PIN_CODE), closeSoftKeyboard())
        onView(withId(R.id.et_acreage)).perform(typeText(TestAuthCredentials.ACREAGE), closeSoftKeyboard())

        onView(withId(R.id.et_crop)).perform(click()) // Open crop dropdown
        onView(withText(TestAuthCredentials.CROP)).perform(click()) // Select crop

        onView(withId(R.id.et_soil_type)).perform(click()) // Open soil type dropdown
        onView(withText(TestAuthCredentials.SOIL_TYPE)).perform(click()) // Select soil type

        // Select Sowing Date (e.g., yesterday)
        onView(withId(R.id.et_sowing_date)).perform(click())
        onView(withClassName(org.hamcrest.Matchers.equalTo(android.widget.DatePicker::class.java.name)))
            .perform(PickerActions.setDate(
                Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH) + 1, // Month is 0-indexed in DatePicker
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH) -1 // Yesterday
            ))
        onView(withId(com.google.android.material.R.id.confirm_button)).perform(click()) // Confirm date selection

        // 3. Send OTP
        onView(withId(R.id.btn_send_otp)).perform(click())

        // 4. OTP Dialog - Assuming test phone number + OTP auto-verification or a known test OTP
        // For numbers that Firebase auto-verifies or if using a test OTP that bypasses actual SMS:
        // The OTP dialog might briefly appear and then automatically proceed.
        // If a test OTP needs to be entered (e.g. for 650-555-3434, OTP is 123456)
        onView(withText("Enter OTP")).check(matches(isDisplayed())) // Check dialog title
        onView(withId(R.id.et_otp_digit_1)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(0,1)))
        onView(withId(R.id.et_otp_digit_2)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(1,2)))
        onView(withId(R.id.et_otp_digit_3)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(2,3)))
        onView(withId(R.id.et_otp_digit_4)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(3,4)))
        onView(withId(R.id.et_otp_digit_5)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(4,5)))
        onView(withId(R.id.et_otp_digit_6)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(5,6)), closeSoftKeyboard())
        // No explicit submit button in current OtpDialogFragment, verification is triggered by text change.

        // 5. Verify Navigation to MainActivity
        // Check for a view in MainActivity (e.g., toolbar). Timeout might be needed if network is slow.
        // Using UiAutomator for checking if a new activity (MainActivity) is launched.
        device.wait(Until.hasObject(By.pkg("com.example.chinna").depth(0)), 10000) // Wait for app package
        onView(withId(R.id.toolbar)).check(matches(isDisplayed())) // Assuming MainActivity has a toolbar with this ID
    }


    @Test
    fun testExistingUserLoginFlow() {
        // This test assumes TEST_MOBILE_EXISTING_USER is already registered AND
        // its details are in the local Room DB. Seeding this for a UI test can be complex.
        // A simpler version: Firebase user exists, app fetches details or asks to create profile.
        // For this test, we'll use a number known to Firebase Test Auth.
        // The app's logic: if Firebase auth exists + local data -> main
        // if Firebase auth exists + no local data -> details form prefilled with mobile
        // if no Firebase auth -> OTP flow

        // 1. Enter Mobile Number of an existing Firebase test user
        onView(withId(R.id.et_mobile)).perform(typeText(TestAuthCredentials.TEST_MOBILE_EXISTING_USER), closeSoftKeyboard())
        onView(withId(R.id.btn_continue)).perform(click())

        // App logic:
        // If this user was registered via `testNewUserRegistrationFlow` in a previous run AND data persisted (not cleared),
        // then details might be prefilled.
        // If it's a fresh install but user is known to Firebase, it should go to OTP.
        // If user is known to Firebase AND locally, it might skip details and go to OTP or Main directly.
        // The current AuthActivityUpdated logic for checkIfUserExists:
        // - If (currentUser != null && currentUser.phoneNumber == formattedMobile): (Firebase already logged in with this number)
        //   - If (localUser != null) -> navigateToMain()
        //   - Else (no local user) -> setupUserDetailsForNewUser() (but mobile might be prefilled)
        // - Else (not authenticated with Firebase with this number):
        //   - If (localUser != null) -> setupUserDetailsWithExistingData(user)
        //   - Else (new user) -> setupUserDetailsForNewUser()
        // Then, userDetailsLayout is shown, then btnSendOtp.

        // For a Firebase test number like +1 650-555-3434, it will likely go to OTP directly
        // or show user details form if local data is missing.
        // Let's assume it shows user details form (either prefilled or for new local profile)
        onView(withId(R.id.user_details_layout_root)).check(matches(isDisplayed()))

        // If details were pre-filled, they could be checked here.
        // onView(withId(R.id.et_name)).check(matches(withText(TestAuthCredentials.USER_NAME)))

        // For simplicity, let's assume we need to click "Send OTP" regardless of prefill
        onView(withId(R.id.btn_send_otp)).perform(click())

        // Enter OTP
        onView(withText("Enter OTP")).check(matches(isDisplayed()))
        onView(withId(R.id.et_otp_digit_1)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(0,1)))
        onView(withId(R.id.et_otp_digit_2)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(1,2)))
        onView(withId(R.id.et_otp_digit_3)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(2,3)))
        onView(withId(R.id.et_otp_digit_4)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(3,4)))
        onView(withId(R.id.et_otp_digit_5)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(4,5)))
        onView(withId(R.id.et_otp_digit_6)).perform(typeText(TestAuthCredentials.TEST_OTP.substring(5,6)), closeSoftKeyboard())

        // Verify Navigation to MainActivity
        device.wait(Until.hasObject(By.pkg("com.example.chinna").depth(0)), 10000)
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
    }

    @Test
    fun testInvalidOtp() {
        onView(withId(R.id.et_mobile)).perform(typeText(TestAuthCredentials.TEST_MOBILE_EXISTING_USER), closeSoftKeyboard())
        onView(withId(R.id.btn_continue)).perform(click())

        onView(withId(R.id.user_details_layout_root)).check(matches(isDisplayed()))
        // Fill minimal details if needed by the flow before OTP
        onView(withId(R.id.et_name)).perform(typeText("Temp Name"), closeSoftKeyboard())
        onView(withId(R.id.et_pincode)).perform(typeText("111111"), closeSoftKeyboard())
        onView(withId(R.id.et_acreage)).perform(typeText("1"), closeSoftKeyboard())
        onView(withId(R.id.et_crop)).perform(click())
        onView(withText(TestAuthCredentials.CROP)).perform(click())
        onView(withId(R.id.et_soil_type)).perform(click())
        onView(withText(TestAuthCredentials.SOIL_TYPE)).perform(click())


        onView(withId(R.id.btn_send_otp)).perform(click())

        onView(withText("Enter OTP")).check(matches(isDisplayed()))
        onView(withId(R.id.et_otp_digit_1)).perform(typeText(TestAuthCredentials.INVALID_OTP.substring(0,1)))
        onView(withId(R.id.et_otp_digit_2)).perform(typeText(TestAuthCredentials.INVALID_OTP.substring(1,2)))
        onView(withId(R.id.et_otp_digit_3)).perform(typeText(TestAuthCredentials.INVALID_OTP.substring(2,3)))
        onView(withId(R.id.et_otp_digit_4)).perform(typeText(TestAuthCredentials.INVALID_OTP.substring(3,4)))
        onView(withId(R.id.et_otp_digit_5)).perform(typeText(TestAuthCredentials.INVALID_OTP.substring(4,5)))
        onView(withId(R.id.et_otp_digit_6)).perform(typeText(TestAuthCredentials.INVALID_OTP.substring(5,6)), closeSoftKeyboard())

        // Check for error message (Snackbar)
        onView(allOf(withId(com.google.android.material.R.id.snackbar_text), withText("Invalid OTP")))
            .check(matches(isDisplayed()))

        // Verify OTP dialog is still visible (or re-opens, depending on exact behavior)
        // If it closes and re-opens, this check might be flaky.
        // A more robust check might be that MainActivity is NOT displayed.
        onView(withText("Enter OTP")).check(matches(isDisplayed()))
    }

    @Test
    fun testUserDetailsValidationMessages_emptyName() {
        onView(withId(R.id.et_mobile)).perform(typeText(TestAuthCredentials.TEST_MOBILE_NEW_USER), closeSoftKeyboard())
        onView(withId(R.id.btn_continue)).perform(click())

        onView(withId(R.id.user_details_layout_root)).check(matches(isDisplayed()))
        // Leave name empty
        onView(withId(R.id.et_pincode)).perform(typeText(TestAuthCredentials.PIN_CODE), closeSoftKeyboard())
        // ... fill other fields if their validation doesn't block name validation check ...

        onView(withId(R.id.btn_send_otp)).perform(click())

        // Check for error message on name field or as a Snackbar
        // AuthActivityUpdated uses Snackbar for errors.
        onView(allOf(withId(com.google.android.material.R.id.snackbar_text), withText("Name is required")))
            .check(matches(isDisplayed()))
    }

     @Test
    fun testUserDetailsValidationMessages_invalidPincode() {
        onView(withId(R.id.et_mobile)).perform(typeText(TestAuthCredentials.TEST_MOBILE_NEW_USER), closeSoftKeyboard())
        onView(withId(R.id.btn_continue)).perform(click())

        onView(withId(R.id.user_details_layout_root)).check(matches(isDisplayed()))
        onView(withId(R.id.et_name)).perform(typeText(TestAuthCredentials.USER_NAME), closeSoftKeyboard())
        onView(withId(R.id.et_pincode)).perform(typeText("000"), closeSoftKeyboard()) // Invalid pincode

        onView(withId(R.id.btn_send_otp)).perform(click())

        onView(allOf(withId(com.google.android.material.R.id.snackbar_text), withText("PIN code must be 6 digits")))
            .check(matches(isDisplayed()))
    }
}
