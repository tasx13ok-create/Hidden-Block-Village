@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 goto try_python
py -3 "%~dp0vale_companion.py" --setup
goto done
:try_python
where python >nul 2>nul
if errorlevel 1 goto missing_python
python "%~dp0vale_companion.py" --setup
goto done
:missing_python
echo Python 3.8 or newer is required. No software was installed.
:done
echo.
echo The companion has stopped. This window can be closed.
pause
