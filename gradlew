#!/usr/bin/env sh
GRADLE_APP_NAME=Gradle
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit
JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}
exec "$JAVA_HOME/bin/java" -Xmx64m -Xms64m -Dorg.gradle.appname=$APP_BASE_NAME -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
