# Git Workflow Guidelines

## Branch Strategy

### Main Branches
- `main` - Production ready code
- `develop` - Integration branch for features

### Feature Branches
- `feature/login-improvements` - New features
- `bugfix/otp-crash` - Bug fixes
- `hotfix/urgent-fix` - Production hotfixes

## Commit Messages

Follow conventional commits format:

```
type(scope): description

[optional body]
[optional footer]
```

### Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc)
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance tasks

### Examples:
```bash
git commit -m "feat(auth): Add soil type field to user registration"
git commit -m "fix(ui): Correct OTP dialog theme colors"
git commit -m "test(auth): Add unit tests for UserRepository"
git commit -m "docs: Update testing strategy documentation"
```

## Workflow

### 1. Starting New Work
```bash
# Update your local develop branch
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/your-feature-name
```

### 2. Making Changes
```bash
# Make small, atomic commits
git add app/src/main/java/com/example/chinna/ui/auth/LoginFragment.kt
git commit -m "feat(auth): Add soil type dropdown to login"

git add app/src/main/res/layout/fragment_login.xml
git commit -m "feat(auth): Update login layout with soil type field"
```

### 3. Before Pushing
```bash
# Run tests
./gradlew test

# Check for issues
./gradlew lint

# Update from develop
git checkout develop
git pull origin develop
git checkout feature/your-feature-name
git rebase develop
```

### 4. Creating Pull Request
```bash
# Push your branch
git push origin feature/your-feature-name
```

Then create PR with:
- Clear title
- Description of changes
- Test results
- Screenshots if UI changes

### 5. Code Review Checklist
- [ ] All tests pass
- [ ] No lint warnings
- [ ] Code follows style guide
- [ ] Documentation updated
- [ ] No hardcoded values
- [ ] Error handling in place

## Protecting Working Features

### Before Starting Work:
1. Create a test baseline
```bash
# Run and save test results
./gradlew test > test-baseline.txt
```

2. Document current state
```bash
# Take screenshots of working features
# Note any specific behaviors
```

3. Create feature branch
```bash
git checkout -b feature/new-work
```

### During Development:
1. Make frequent commits
2. Run tests after each change
3. Keep changes focused
4. Don't modify unrelated code

### Before Merging:
1. Run full test suite
2. Compare with baseline
3. Manual testing checklist
4. Code review

## Emergency Rollback

If something breaks in production:

```bash
# Revert to last known good commit
git checkout main
git log --oneline -10  # Find good commit
git checkout <commit-hash>

# Create hotfix from good state
git checkout -b hotfix/emergency-fix
```

## Best Practices

1. **Never force push to main/develop**
2. **Always create PR for review**
3. **Run tests before pushing**
4. **Keep commits atomic**
5. **Write clear commit messages**
6. **Update documentation**
7. **Tag releases properly**

## Release Process

```bash
# Create release branch
git checkout -b release/1.2.0

# Bump version
# Update CHANGELOG.md
# Run final tests

# Merge to main
git checkout main
git merge --no-ff release/1.2.0
git tag -a v1.2.0 -m "Release version 1.2.0"

# Merge back to develop
git checkout develop
git merge --no-ff release/1.2.0
```

## Useful Git Commands

```bash
# See what changed
git status
git diff

# Undo last commit (keep changes)
git reset --soft HEAD~1

# Stash work temporarily
git stash
git stash pop

# See commit history
git log --oneline --graph

# Find who changed a line
git blame file.kt

# Search commit messages
git log --grep="soil type"
```

## Integration with CI/CD

All PRs should:
- Pass automated tests
- Pass lint checks
- Have updated documentation
- Include test coverage report
- Have at least one approval

This ensures working features stay working!