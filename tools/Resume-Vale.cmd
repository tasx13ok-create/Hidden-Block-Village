@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem File preparation only: no Java launch, download, policy change, or deployment.
if "%~1"=="" goto usage
if "%~2"=="" goto usage
if not "%~5"=="" goto usage
set "vale_contract=%~f1"
set "vale_contract_sha256=%~2"
set "vale_source=D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage"
set "vale_destination=D:\Codex\Vale-Export"
if not "%~3"=="" set "vale_source=%~f3"
if not "%~4"=="" set "vale_destination=%~f4"
if not exist "%vale_contract%" goto missing_contract
for %%T in (prepare_connected_release.py assemble_release.py release_pack.py package_bootstrap.py) do if not exist "%~dp0%%T" goto missing_tools
where py.exe >nul 2>nul
if not errorlevel 1 goto use_py
where python.exe >nul 2>nul
if not errorlevel 1 goto use_python
echo Python 3.8 or later must already be installed. No software was downloaded.
exit /b 2

:use_py
py.exe -3 "%~dp0prepare_connected_release.py" --source "%vale_source%" --contract "%vale_contract%" --contract-sha256 "%vale_contract_sha256%" --export-root "%vale_destination%"
set "vale_result=%ERRORLEVEL%"
goto finished

:use_python
python.exe "%~dp0prepare_connected_release.py" --source "%vale_source%" --contract "%vale_contract%" --contract-sha256 "%vale_contract_sha256%" --export-root "%vale_destination%"
set "vale_result=%ERRORLEVEL%"
goto finished

:finished
if not "%vale_result%"=="0" echo Preparation stopped. Keep incomplete output for inspection; do not upload it.
if "%vale_result%"=="0" echo Candidate bytes are verified. World, client and hosted runtime checks remain pending.
exit /b %vale_result%

:missing_contract
echo The reviewed contract file is missing.
exit /b 2

:missing_tools
echo Keep Resume-Vale.cmd and the four Python tools in the same tools folder.
exit /b 2

:usage
echo Usage: Resume-Vale.cmd REVIEWED_CONTRACT_JSON REVIEWED_SHA256 [COMPLETE_SOURCE_DIRECTORY] [VALE_EXPORT_DIRECTORY]
echo Defaults: actual-bisect-server-stage and D:\Codex\Vale-Export.
echo This command creates a new candidate and leaves the source unchanged. Keep the source server stopped.
exit /b 2
