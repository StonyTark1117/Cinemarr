@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-client.ps1" -MediaRoot "%~dp0" -QueueOnly
