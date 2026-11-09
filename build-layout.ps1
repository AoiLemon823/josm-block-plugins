$ErrorActionPreference='Stop'
$Root=Split-Path -Parent $MyInvocation.MyCommand.Path
$JOSM='C:\Users\AoiLemon\AppData\Local\JOSM\app\josm-custom.jar'
$JavaHome=Join-Path $Root 'tools\zulu21.54.109-ca-jdk21.0.13.0.3-win_x64'
$Java=Join-Path $JavaHome 'bin\java.exe';$Javac=Join-Path $JavaHome 'bin\javac.exe';$Jar=Join-Path $JavaHome 'bin\jar.exe'
foreach($p in @($JOSM,$Java,$Javac,$Jar)){if(!(Test-Path -LiteralPath $p)){throw "Required file not found: $p"}}
$Build=Join-Path $Root 'build-layout';$Classes=Join-Path $Build 'classes';$Tests=Join-Path $Build 'test-classes';$Dist=Join-Path $Root 'dist'
New-Item -ItemType Directory -Force -Path $Classes,$Tests,$Dist | Out-Null
# Isolated output only. Never read, delete, or overwrite legacy build/classes or BlockFill artifacts.
Remove-Item -LiteralPath $Classes,$Tests -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes,$Tests | Out-Null
$Sources=@(Get-ChildItem -LiteralPath (Join-Path $Root 'src\org\openstreetmap\josm\plugins\blocklayout') -Filter '*.java' -File -Recurse | ForEach-Object FullName)
if($Sources.Count -eq 0){throw 'No standalone layout plugin source files found.'}
& $Javac -encoding UTF-8 --release 17 -Xlint:all,-serial -Werror -cp $JOSM -d $Classes $Sources
if($LASTEXITCODE -ne 0){throw 'Standalone plugin compilation failed.'}
$TestSources=@(Get-ChildItem -LiteralPath (Join-Path $Root 'test\org\openstreetmap\josm\plugins\blocklayout') -Filter '*.java' -File -Recurse | ForEach-Object FullName)
& $Javac -encoding UTF-8 --release 17 -Xlint:all,-serial -Werror -cp "$JOSM;$Classes" -d $Tests $TestSources
if($LASTEXITCODE -ne 0){throw 'Standalone test compilation failed.'}
& $Java -cp "$Classes;$Tests" org.openstreetmap.josm.plugins.blocklayout.LayoutGeometryTest
if($LASTEXITCODE -ne 0){throw 'Geometry test failed.'}
& $Java '-Djava.awt.headless=true' -cp "$JOSM;$Classes;$Tests" org.openstreetmap.josm.plugins.blocklayout.LayoutUndoIntegrationTest
if($LASTEXITCODE -ne 0){throw 'JOSM undo integration test failed.'}
$ResourceRoot=Join-Path $Build 'josm-resources';New-Item -ItemType Directory -Force -Path $ResourceRoot | Out-Null
Push-Location $ResourceRoot
try { & $Jar --extract --file $JOSM; if($LASTEXITCODE -ne 0){throw 'Could not extract JOSM resources for UI test.'} } finally { Pop-Location }
& $Java '-Djava.awt.headless=true' -cp "$JOSM;$Classes;$Tests" org.openstreetmap.josm.plugins.blocklayout.LayoutDuplicateNodeTest
if($LASTEXITCODE -ne 0){throw 'Duplicate-node regression test failed.'}
& $Java '-Djava.awt.headless=true' '-Dblocklayout.testPreferencePrefix=blocklayout.test.' -cp "$JOSM;$Classes;$Tests;$ResourceRoot" org.openstreetmap.josm.plugins.blocklayout.LayoutUiStartupTest
if($LASTEXITCODE -ne 0){throw 'UI startup test failed.'}
$Manifest=@'
Manifest-Version: 1.0
Plugin-Mainversion: 19555
Plugin-Class: org.openstreetmap.josm.plugins.blocklayout.BlockLayoutPlugin
Plugin-Name: JOSM 住宅楼排布
Plugin-Version: 1.0.0
Plugin-Description: 在闭合地块内预览并生成住宅楼、社区底商和工业厂房，支持日照间距、样板平铺和可撤销批量创建
Plugin-Author: AoiLemon
Plugin-Link: https://github.com/AoiLemon/josm-block-plugins
Plugin-Minimum-Java-Version: 17

'@
$ManifestPath=Join-Path $Build 'MANIFEST.MF';[IO.File]::WriteAllText($ManifestPath,$Manifest,[Text.UTF8Encoding]::new($false))
$Output=Join-Path $Dist 'josm-block-layout.jar'
& $Jar --create --file $Output --manifest $ManifestPath -C $Classes .
if($LASTEXITCODE -ne 0){throw 'Standalone JAR packaging failed.'}
Write-Host "Built standalone plugin: $Output"
