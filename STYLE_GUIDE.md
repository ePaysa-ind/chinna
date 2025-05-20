# Chinna App Style Guide

## Color Palette

### Primary Colors (Dark Theme)
- **Background**: `@color/dark_background` (#121212)
- **Surface**: `@color/dark_surface` (#1E1E1E)
- **Primary**: `@color/dark_primary` (#1B5E20)
- **Accent**: `@color/dark_accent` (#FFD700)

### Text Colors
- **Primary Text**: `@color/dark_text_primary` (#EFEFEF)
- **Secondary Text**: `@color/dark_text_secondary` (#B3B3B3)
- **On Primary**: `@color/text_on_primary` (#FFFFFF)

### Status Colors
- **Success**: `@color/success` (#228B22)
- **Error**: `@color/error` (#F44336)
- **Warning**: `@color/warning` (#FF8C00)

## Best Practices

### 1. Use Theme Attributes
Instead of hardcoding colors, use theme attributes:
```xml
<!-- Good -->
android:background="?attr/colorSurface"
android:textColor="?android:attr/textColorPrimary"

<!-- Avoid -->
android:background="@color/dark_surface"
android:textColor="@color/dark_text_primary"
```

### 2. Material Components
Use Material Design components with proper styling:
```xml
<com.google.android.material.textfield.TextInputLayout
    app:boxStrokeColor="@color/dark_accent"
    app:hintTextColor="@color/dark_accent">

<com.google.android.material.button.MaterialButton
    app:backgroundTint="@color/dark_accent"
    app:cornerRadius="@dimen/corner_radius_medium" />
```

### 3. Text Sizes
Use predefined dimensions:
```xml
android:textSize="@dimen/text_size_title"  <!-- 24sp -->
android:textSize="@dimen/text_size_body"   <!-- 16sp -->
android:textSize="@dimen/text_size_small"  <!-- 14sp -->
```

### 4. Spacing
Use consistent padding/margins:
```xml
android:padding="@dimen/padding_medium"    <!-- 16dp -->
android:padding="@dimen/padding_large"     <!-- 24dp -->
android:layout_margin="@dimen/margin_medium"
```

## Component Styling

### Cards
```xml
<com.google.android.material.card.MaterialCardView
    app:cardBackgroundColor="@color/dark_surface"
    app:cardCornerRadius="@dimen/corner_radius_medium"
    app:cardElevation="4dp">
```

### Buttons
```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.MaterialComponents.Button"
    app:backgroundTint="@color/dark_accent"
    android:textColor="@color/white" />
```

## Dark Theme Requirements
- Always use dark theme colors
- Ensure sufficient contrast for readability
- Use emojis for visual enhancement
- Keep text simple (Grade 3 English)

## Avoid
- Hardcoded colors (use theme attributes)
- Custom text sizes (use dimensions)
- Inconsistent spacing
- Light theme elements