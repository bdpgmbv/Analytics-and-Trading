import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
	id("org.springframework.boot") version "3.2.3" apply false
	id("io.spring.dependency-management") version "1.1.4" apply false
	id("com.diffplug.spotless") version "6.25.0"
	id("info.solidsoft.pitest") version "1.15.0" apply false
	java // Keeps Java plugin available for resolution, though we don't strictly need it applied to root
}

allprojects {
	group = "com.vyshali"
	version = "1.0.0-SNAPSHOT"

	repositories {
		mavenCentral()
	}

	// Apply Spotless to enforce Google Java Format
	apply(plugin = "com.diffplug.spotless")
	spotless {
		java {
			googleJavaFormat("1.17.0").aosp().reflowLongStrings()
			toggleOffOn()
			removeUnusedImports()
			trimTrailingWhitespace()
			endWithNewline()
		}
	}
}

subprojects {
	// FIX: Wrap Pitest logic in 'withPlugin("java")'
	// This ensures it only runs AFTER the subproject applies the Java plugin.
	pluginManager.withPlugin("java") {
		apply(plugin = "info.solidsoft.pitest")

		configure<PitestPluginExtension> {
			junit5PluginVersion.set("1.2.1")
			// Only test service logic to save time and avoid false positives
			targetClasses.set(setOf("com.vyshali.*.service.*", "com.vyshali.*.logic.*"))
			excludedClasses.set(setOf("*DTO", "*Config", "*Application", "*Test"))
			threads.set(4)
			outputFormats.set(setOf("XML", "HTML"))
			timestampedReports.set(false)
		}
	}
}