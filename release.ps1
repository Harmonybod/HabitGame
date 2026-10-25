<#
Publishes a new HabitGame version on GitHub Releases. Phones with HabitGame then show
"HabitGame X.Y is out" on Home and update with one tap.

Before running: raise versionCode and versionName in app/build.gradle.kts and commit your changes.

    .\release.ps1 -Notes "What's new in this version"

It runs the tests, builds the signed release APK, tags the commit vX.Y, pushes, and creates the
GitHub release with the APK attached. It signs in to GitHub with the login git already uses.
#>
param(
    [Parameter(Mandatory = $true)][string]$Notes
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$repo = "Harmonybod/HabitGame"

# Version from the build file
$gradleFile = Get-Content "app\build.gradle.kts" -Raw
$version = [regex]::Match($gradleFile, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $version) { throw "Couldn't find versionName in app/build.gradle.kts" }
$tag = "v$version"

if (git status --porcelain) { throw "There are uncommitted changes. Commit them first." }
if (git tag --list $tag) { throw "$tag already exists. Raise versionName (and versionCode) in app/build.gradle.kts first." }

# Tests and the signed release APK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = (Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin\*\gradle-8.13\bin\gradle.bat" | Select-Object -First 1).FullName
& $gradle :app:testDebugUnitTest :app:assembleRelease -x lintVitalRelease -q
if ($LASTEXITCODE -ne 0) { throw "Build or tests failed; nothing was published." }
$apk = "app\build\outputs\apk\release\app-release.apk"
$apkName = "HabitGame-$version.apk"
Copy-Item $apk "..\$apkName" -Force

# Tag and push
git tag -a $tag -m "HabitGame $version"
git push origin main
git push origin $tag

# GitHub login from git's credential manager (never printed)
$cred = "protocol=https`nhost=github.com`n`n" | git credential fill
$token = ($cred | Where-Object { $_ -like "password=*" }) -replace '^password=', ''
if (-not $token) { throw "No GitHub login saved in git. Run 'git push' once to sign in, then try again." }
$headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json"; "X-GitHub-Api-Version" = "2022-11-28" }

$body = @{ tag_name = $tag; name = "HabitGame $version"; body = $Notes; draft = $false; prerelease = $false } | ConvertTo-Json
$release = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType "application/json; charset=utf-8"

$uploadUrl = $release.upload_url -replace '\{.*\}$', ''
Invoke-RestMethod -Method Post -Uri "$uploadUrl`?name=$apkName" -Headers $headers -InFile $apk -ContentType "application/vnd.android.package-archive" | Out-Null

Write-Host "Published $tag with $apkName"
Write-Host $release.html_url
