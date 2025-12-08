import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
	id("org.springframework.boot") version "3.2.3" apply false
	id("io.spring.dependency-management") version "1.1.4" apply false
	id("com.diffplug.spotless") version "6.25.0"
	id("info.solidsoft.pitest") version "1.15.0" apply false
	java
}

allprojects {
	group = "com.vyshali"
	version = "1.0.0-SNAPSHOT"

	repositories {
		mavenCentral()
	}

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
	pluginManager.withPlugin("java") {
		apply(plugin = "info.solidsoft.pitest")
		configure<PitestPluginExtension> {
			junit5PluginVersion.set("1.2.1")
			targetClasses.set(setOf("com.vyshali.*.service.*", "com.vyshali.*.logic.*"))
			excludedClasses.set(setOf("*DTO", "*Config", "*Application", "*Test"))
			threads.set(4)
			outputFormats.set(setOf("XML", "HTML"))
			timestampedReports.set(false)
		}
	}
}