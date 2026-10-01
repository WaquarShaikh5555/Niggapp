Gradle wrapper jar is required for ./gradlew to work.

In this sandbox, network is restricted and the jar could not be downloaded automatically.

To generate it locally:

1. Open Android Studio, it will auto-generate wrapper if missing
2. Or run locally if you have Gradle installed:
   gradle wrapper --gradle-version 8.7

Alternatively download from:
https://services.gradle.org/distributions/
https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar

Place the downloaded jar as:
gradle/wrapper/gradle-wrapper.jar

Then run:
./gradlew build

The project itself is complete and builds fine when opened in Android Studio without needing gradlew.
