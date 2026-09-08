param(
    [string]$GradleUserHome = $env:GRADLE_USER_HOME
)

$ErrorActionPreference = 'Stop'
if (-not $GradleUserHome) { $GradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
$version = '2.2.2-compose-1.12-SNAPSHOT'
$cache = Join-Path $GradleUserHome 'caches/modules-2/files-2.1/io.github.alexzhirkevich'
$repo = Join-Path $PSScriptRoot '../../.gradle/compottie-maven/io/github/alexzhirkevich'
$modules = 'compottie', 'compottie-core', 'compottie-dot', 'compottie-network', 'compottie-network-core', 'compottie-resources'

# This cache contains Android binaries and root variant metadata, not iOS/JVM
# binaries or common metadata jars. Copy originals; never synthesize variants.
$copies = @()
foreach ($module in $modules) {
    foreach ($artifact in $module, "$module-android") {
        $source = Join-Path $cache "$artifact/$version"
        $files = @(Get-ChildItem -LiteralPath $source -Recurse -File)
        foreach ($extension in @('.pom', '.module') + $(if ($artifact.EndsWith('-android')) { '.aar' })) {
            $matches = @($files | Where-Object Extension -eq $extension)
            if ($matches.Count -ne 1) { throw "Expected exactly one $extension for $artifact; found $($matches.Count)" }
            $file = $matches[0]
            $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA1).Hash.ToLowerInvariant()
            if ($hash -ne $file.Directory.Name.PadLeft(40, '0')) { throw "Cache checksum mismatch: $($file.FullName)" }
            $copies += [PSCustomObject]@{ Source = $file.FullName; Destination = (Join-Path $repo "$artifact/$version/$($file.Name)"); Hash = $hash }
        }
        if ($artifact.EndsWith('-android')) {
            $metadata = Get-Content -LiteralPath ($files | Where-Object Extension -eq '.module').FullName -Raw | ConvertFrom-Json
            $aar = $files | Where-Object Extension -eq '.aar'
            foreach ($variant in $metadata.variants) {
                foreach ($entry in $variant.files | Where-Object url -like '*.aar') {
                    if ($entry.url -ne $aar.Name -or $entry.size -ne $aar.Length -or
                        $entry.sha256 -ne (Get-FileHash -LiteralPath $aar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()) {
                        throw "AAR does not match original Gradle metadata: $artifact"
                    }
                }
            }
        }
    }
}

# Validate every input before writing, and refuse to replace different contents.
foreach ($copy in $copies) {
    if ((Test-Path -LiteralPath $copy.Destination) -and
        (Get-FileHash -LiteralPath $copy.Destination -Algorithm SHA1).Hash.ToLowerInvariant() -ne $copy.Hash) {
        throw "Conflicting local artifact: $($copy.Destination)"
    }
}
foreach ($copy in $copies) {
    New-Item -ItemType Directory -Path (Split-Path $copy.Destination) -Force | Out-Null
    Copy-Item -LiteralPath $copy.Source -Destination $copy.Destination
}
Write-Host "Restored $($copies.Count) checksum-verified files to $repo"
