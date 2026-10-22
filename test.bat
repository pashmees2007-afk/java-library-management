@echo off
rem Builds everything and runs both test suites.
setlocal enabledelayedexpansion
cd /d "%~dp0"
set LIB=lib\sqlite-jdbc-3.50.3.0.jar

call "%~dp0build.bat"
if errorlevel 1 exit /b 1

if exist build\testsources.txt del build\testsources.txt
for /f "delims=" %%F in ('dir /s /b src\test\java\*.java') do (
  set "P=%%F"
  echo "!P:\=/!" >> build\testsources.txt
)

javac -d build\testclasses -cp "build\classes;%LIB%" @build\testsources.txt
if errorlevel 1 exit /b 1

java --enable-native-access=ALL-UNNAMED -cp "build\testclasses;build\classes;%LIB%" com.library.RunTests
