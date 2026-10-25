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

# Git writes progress to stderr, which Windows PowerShell would treat as an error: judge by the exit code instead
function Invoke-Git {
    $ErrorActionPreference = "Continue"
    & git @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed" }
}

# Version from the build file
$gradleFile = Get-Content "app\build.gradle.kts" -Raw
$version = [regex]::Match($gradleFile, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $version) { throw "Couldn't find versionName in app/build.gradle.kts" }
$tag = "v$version"

if (git status --porcelain) { throw "There are uncommitted changes. Commit them first." }
# A tag on this very commit means an earlier run stopped half way: carry on from there
$tagged = git tag --list $tag
if ($tagged -and (git rev-list -n 1 $tag) -ne (git rev-parse HEAD)) {
    throw "$tag already exists. Raise versionName (and versionCode) in app/build.gradle.kts first."
}

# Tests and the signed release APK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = (Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin\*\gradle-8.13\bin\gradle.bat" | Select-Object -First 1).FullName
& $gradle :app:testDebugUnitTest :app:assembleRelease -x lintVitalRelease -q
if ($LASTEXITCODE -ne 0) { throw "Build or tests failed; nothing was published." }
$apk = "app\build\outputs\apk\release\app-release.apk"
$apkName = "HabitGame-$version.apk"
Copy-Item $apk "..\$apkName" -Force

# Tag and push
if (-not $tagged) { Invoke-Git tag -a $tag -m "HabitGame $version" }
Invoke-Git push origin main
Invoke-Git push origin $tag

# GitHub login from git's credential manager (never printed). Given to git from a file with plain
# line endings: Windows PowerShell's pipe changes them and git then can't read the request.
$request = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllText($request, "protocol=https`nhost=github.com`n`n")
$cred = cmd /c "git credential fill < `"$request`""
Remove-Item $request
$token = ($cred | Where-Object { $_ -like "password=*" }) -replace '^password=', ''
if (-not $token) { throw "No GitHub login saved in git. Run 'git push' once to sign in, then try again." }
$headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json"; "X-GitHub-Api-Version" = "2022-11-28" }

$body = @{ tag_name = $tag; name = "HabitGame $version"; body = $Notes; draft = $false; prerelease = $false } | ConvertTo-Json
$release = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType "application/json; charset=utf-8"

$uploadUrl = $release.upload_url -replace '\{.*\}$', ''
Invoke-RestMethod -Method Post -Uri "$uploadUrl`?name=$apkName" -Headers $headers -InFile $apk -ContentType "application/vnd.android.package-archive" | Out-Null

Write-Host "Published $tag with $apkName"
Write-Host $release.html_url
