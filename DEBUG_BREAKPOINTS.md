# Debug Breakpoints for App Crash

Add breakpoints at these specific locations:

## 1. ChinnaApplication.kt
```kotlin
override fun onCreate() {
    super.onCreate()  // <- Add breakpoint here (line 11)
    
    try {
        // Initialize Firebase
        FirebaseApp.initializeApp(this)  // <- Add breakpoint here (line 15)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

## 2. AuthActivity.kt
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)  // <- Add breakpoint here (line 39)
    
    try {
        // Check if already logged in
        if (auth.currentUser != null) {  // <- Add breakpoint here (line 43)
            navigateToMain()
            return
        }
```

## 3. AppModule.kt
```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
    return Room.databaseBuilder(  // <- Add breakpoint here (line 60)
        context,
        AppDatabase::class.java,
        "chinna_database"
    )
```

## 4. MainActivity.kt (if it reaches here)
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)  // <- Add breakpoint here (line 42)
    
    try {
        // Check if user is logged in
        if (!userRepository.isLoggedIn()) {  // <- Add breakpoint here (line 46)
```