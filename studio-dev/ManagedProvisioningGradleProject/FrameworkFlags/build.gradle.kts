plugins {
    id("aconfig")
    id(libs.plugins.android.library.get().pluginId)
}

val androidTop = extra["ANDROID_TOP"].toString()
val moduleDir = "$androidTop/frameworks/base/core"

aconfig {
    aconfigDeclaration {
        packageName.set("android.widget.flags")
        srcFile.setFrom(fileTree("$moduleDir/java/android/widget/flags") {
            include("*.aconfig")
        })
    }
    aconfigDeclaration {
        packageName.set("android.os")
        srcFile.setFrom("$moduleDir/java/android/os/flags.aconfig")
    }
    aconfigDeclaration {
        packageName.set("android.app.admin.flags")
        srcFile.setFrom("$moduleDir/java/android/app/admin/flags/flags.aconfig")
    }
}

android {
    namespace = "android.frameworks.base"
    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}