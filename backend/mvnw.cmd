@REM ----------------------------------------------------------------------------
@REM  Licensed to the Apache Software Foundation (ASF) under one
@REM  or more contributor license agreements.  See the NOTICE file
@REM  distributed with this work for additional information
@REM  regarding copyright ownership.  The ASF licenses this file
@REM  to you under the Apache License, Version 2.0 (the
@REM  "License"); you may not use this file except in compliance
@REM  with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing,
@REM  software distributed under the License is distributed on an
@REM  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM  KIND, either express or implied.  See the License for the
@REM  specific language governing permissions and limitations
@REM  under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MAVEN_CMD_LINE_ARGS%"=="" (SET __MAVEN_CMD_LINE_ARGS=%*) else (SET __MAVEN_CMD_LINE_ARGS=%__MAVEN_CMD_LINE_ARGS% %*)

@SETLOCAL
set ERROR_CODE=0

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set MAVEN_WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

@REM Find JAVA_HOME or java.exe
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto error

:execute
@REM Setup the command line
set WRAPPER_JAR=%MAVEN_WRAPPER_JAR%
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@REM Determine the Maven wrapper download URL
set DEFAULT_MAVEN_DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
for /f "tokens=1,2 delims==" %%a in ("%MAVEN_WRAPPER_PROPERTIES%") do (
    if "%%a"=="wrapperUrl" set DEFAULT_MAVEN_DOWNLOAD_URL=%%b
)

@REM Execute Maven
"%JAVA_EXE%" ^
  -Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR% ^
  -classpath %WRAPPER_JAR% ^
  %WRAPPER_LAUNCHER% ^
  %__MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
if not "%ERROR_CODE%"=="0" (
    echo Failed to run Maven Wrapper
    exit /b %ERROR_CODE%
)
