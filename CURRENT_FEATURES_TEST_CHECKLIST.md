# Current Features Test Checklist

## Before Making Any Changes
Run through this checklist to ensure all features are working correctly.

## 1. Login/Registration Flow ✅
- [ ] Launch app shows login screen
- [ ] All fields appear in correct order:
  - [ ] Name
  - [ ] Village  
  - [ ] Acreage
  - [ ] Crop (dropdown)
  - [ ] Soil Type (dropdown)
  - [ ] Date of Sowing
  - [ ] Mobile Number
- [ ] Name field rejects numbers
- [ ] Village field rejects numbers
- [ ] Acreage accepts only decimals 1-9
- [ ] Crop dropdown shows all options
- [ ] Soil Type shows: Black, Red, Sandy loam
- [ ] Date picker doesn't allow future dates
- [ ] Mobile number accepts only 10 digits
- [ ] Send OTP button works
- [ ] OTP dialog appears with:
  - [ ] Black background
  - [ ] White text
  - [ ] 6 digit input boxes
- [ ] OTP verification works
- [ ] User data is saved correctly
- [ ] App navigates to home screen

## 2. Home Screen ✅
- [ ] Header shows:
  - [ ] App icon (32dp)
  - [ ] "Chinna" text
  - [ ] Proper spacing from top (24dp)
- [ ] Welcome text shows user's actual name
- [ ] Current crop displays without duplicate text
- [ ] Three main cards visible:
  - [ ] Pest & Disease Identification
  - [ ] Explore Other Crops
  - [ ] Smart Advisory
- [ ] All cards are clickable

## 3. Bottom Navigation ✅
- [ ] 5 items visible:
  - [ ] Home (home icon)
  - [ ] Identify (pest icon)
  - [ ] Advice (chat icon)
  - [ ] Explore (explore icon)
  - [ ] Logout (logout icon)
- [ ] Icons match card icons
- [ ] Navigation works correctly
- [ ] Current screen is highlighted

## 4. History Feature ✅
- [ ] History dialog opens from home screen
- [ ] Empty state shows when no history
- [ ] Previous results display correctly
- [ ] Non-plant results are filtered out

## 5. Logout Feature ✅
- [ ] Logout button in bottom nav works
- [ ] User data is cleared
- [ ] App returns to login screen

## 6. Data Persistence ✅
- [ ] User data survives app restart
- [ ] Soil type is saved and retrieved
- [ ] All user fields are maintained
- [ ] Database migration works (v1 to v2)

## 7. Theme Consistency ✅
- [ ] Dark background throughout
- [ ] White primary text
- [ ] Gray secondary text
- [ ] Yellow accent color
- [ ] Consistent spacing and padding

## 8. Error Handling ✅
- [ ] Network errors show toast messages
- [ ] Invalid inputs show error messages
- [ ] Database errors are caught
- [ ] Navigation errors have fallback

## Critical Paths to Test After Any Change:

### Path 1: New User Registration
1. Fill all fields correctly
2. Send OTP
3. Verify OTP
4. Check home screen greeting
5. Check data persistence

### Path 2: Existing User
1. Logout
2. Login with same number
3. Check data is retrieved
4. Navigate all screens

### Path 3: Navigation Flow
1. Start at home
2. Navigate to each screen
3. Use bottom nav
4. Check back navigation

### Path 4: Edge Cases
1. Register without crop
2. Register without sowing date
3. Try invalid inputs
4. Test on different screen sizes

## Automated Test Coverage Goals:
- [ ] Unit tests for UserRepository
- [ ] Unit tests for data validation
- [ ] UI tests for registration flow
- [ ] UI tests for navigation
- [ ] Integration tests for database

## Manual Test Devices:
- [ ] Small phone (5")
- [ ] Medium phone (6")
- [ ] Large phone (6.7")
- [ ] Tablet (if supported)
- [ ] Android 6.0 (API 23)
- [ ] Android 10 (API 29)
- [ ] Android 13 (API 33)

## Performance Checks:
- [ ] App launches in < 3 seconds
- [ ] Navigation is instant
- [ ] No memory leaks
- [ ] Database operations are fast

## Accessibility:
- [ ] Text is readable
- [ ] Touch targets are 44dp minimum
- [ ] Colors have sufficient contrast
- [ ] Screen reader compatible

## Notes:
- Always test on a fresh install
- Test with airplane mode
- Test with slow network
- Clear app data between tests
- Check logcat for errors