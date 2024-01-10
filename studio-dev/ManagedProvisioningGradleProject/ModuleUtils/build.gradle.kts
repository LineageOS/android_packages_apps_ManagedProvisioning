plugins {
    id("com.android.library")
}
val androidTop = extra["ANDROID_TOP"].toString()
android {
    namespace = "com.android.internal.modules.utils"

    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(
                "$androidTop/frameworks/libs/modules-utils/java",
        ))
        java.exclude(
                "android/annotations/**",
                "com/android/internal/**",
                "com/android/modules/**",
        )
        manifest.srcFile("empty-manifest.xml")
    }
}
