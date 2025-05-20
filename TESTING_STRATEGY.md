# Testing Strategy for Chinna App

## 1. Unit Tests

Create unit tests for critical business logic:

```kotlin
// Example: UserRepositoryTest.kt
@RunWith(MockitoJUnitRunner::class)
class UserRepositoryTest {
    
    @Mock
    private lateinit var userDao: UserDao
    
    @Mock
    private lateinit var prefsManager: PrefsManager
    
    private lateinit var userRepository: UserRepository
    
    @Before
    fun setup() {
        userRepository = UserRepository(userDao, prefsManager)
    }
    
    @Test
    fun `saveUser should save user with all fields including soilType`() = runTest {
        // Arrange
        val expectedUser = UserEntity(
            mobile = "9876543210",
            name = "Test User",
            village = "Test Village",
            acreage = 5.0,
            crop = "Cotton",
            sowingDate = 1234567890L,
            soilType = "Black"
        )
        
        // Act
        userRepository.saveUser(
            mobile = "9876543210",
            name = "Test User",
            village = "Test Village",
            acreage = 5.0,
            crop = "Cotton",
            sowingDate = 1234567890L,
            soilType = "Black"
        )
        
        // Assert
        verify(userDao).insertUser(expectedUser)
        verify(prefsManager).saveUserLoggedIn(true)
        verify(prefsManager).saveUserMobile("9876543210")
    }
}
```

## 2. UI Tests

Create UI tests for critical user flows:

```kotlin
// Example: LoginFlowTest.kt
@RunWith(AndroidJUnit4::class)
class LoginFlowTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    @Test
    fun testLoginFlowWithAllFields() {
        // Navigate to login
        onView(withId(R.id.homeFragment)).perform(click())
        
        // Fill in all fields
        onView(withId(R.id.et_name)).perform(typeText("Test User"))
        onView(withId(R.id.et_village)).perform(typeText("Test Village"))
        onView(withId(R.id.et_acreage)).perform(typeText("5.5"))
        onView(withId(R.id.et_crop)).perform(click())
        onView(withText("Cotton")).perform(click())
        onView(withId(R.id.et_soil_type)).perform(click())
        onView(withText("Black")).perform(click())
        // ... continue with date and phone number
        
        // Verify OTP dialog appears
        onView(withText("Enter OTP")).check(matches(isDisplayed()))
    }
}
```

## 3. Feature Flags

Use feature flags to isolate new features:

```kotlin
// FeatureFlags.kt
object FeatureFlags {
    const val SOIL_TYPE_ENABLED = true
    const val NEW_LOGIN_FLOW = true
    const val HISTORY_IN_BOTTOM_NAV = true
}

// Usage in code
if (FeatureFlags.SOIL_TYPE_ENABLED) {
    // Show soil type field
}
```

## 4. Regression Test Suite

Create a manual regression test checklist:

### Login Flow
- [ ] User can enter name without numbers
- [ ] User can select village
- [ ] User can enter acreage (1-9)
- [ ] User can select crop
- [ ] User can select soil type
- [ ] User can select sowing date
- [ ] User can enter phone number
- [ ] OTP is sent successfully
- [ ] OTP dialog shows correct theme (black bg, white text)
- [ ] User data is saved after OTP verification
- [ ] User is greeted by name on home screen

### Navigation
- [ ] Bottom navigation works correctly
- [ ] History shows in bottom nav
- [ ] Logout is accessible from bottom nav
- [ ] App icon shows in header
- [ ] Proper spacing in header

### Data Persistence
- [ ] User data persists after app restart
- [ ] Soil type is saved and retrieved correctly
- [ ] Crop selection persists

## 5. Integration Tests

Test critical integrations:

```kotlin
@Test
fun testDatabaseMigration() {
    // Create database with version 1
    val db = helper.createDatabase(TEST_DB, 1).apply {
        execSQL("INSERT INTO users (mobile, name, village, acreage, crop, sowingDate) VALUES ('1234567890', 'Test', 'Village', 5.0, 'Cotton', 123456)")
        close()
    }
    
    // Migrate to version 2
    db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    
    // Verify data integrity
    val cursor = db.query("SELECT * FROM users")
    assertTrue(cursor.moveToFirst())
    assertEquals("", cursor.getString(cursor.getColumnIndex("soilType")))
}
```

## 6. Code Review Checklist

Before making changes:
- [ ] Run all existing tests
- [ ] Document what functionality might be affected
- [ ] Review dependencies of the code being changed

After making changes:
- [ ] Run all tests again
- [ ] Manually test related features
- [ ] Check for any console errors or warnings
- [ ] Test on different screen sizes/orientations

## 7. Continuous Integration

Set up CI/CD pipeline:

```yaml
# .github/workflows/android.yml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
    - name: Run tests
      run: ./gradlew test
    - name: Run instrumented tests
      run: ./gradlew connectedAndroidTest
```

## 8. Documentation

Keep feature documentation updated:

```markdown
# Feature: User Registration

## Overview
Users register with personal and farming details.

## Fields (in order):
1. Name - Text only, no numbers
2. Village - Text only
3. Acreage - Decimal (1-9)
4. Crop - Dropdown selection
5. Soil Type - Black/Red/Sandy loam
6. Sowing Date - Date picker
7. Mobile Number - 10 digits

## Key Behaviors:
- Soil type is mandatory
- If crop is selected, sowing date is required
- All data saved to local database after OTP verification
```

## 9. Version Control Best Practices

```bash
# Create feature branches
git checkout -b feature/login-improvements

# Make atomic commits
git commit -m "fix: Update OTP dialog theme colors"
git commit -m "feat: Add soil type to user registration"

# Use pull requests for review
```

## 10. Monitoring and Analytics

Add logging for critical paths:

```kotlin
// Analytics.kt
object Analytics {
    fun logRegistrationStep(step: String, success: Boolean) {
        // Log to analytics service
    }
    
    fun logError(error: String, context: String) {
        // Log errors for monitoring
    }
}

// Usage
Analytics.logRegistrationStep("soil_type_selected", true)
```

## Summary

By implementing these strategies, you can:
1. Catch regressions early with automated tests
2. Isolate new features with feature flags
3. Have a clear checklist for manual testing
4. Document expected behaviors
5. Use version control effectively
6. Monitor production for issues

This creates a safety net that protects working features while allowing confident development of new features and bug fixes.