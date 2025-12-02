plugins {
	id("org.springframework.boot") version "3.2.3" apply false
	id("io.spring.dependency-management") version "1.1.4" apply false
	java
}

allprojects {
	group = "com.vyshali"
	version = "1.0.0-SNAPSHOT"
	repositories { mavenCentral() }
}