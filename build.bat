@echo off
rem Compiles the application into build\classes.
setlocal enabledelayedexpansion
cd /d "%~dp0"
set LIB=lib\sqlite-jdbc-3.50.3.0.jar

if not exist "%LIB%" (
  echo Missing %LIB%
  echo Download it from https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.50.3.0/
  exit /b 1
)

if not exist build mkdir build
rem Paths go into the argument file quoted and with forward slashes: javac
rem splits unquoted paths on spaces and treats a backslash as an escape.
if exist build\sources.txt del build\sources.txt
for /f "delims=" %%F in ('dir /s /b src\main\java\*.java') do (
  set "P=%%F"
  echo "!P:\=/!" >> build\sources.txt
)

javac -d build\classes -cp "%LIB%" @build\sources.txt
if errorlevel 1 exit /b 1
echo Build complete: build\classes
