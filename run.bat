@echo off
rem Runs the application. Any arguments are passed through, e.g. run.bat --demo
setlocal
cd /d "%~dp0"
set LIB=lib\sqlite-jdbc-3.50.3.0.jar

if not exist build\classes call "%~dp0build.bat"
if errorlevel 1 exit /b 1

java --enable-native-access=ALL-UNNAMED -cp "build\classes;%LIB%" com.library.Main %*
