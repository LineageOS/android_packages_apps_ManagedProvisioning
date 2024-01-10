/**
 * This is an adaptation of the build.gradle found in external/setupdesign/lottie_loading_layout.
 * There are certain classes that must be modified in order to work with the sysui studio build.
 */
plugins {
  id("com.android.library")
}

val top = extra["ANDROID_TOP"].toString()

android {
  namespace = "com.google.android.setupdesign.lottieloadinglayout"
  defaultConfig {
    vectorDrawables.useSupportLibrary = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  sourceSets {
    sourceSets.getByName("main") {
      manifest.srcFile("$top/external/setupdesign/lottie_loading_layout/AndroidManifest.xml")
      java.srcDirs(listOf("src", "$top/external/setupdesign/lottie_loading_layout/src"))
      res.srcDirs(listOf("$top/external/setupdesign/lottie_loading_layout/res"))
    }
  }
}

dependencies {
  api(libs.androidx.annotation)
  api(project(":setupcompat"))
  api(project(":setupdesign"))
  api(libs.com.airbnb.android.lottie)
}
