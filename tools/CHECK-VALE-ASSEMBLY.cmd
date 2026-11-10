@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Run this in a second window. Existing assembly and source stay untouched.
if not exist "%~dp0check_assembly_status.py" goto missing_tool
where py.exe >nul 2>nul
if not errorlevel 1 goto use_py
where python.exe >nul 2>nul
if not errorlevel 1 goto use_python
echo Python 3.8 or later must already be installed. No software was downloaded.
set "vale_status_result=2"
goto finished

:use_py
py.exe -3 "%~dp0check_assembly_status.py"
set "vale_status_result=%ERRORLEVEL%"
goto finished

:use_python
python.exe "%~dp0check_assembly_status.py"
set "vale_status_result=%ERRORLEVEL%"
goto finished

:missing_tool
echo Keep CHECK-VALE-ASSEMBLY.cmd and check_assembly_status.py together in Vale-Export\vale-038.
set "vale_status_result=2"

:finished
echo.
echo Keep the original assembly window open. This diagnostic did not stop it.
pause
exit /b %vale_status_result%
