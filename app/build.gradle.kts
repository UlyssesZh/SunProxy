plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
}

android {
	namespace = "io.github.ulysseszh.sunproxy"
	compileSdk = 36

	defaultConfig {
		applicationId = "io.github.ulysseszh.sunproxy"
		minSdk = 21
		targetSdk = 36
		versionCode = 102
		versionName = "0.1.2"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	kotlinOptions {
		jvmTarget = "11"
	}
	externalNativeBuild {
		cmake {
			path = file("CMakeLists.txt")
			version = "3.22.1"
		}
	}
	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
	buildToolsVersion = "36.0.0"
	ndkVersion = "29.0.13113456 rc1"
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)
	implementation(libs.material)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.preference.ktx)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.material3)
	implementation(libs.androidx.coordinatorlayout)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}
