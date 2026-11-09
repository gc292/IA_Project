param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
$jar = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/jar.exe' } else { 'jar' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
if (Test-Path build/classes) { Remove-Item build/classes -Recurse -Force }
New-Item -ItemType Directory -Force build/classes | Out-Null
$sources = Get-ChildItem src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
& $javac --release 17 -encoding UTF-8 -d build/classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force build/classes/models | Out-Null
Get-ChildItem models -Filter '*.nn' | Copy-Item -Destination build/classes/models
& $jar --create --file build/ia-arcade.jar --main-class arcade.Launcher -C build/classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Built: build/ia-arcade.jar'
if ($Test) {
    New-Item -ItemType Directory -Force build/test-classes | Out-Null
    $tests = Get-ChildItem tests -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
    & $javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes $tests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $java -Djava.awt.headless=true -cp 'build/classes;build/test-classes' ArcadeTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $java -Djava.awt.headless=true -cp 'build/classes;build/test-classes' NeuralTests
    exit $LASTEXITCODE
}
