# Keep DTOs and API models for kotlinx.serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
