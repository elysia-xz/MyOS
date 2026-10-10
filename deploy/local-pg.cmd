@echo off
rem ============================================================
rem  MyOS local PostgreSQL (portable, no installer) - START
rem
rem  Usage: just double-click this file.
rem         (or run it from any terminal: cmd /c deploy\local-pg.cmd)
rem
rem  What it does:
rem    1) initdb  -> create the data directory (only on first run)
rem    2) pg_ctl  -> start the server on localhost:5432
rem    3) create role myos / myos123 (superuser, local dev only)
rem    4) create database myos
rem
rem  Safe to run repeatedly: every step is skipped if already done.
rem ============================================================
setlocal

if "%MYOS_PG_ROOT%"=="" (
  set "PGROOT=C:\Users\Lenovo\.workbuddy-ai\binaries\postgres"
) else (
  set "PGROOT=%MYOS_PG_ROOT%"
)
set "PGBIN=%PGROOT%\pgsql\bin"
set "PGDATA=%PGROOT%\data"
set "PGLOG=%PGROOT%\pg.log"
set "PGPORT=5432"
set "DBNAME=myos"
set "DBUSER=myos"
set "DBPASSWORD=myos123"

if not exist "%PGBIN%\pg_ctl.exe" (
  echo [ERROR] PostgreSQL not found: %PGBIN%\pg_ctl.exe
  echo         Set MYOS_PG_ROOT if it lives somewhere else.
  pause
  exit /b 1
)

rem ---------- 1) initdb ----------
if not exist "%PGDATA%\PG_VERSION" (
  echo ==^> initdb: %PGDATA%
  if not exist "%PGROOT%" mkdir "%PGROOT%"
  "%PGBIN%\initdb" -D "%PGDATA%" -U postgres -E UTF8 --locale=C -A trust
  if not exist "%PGDATA%\PG_VERSION" (
    echo.
    echo [ERROR] initdb failed. Nothing was created.
    pause
    exit /b 1
  )
) else (
  echo ==^> data dir already exists, skip initdb
)

rem ---------- 2) start server ----------
"%PGBIN%\pg_isready" -h localhost -p %PGPORT% >nul 2>&1
if errorlevel 1 (
  echo ==^> starting PostgreSQL on port %PGPORT% ^(localhost only^)
  "%PGBIN%\pg_ctl" -D "%PGDATA%" -l "%PGLOG%" -o "-p %PGPORT% -c listen_addresses=localhost" -w start
  if errorlevel 1 (
    echo.
    echo [ERROR] start failed. See %PGLOG%
    pause
    exit /b 1
  )
) else (
  echo ==^> PostgreSQL already running
)

rem ---------- 3) role ----------
set "ROLE_EXISTS="
for /f %%i in ('"%PGBIN%\psql" -h localhost -p %PGPORT% -U postgres -tAc "select 1 from pg_roles where rolname='%DBUSER%'" 2^>nul') do set "ROLE_EXISTS=%%i"
if "%ROLE_EXISTS%"=="1" (
  echo ==^> role %DBUSER% exists
) else (
  "%PGBIN%\psql" -h localhost -p %PGPORT% -U postgres -q -c "create role %DBUSER% login password '%DBPASSWORD%' superuser"
  if errorlevel 1 (
    echo [ERROR] create role %DBUSER% failed
    pause
    exit /b 1
  )
  echo ==^> role %DBUSER% created
)

rem ---------- 4) database ----------
set "DB_EXISTS="
for /f %%i in ('"%PGBIN%\psql" -h localhost -p %PGPORT% -U postgres -tAc "select 1 from pg_database where datname='%DBNAME%'" 2^>nul') do set "DB_EXISTS=%%i"
if "%DB_EXISTS%"=="1" (
  echo ==^> database %DBNAME% exists
) else (
  "%PGBIN%\createdb" -h localhost -p %PGPORT% -U postgres -O %DBUSER% %DBNAME%
  if errorlevel 1 (
    echo [ERROR] create database %DBNAME% failed
    pause
    exit /b 1
  )
  echo ==^> database %DBNAME% created
)

echo.
echo [OK] PostgreSQL ready: localhost:%PGPORT%/%DBNAME%
echo      user=%DBUSER%  password=%DBPASSWORD%
echo.
echo Tell the assistant "done" - it takes over from here.
echo.
pause
endlocal
