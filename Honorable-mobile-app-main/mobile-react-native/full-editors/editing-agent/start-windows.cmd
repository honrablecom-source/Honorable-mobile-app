@echo off
setlocal
if "%~1"=="" (
  echo Usage: start-windows.cmd installed-ollama-model
  exit /b 1
)
set "NOBLE_EDIT_MODEL=%~1"
echo Open http://127.0.0.1:4181/video/ and select Editing agent.
node "%~dp0..\preview.mjs"
