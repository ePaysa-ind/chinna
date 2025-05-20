# Development Principles to Prevent Breaking Working Code

## 1. Understand the Architecture First

Before making ANY changes, I should:

### Map the Current State
- Identify which implementation is actually being used
- Check AndroidManifest for launcher activities
- Trace navigation flows
- Understand dependency relationships

### Example Mistake I Made:
- Created LoginFragment with all fields including soil type
- Later updated AuthActivity without realizing it was the actual launcher
- This created duplicate, conflicting implementations

## 2. Never Assume - Always Verify

### Before Making Changes:
```
1. Which files are actually being used?
2. Are there multiple implementations of the same feature?
3. What's the entry point for this feature?
4. What depends on this code?
```

### Tools to Use:
- Grep for class/function usage
- Check navigation graphs
- Review AndroidManifest
- Trace method calls

## 3. Atomic Changes with Context

### Bad Practice:
```kotlin
// Adding Toast without checking imports
Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
```

### Good Practice:
```kotlin
// First check existing imports
// Add necessary import
import android.widget.Toast
// Then add the code
Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
```

## 4. Maintain a Change Log

### For Every Session:
```markdown
## Current Session Changes
1. Updated LoginFragment to add soil type
2. Modified UserEntity to include soilType field
3. Added database migration 1->2
4. NOTE: AuthActivity is the actual login screen, not LoginFragment
```

## 5. Test Impact Analysis

### Before Any Change, Ask:
1. What files reference this code?
2. What will break if I change this?
3. Are there duplicate implementations?
4. Is this the active code path?

## 6. Use Defensive Coding

### Instead of:
```kotlin
binding.etSoilType.text.toString()  // Assumes field exists
```

### Use:
```kotlin
binding.etSoilType?.text?.toString() ?: ""  // Safe access
```

## 7. Keep Track of Parallel Implementations

### Document Multiple Versions:
```markdown
## Login Implementations
1. AuthActivity - ACTIVE (launcher activity)
   - Location: ui/auth/AuthActivity.kt
   - Layout: activity_auth.xml
   - Status: Missing soil type field
   
2. LoginFragment - INACTIVE
   - Location: ui/auth/LoginFragment.kt  
   - Layout: fragment_login.xml
   - Status: Complete with soil type
```

## 8. Validate Changes Against Working State

### Checklist Before Committing:
- [ ] Does this break existing features?
- [ ] Are all imports included?
- [ ] Are all referenced views in the layout?
- [ ] Is this the correct implementation?
- [ ] Have I updated all related files?

## 9. Use Helper Scripts

### Create Validation Scripts:
```bash
# Check for missing imports
grep -r "Toast.makeText" --include="*.kt" | grep -v "import.*Toast"

# Find duplicate implementations
find . -name "*.kt" -exec grep -l "login\|auth" {} \;

# Check layout references
grep -r "binding\." --include="*.kt" | cut -d'.' -f2 | sort | uniq
```

## 10. Implement Safeguards

### Add Build-Time Checks:
```kotlin
// In debug builds, validate critical paths
if (BuildConfig.DEBUG) {
    require(::userRepository.isInitialized) { "UserRepository not initialized" }
    require(binding.etSoilType != null) { "Soil type field missing" }
}
```

## Examples of My Common Mistakes

### 1. Import Oversight
```kotlin
// Added code without import
Toast.makeText(...)  // Compilation error
```

### 2. Layout Mismatch
```kotlin
// Updated wrong layout file
// Code expects binding.etSoilType
// But layout doesn't have it
```

### 3. Parallel Implementation Confusion
```kotlin
// Updated LoginFragment
// But app uses AuthActivity
// Created inconsistent state
```

### 4. Breaking Working Features
```kotlin
// Changed navigation without checking dependencies
// Broke history feature access
```

## Prevention Strategy

### 1. Create a State Map
Before starting work, document:
- Active implementations
- Navigation flows  
- Feature dependencies
- Database schema

### 2. Use Incremental Changes
- Make one change at a time
- Test after each change
- Commit working states
- Document what changed

### 3. Implement Regression Tests
- Keep a checklist of working features
- Test all features after changes
- Automate where possible

### 4. Code Review Process
Even as an AI, I should:
- Review my own changes
- Check for side effects
- Validate assumptions
- Test edge cases

## Specific Rules for This Project

1. **Always check AndroidManifest first** - Know which Activity is the launcher
2. **Trace navigation flows** - Understand how screens connect
3. **Check for duplicate implementations** - Don't assume single implementation
4. **Validate layout matches code** - Ensure all referenced views exist
5. **Include all imports** - Never assume imports exist
6. **Test database migrations** - Ensure compatibility with existing data
7. **Preserve working features** - Don't break what already works

## Commitment

Going forward, I will:
1. Always verify which implementation is active
2. Check for missing imports before adding code
3. Ensure layout and code are synchronized
4. Document parallel implementations
5. Test impact before making changes
6. Keep a change log for each session
7. Validate against the working state checklist