@echo off
setlocal enabledelayedexpansion

:: StressPilot Multiplatform Launcher (Windows)
:: Automatically handles AppCDS (.jsa) generation and usage.

set "SCRIPT_DIR=%~dp0"
set "APP_ROOT=%SCRIPT_DIR%.."

:: Search for the executable JAR
set "JAR_FILE="
for /f "delims=" %%i in ('dir /b /s "%APP_ROOT%\target\stresspilot-*-exec.jar" 2^>nul') do (
    set "JAR_FILE=%%i"
    goto :found_jar
)
for /f "delims=" %%i in ('dir /b /s "%APP_ROOT%\stresspilot-*-exec.jar" 2^>nul') do (
    set "JAR_FILE=%%i"
    goto :found_jar
)

:found_jar
if "%JAR_FILE%"=="" (
    echo Error: Could not find stresspilot-*-exec.jar in %APP_ROOT% or %APP_ROOT%\target
    pause
    exit /b 1
)

set "JSA_FILE=%JAR_FILE:.jar=.jsa%"

:: 1. Staleness Check
if exist "%JSA_FILE%" (
    set "REGENERATE=false"
    
    :: Check if JAR is newer than JSA (using forfiles for timestamp comparison)
    for /f "delims=" %%i in ('xcopy /d /y /l "%JAR_FILE%" "%JSA_FILE%" ^| findstr /i "File(s)"') do (
        for /f "tokens=1" %%j in ("%%i") do (
            if %%j gtr 0 (
                echo Application update detected. Updating JVM cache...
                set "REGENERATE=true"
            )
        )
    )
    
    :: Check if JVM rejects the archive
    java -Xshare:on -XX:SharedArchiveFile="%JSA_FILE%" -version >nul 2>&1
    if errorlevel 1 (
        if "!REGENERATE!"=="false" (
            echo JVM version mismatch or incompatible cache. Updating JVM cache...
            set "REGENERATE=true"
        )
    )
    
    if "!REGENERATE!"=="true" (
        del "%JSA_FILE%"
    )
)

:: 2. Training (if JSA missing)
if not exist "%JSA_FILE%" (
    echo First run optimization: generating JVM cache. This will take a few seconds...
    java -Dspring.context.exit=onRefresh -XX:ArchiveClassesAtExit="%JSA_FILE%" -jar "%JAR_FILE%" >nul 2>&1
    
    if not exist "%JSA_FILE%" (
        echo Warning: Failed to generate AppCDS cache. Starting normally...
    ) else (
        echo Optimization complete.
    )
)

:: 3. Execution
if exist "%JSA_FILE%" (
    java -XX:SharedArchiveFile="%JSA_FILE%" -jar "%JAR_FILE%" %*
) else (
    java -jar "%JAR_FILE%" %*
)

endlocal
