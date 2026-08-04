#requires -Version 7
param(
    [ValidateSet(10000, 50000, 100000)]
    [int]$Rows = 10000,
    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [string]$Database = "maimai_dx",
    [string]$DbUser = "root",
    [string]$PasswordEnvVar = "DB_PASSWORD"
)

$ErrorActionPreference = "Stop"

if ($DbHost -notin @("localhost", "127.0.0.1")) {
    throw "Refusing to seed performance data outside local database host: $DbHost"
}

$password = [Environment]::GetEnvironmentVariable($PasswordEnvVar)
if ([string]::IsNullOrEmpty($password)) {
    throw "Environment variable $PasswordEnvVar is not set. The value is required but will not be printed."
}

$mysql = Get-Command mysql -ErrorAction Stop
$sqlPath = (Resolve-Path (Join-Path $PSScriptRoot "seed-performance-data.sql")).Path.Replace("\", "/")

$env:MYSQL_PWD = $password
try {
    "Target database: $DbHost`:$DbPort/$Database, user=$DbUser, password=******"
    "Generating $Rows score_record rows for local performance tests..."
    "SET @target_rows := $Rows; SOURCE $sqlPath;" |
        & $mysql.Source -h $DbHost -P $DbPort -u $DbUser -D $Database --default-character-set=utf8mb4
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}
