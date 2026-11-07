[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Version,

    [switch]$NoPush
)

$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
    throw "版本号必须是 major.minor.patch 格式，例如 1.0.1"
}

$segments = $Version.Split('.')
$minor = [int]$segments[1]
$patch = [int]$segments[2]
if ($minor -gt 99 -or $patch -gt 99) {
    throw "minor 与 patch 必须小于 100（versionCode = major*10000 + minor*100 + patch）"
}
$versionCode = [int]$segments[0] * 10000 + $minor * 100 + $patch

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repoRoot
try {
    $gradleFile = Join-Path $repoRoot 'app/build.gradle.kts'
    $pattern = 'val appVersionName = "\d+\.\d+\.\d+"'
    $content = [IO.File]::ReadAllText($gradleFile, [Text.Encoding]::UTF8)
    if ($content -notmatch $pattern) {
        throw "未在 app/build.gradle.kts 中找到 val appVersionName，无法更新版本号"
    }

    $branch = (git rev-parse --abbrev-ref HEAD).Trim()
    if ($branch -ne 'master') {
        throw "当前分支是 $branch，请在 master 分支上发版"
    }

    if (git status --porcelain) {
        throw "工作区有未提交的改动，请先提交或暂存后再发版"
    }

    $tag = "v$Version"
    git rev-parse -q --verify "refs/tags/$tag" *> $null
    if ($LASTEXITCODE -eq 0) {
        throw "本地已存在 tag $tag"
    }
    $remoteTag = git ls-remote --tags origin "refs/tags/$tag"
    if ($remoteTag) {
        throw "远端已存在 tag $tag"
    }

    $updated = [regex]::new($pattern).Replace($content, "val appVersionName = `"$Version`"", 1)
    [IO.File]::WriteAllText($gradleFile, $updated, [Text.UTF8Encoding]::new($false))

    Write-Host "版本号已更新为 $Version，versionCode = $versionCode"
    git --no-pager diff -- app/build.gradle.kts

    git add app/build.gradle.kts
    git commit -m "chore: 发布 $tag"
    git tag -a $tag -m "Pulse $tag"

    if ($NoPush) {
        Write-Host "已在本地提交并打 tag $tag，未推送"
        Write-Host "手动推送：git push origin master; git push origin $tag"
        return
    }

    git push origin master
    git push origin $tag

    Write-Host ""
    Write-Host "已推送 $tag，GitHub Actions 正在构建并发布："
    Write-Host "https://github.com/ACG-Q/tts-aggregator-android/actions"
    Write-Host "发布页：https://github.com/ACG-Q/tts-aggregator-android/releases/tag/$tag"
}
finally {
    Pop-Location
}
