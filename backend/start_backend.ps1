param(
    [string]$BindHost = "0.0.0.0",
    [int]$Port = 8000,
    [switch]$SkipInstall,
    [switch]$SkipDbInit
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$BackendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $BackendDir

function Invoke-HostPython {
    param([string[]]$PythonArgs)

    if ($script:UsePyLauncher) {
        & py -3 @PythonArgs
    } else {
        & python @PythonArgs
    }
}

Write-Host "[backend] Working directory: $BackendDir"

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "[backend] Created .env from .env.example."
    throw "Please edit backend\.env first, then run this script again."
}

$script:UsePyLauncher = $false
try {
    & py -3 --version *> $null
    if ($LASTEXITCODE -eq 0) {
        $script:UsePyLauncher = $true
    }
} catch {
    $script:UsePyLauncher = $false
}

if (-not $script:UsePyLauncher) {
    try {
        & python --version *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "python command failed"
        }
    } catch {
        throw "Python was not found. Install Python 3.11+ or make sure python/py is available in PATH."
    }
}

$VenvPython = Join-Path $BackendDir ".venv\Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    Write-Host "[backend] Creating virtual environment: .venv"
    Invoke-HostPython @("-m", "venv", ".venv")
}

if (-not $SkipInstall) {
    Write-Host "[backend] Installing dependencies from requirements.txt"
    & $VenvPython -m pip install --upgrade pip
    & $VenvPython -m pip install -r requirements.txt
} else {
    Write-Host "[backend] Skipping dependency install"
}

if (-not $SkipDbInit) {
    Write-Host "[backend] Initializing PostgreSQL database"
    & $VenvPython "scripts\init_postgres_db.py"
} else {
    Write-Host "[backend] Skipping database initialization"
}

Write-Host "[backend] Starting uvicorn at http://$BindHost`:$Port"
& $VenvPython -m uvicorn main:app --host $BindHost --port $Port --reload
