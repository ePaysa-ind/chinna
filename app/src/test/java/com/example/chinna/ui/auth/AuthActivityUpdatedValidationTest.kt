package com.example.chinna.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthActivityUpdatedValidationTest {

    // Helper object to encapsulate validation logic similar to AuthActivityUpdated.validateUserDetails
    // This avoids needing Android UI components for testing these specific rules.
    object UserDetailsValidator {
        private val CROPS = listOf("Okra", "Chillies", "Tomatoes", "Cotton", "Maize", "Soybean", "Rice", "Wheat", "Pulses")
        private val SOIL_TYPES = listOf("Black", "Red", "Sandy loam")

        fun validate(
            name: String,
            pinCode: String,
            acreageStr: String,
            crop: String,
            soilType: String,
            selectedSowingDate: Long? // Nullable to represent not selected
        ): Boolean {
            val namePattern = "^[a-zA-Z\\s]+$".toRegex()
            val pinCodePattern = "^[1-9][0-9]{5}$".toRegex()
            val acreage = acreageStr.toDoubleOrNull()

            return when {
                name.isBlank() -> false
                name.length < 3 -> false
                !name.matches(namePattern) -> false
                pinCode.isBlank() -> false
                pinCode.length != 6 -> false
                !pinCode.matches(pinCodePattern) -> false
                acreageStr.isBlank() || acreage == null -> false
                acreage < 1.0 || acreage > 9.0 -> false // Assuming 1-9 as per original example
                crop.isBlank() -> false
                crop !in CROPS -> false
                soilType.isBlank() -> false
                soilType !in SOIL_TYPES -> false
                selectedSowingDate != null && selectedSowingDate > System.currentTimeMillis() -> {
                    // Allow a small buffer for system time differences during test execution if needed,
                    // but generally, future dates are invalid.
                    // For this test, direct comparison is fine.
                    false
                }
                else -> true
            }
        }
    }

    @Test
    fun `validateUserDetails - valid data - returns true`() {
        assertTrue(
            UserDetailsValidator.validate(
                name = "Valid Name",
                pinCode = "500001",
                acreageStr = "5.5",
                crop = "Okra",
                soilType = "Black",
                selectedSowingDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1) // Yesterday
            )
        )
    }

    // Name Validations
    @Test
    fun `validateUserDetails - blank name - returns false`() {
        assertFalse(UserDetailsValidator.validate("", "500001", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - short name - returns false`() {
        assertFalse(UserDetailsValidator.validate("Bo", "500001", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - name with numbers - returns false`() {
        assertFalse(UserDetailsValidator.validate("Name123", "500001", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - name with special chars - returns false`() {
        assertFalse(UserDetailsValidator.validate("Name@#", "500001", "5.0", "Okra", "Black", null))
    }

    // PIN Code Validations
    @Test
    fun `validateUserDetails - blank pincode - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - short pincode - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "50000", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - long pincode - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "5000011", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - pincode starts with 0 - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "012345", "5.0", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - pincode non-digit - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "50000A", "5.0", "Okra", "Black", null))
    }

    // Acreage Validations
    @Test
    fun `validateUserDetails - blank acreage - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - non-numeric acreage - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "abc", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - acreage less than 1 - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "0.5", "Okra", "Black", null))
    }

    @Test
    fun `validateUserDetails - acreage greater than 9 - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "10.0", "Okra", "Black", null))
    }

    // Crop Validations
    @Test
    fun `validateUserDetails - blank crop - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "5.0", "", "Black", null))
    }

    @Test
    fun `validateUserDetails - invalid crop - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "5.0", "InvalidCrop", "Black", null))
    }

    // Soil Type Validations
    @Test
    fun `validateUserDetails - blank soil type - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "5.0", "Okra", "", null))
    }

    @Test
    fun `validateUserDetails - invalid soil type - returns false`() {
        assertFalse(UserDetailsValidator.validate("Valid Name", "500001", "5.0", "Okra", "InvalidSoil", null))
    }

    // Sowing Date Validations
    @Test
    fun `validateUserDetails - future sowing date - returns false`() {
        val futureDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1) // Tomorrow
        assertFalse(
            UserDetailsValidator.validate(
                name = "Valid Name",
                pinCode = "500001",
                acreageStr = "5.0",
                crop = "Okra",
                soilType = "Black",
                selectedSowingDate = futureDate
            )
        )
    }

    @Test
    fun `validateUserDetails - present sowing date (valid) - returns true`() {
        // A date set to "now" should be valid (or very slightly in the past due to execution time)
        assertTrue(
            UserDetailsValidator.validate(
                name = "Valid Name",
                pinCode = "500001",
                acreageStr = "5.0",
                crop = "Okra",
                soilType = "Black",
                selectedSowingDate = System.currentTimeMillis()
            )
        )
    }

    @Test
    fun `validateUserDetails - null sowing date (valid as it's optional) - returns true`() {
        assertTrue(
            UserDetailsValidator.validate(
                name = "Valid Name",
                pinCode = "500001",
                acreageStr = "5.0",
                crop = "Okra",
                soilType = "Black",
                selectedSowingDate = null
            )
        )
    }
}
