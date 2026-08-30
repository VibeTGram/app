@rem Generated for Gradle 9.4.1. Keep this launcher dependency-free.
@echo off
setlocal

set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME set JAVACMD=%JAVA_HOME%\bin\java.exe
if not defined JAVA_HOME set JAVACMD=java.exe

"%JAVACMD%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
if %ERRORLEVEL% EQU 0 goto end
exit /b %ERRORLEVEL%

:end
endlocal
