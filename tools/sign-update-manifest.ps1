param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][long]$VersionCode,
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][string]$ApkUrl,
    [ValidateSet('stable', 'beta')][string]$Channel = 'stable',
    [string]$Notes = '',
    [string]$PrivateKeyPath = '.secrets/update-signing-private.pem',
    [string]$OutputPath = 'update-manifest.json'
)

$ErrorActionPreference = 'Stop'
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$resolvedKey = (Resolve-Path -LiteralPath $PrivateKeyPath).Path
$sha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()
$canonical = "$VersionCode`n$VersionName`n$ApkUrl`n$sha256`n$Channel"
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("coursetrace-update-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot | Out-Null
try {
    $payloadPath = Join-Path $tempRoot 'payload.txt'
    $signaturePath = Join-Path $tempRoot 'signature.bin'
    [IO.File]::WriteAllText($payloadPath, $canonical, [Text.UTF8Encoding]::new($false))
    & openssl dgst -sha256 -sign $resolvedKey -out $signaturePath $payloadPath
    if ($LASTEXITCODE -ne 0) { throw 'OpenSSL signing failed' }
    $signature = [Convert]::ToBase64String([IO.File]::ReadAllBytes($signaturePath))
    $manifest = [ordered]@{
        versionCode = $VersionCode
        versionName = $VersionName
        apkUrl = $ApkUrl
        sha256 = $sha256
        signature = $signature
        channel = $Channel
        notes = $Notes
    }
    $manifest | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding utf8NoBOM
    Write-Host "Signed update manifest: $OutputPath"
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}
