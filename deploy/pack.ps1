# Build (on Windows) and upload the backend jar + frontend dist to a remote server.
# ---------------------------------------------------------------------------
# Usage (run from the repository root):
#   pwsh -File deploy/pack.ps1 -Server root@1.2.3.4
#   pwsh -File deploy/pack.ps1 -Server root@1.2.3.4 -Identity $HOME\.ssh\mha.pem -SkipFrontend
#   pwsh -File deploy/pack.ps1 -Server root@1.2.3.4 -SkipBackend -NoRestart
#
# Requires: scp / ssh on this machine (Windows 10+ ships the OpenSSH client).
#           The server must have run deploy/deploy.sh once before (dirs, systemd, Nginx).
#
# Notes:
#   * `npm run build` fails inside the DSH sandbox (esbuild spawn EPERM); run this
#     script with a one-shot escalation, or outside the sandbox.
#   * Keep this file ASCII-only: it may be run by Windows PowerShell 5.1, which
#     decodes BOM-less .ps1 files as the ANSI code page and would then garble any
#     non-ASCII text (that is why the messages below are in English).
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Server,   # e.g. root@1.2.3.4
    [string]$Identity = '',                          # ssh private key (optional)
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$NoRestart
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sshOpts = @()
if ($Identity) { $sshOpts += @('-i', $Identity) }

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "    OK  $msg" -ForegroundColor Green }

# ---- backend -------------------------------------------------------------
if (-not $SkipBackend) {
    Step 'Build backend (mvn -B package -DskipTests)'
    Push-Location (Join-Path $root 'backend')
    try {
        & mvn -B package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'mvn package failed (no clean: target has a DENY ACE on this machine)' }
    }
    finally { Pop-Location }

    $jar = Join-Path $root 'backend\target\application-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $jar)) { throw "artifact not found: $jar" }
    Ok ('jar {0:N1} MB' -f ((Get-Item $jar).Length / 1MB))

    Step "Upload jar to ${Server}:/opt/mha/backend/app.jar.new"
    & scp @sshOpts $jar "${Server}:/opt/mha/backend/app.jar.new"
    if ($LASTEXITCODE -ne 0) { throw 'scp failed' }

    if (-not $NoRestart) {
        Step 'Swap jar and restart the mha systemd service'
        $remote = 'cd /opt/mha/backend && cp -f app.jar app.jar.bak 2>/dev/null; mv -f app.jar.new app.jar && chown mha:mha app.jar && systemctl restart mha && sleep 3 && systemctl is-active mha'
        & ssh @sshOpts $Server $remote
        if ($LASTEXITCODE -ne 0) { throw 'restart failed, check: journalctl -u mha -n 100' }
        Ok 'mha restarted'
    }
    else {
        Ok 'uploaded as app.jar.new (no restart; do it manually: mv app.jar.new app.jar && systemctl restart mha)'
    }
}

# ---- frontend ------------------------------------------------------------
if (-not $SkipFrontend) {
    Step 'Build frontend (npm ci && npm run build)'
    Push-Location (Join-Path $root 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) { & npm ci } else { Write-Host '    (node_modules present, skipping npm ci)' }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'npm run build failed (esbuild spawn EPERM inside the sandbox -> escalate)' }
    }
    finally { Pop-Location }

    $dist = Join-Path $root 'frontend\dist'
    if (-not (Test-Path $dist)) { throw "artifact not found: $dist" }
    Ok ("dist ready ({0} files)" -f (Get-ChildItem $dist -Recurse -File).Count)

    Step "Upload dist to ${Server}:/var/www/mental-health/dist/"
    & ssh @sshOpts $Server 'rm -rf /tmp/mha-dist && mkdir -p /tmp/mha-dist'
    & scp @sshOpts -r (Join-Path $dist '*') "${Server}:/tmp/mha-dist/"
    if ($LASTEXITCODE -ne 0) { throw 'scp failed' }
    & ssh @sshOpts $Server 'cp -r /tmp/mha-dist/. /var/www/mental-health/dist/ && chown -R mha:mha /var/www/mental-health/dist && rm -rf /tmp/mha-dist && echo dist-updated'
    if ($LASTEXITCODE -ne 0) { throw 'remote dist swap failed' }
    Ok 'frontend updated (static files, no Nginx restart needed)'
}

Step 'Done'
Write-Host 'Self-check: curl -s http://<server>/api/test ; then log in and send a chat message to confirm it streams token by token' -ForegroundColor Yellow
