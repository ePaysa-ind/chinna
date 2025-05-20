# Pre-Change Checklist

⚠️ **CRITICAL: DO NOT REVERT THE LOGOUT SEQUENCE CHANGES! LOGOUT HAS BEEN FIXED TO PREVENT BLACK SCREENS AND LOGIN SCREEN FLICKERING. THE FIXES ADDRESS FIREBASE SECURITY RULES AND AUTH STATE MANAGEMENT.** ⚠️

## Before Making ANY Code Change

### 1. Identify the Context
- [ ] What feature am I modifying?
- [ ] Which files are involved?
- [ ] Is this the active implementation?
- [ ] Are there duplicate/parallel implementations?

### 2. Check Dependencies
- [ ] Run: `grep -r "ClassName" --include="*.kt"` to find usage
- [ ] Check AndroidManifest for activity declarations
- [ ] Review navigation graph for fragment usage
- [ ] Identify what depends on this code

### 3. Verify Current State
- [ ] Does the current code compile?
- [ ] Are all features working?
- [ ] Document current behavior
- [ ] Take screenshots if UI changes

### 4. Plan the Change
- [ ] What exactly needs to change?
- [ ] What files need modification?
- [ ] What could break?
- [ ] Do I need to update multiple places?

### 5. Check Prerequisites
- [ ] Are all necessary imports present?
- [ ] Do referenced layouts exist?
- [ ] Are all view IDs correct?
- [ ] Is the database schema compatible?

### 6. Make Changes Systematically
- [ ] Change one thing at a time
- [ ] Add imports BEFORE using classes
- [ ] Update layouts BEFORE referencing views
- [ ] Test after EACH change

### 7. Validate Changes
- [ ] Does it still compile?
- [ ] Do existing features still work?
- [ ] Is the new feature working?
- [ ] Are there any warnings/errors?

### 8. Document Changes
- [ ] What files were modified?
- [ ] What was the purpose?
- [ ] Any known issues?
- [ ] Any follow-up needed?

## Common Pitfalls to Avoid

1. **DON'T** assume a class is imported - CHECK first
2. **DON'T** assume a view exists in layout - VERIFY first
3. **DON'T** assume single implementation - SEARCH first
4. **DON'T** change navigation without checking impacts
5. **DON'T** modify database without migration plan
6. **DON'T** forget to properly close XML tags - VALIDATE first
7. **DON'T** assume button handlers work from all screens - TEST first
8. **DON'T** remove any Firebase auth state cleanup in logout sequence

## Specific Project Checks

### For Chinna App:
1. Check if AuthActivity or LoginFragment is being used
2. Verify MainActivity navigation setup
3. Ensure database migrations are proper
4. Validate theme/style references
5. Confirm Firebase configuration (Blaze plan)
6. Verify DialogTheme consistency
7. Check layout XML structure for proper tag closing
8. Test History button from all screens

## Quick Commands

```bash
# Find where a class is used
grep -r "ClassName" --include="*.kt" --include="*.java"

# Find layout references
grep -r "R.layout.layout_name" --include="*.kt"

# Find string references  
grep -r "@string/string_name" --include="*.xml"

# Check for missing imports
grep -r "ClassName\." --include="*.kt" | grep -v "import"

# Find navigation actions
grep -r "findNavController" --include="*.kt"

# Find XML files with potential tag issues
grep -n "</RelativeLayout>" file.xml | wc -l
grep -n "<RelativeLayout" file.xml | wc -l

# Check Firebase configuration
cat app/google-services.json | grep "project_id"
```

## Recovery Actions

If something breaks:
1. Identify the last working state
2. Check what changed between then and now
3. Revert problematic changes
4. Apply fixes incrementally
5. Test after each fix

Remember: Prevention is better than debugging!