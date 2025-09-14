@echo off

echo Building Go services...

REM Build all services
echo Building orders service...
go build -o bin\orders.exe .\cmd\orders

echo Building payments service...
go build -o bin\payments.exe .\cmd\payments

echo Building reserve service...
go build -o bin\reserve.exe .\cmd\reserve

echo Building tpc service...
go build -o bin\tpc.exe .\cmd\tpc

echo All services built successfully!

REM Create directory for binaries if it doesn't exist
if not exist bin mkdir bin

echo.
echo To run the services:
echo 1. Start PostgreSQL database (or use docker-compose up postgres)
echo 2. Run each service:
echo    bin\orders.exe
echo    bin\payments.exe
echo    bin\reserve.exe
echo    bin\tpc.exe
echo.
echo Or use Docker Compose:
echo    docker-compose up --build