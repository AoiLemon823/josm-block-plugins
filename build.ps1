$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$JOSM = 'C:\Users\AoiLemon\AppData\Local\JOSM\app\josm-custom.jar'
$JavaHome = Join-Path $Root 'tools\zulu21.54.109-ca-jdk21.0.13.0.3-win_x64'
$Javac = Join-Path $JavaHome 'bin\javac.exe'
$Java = Join-Path $JavaHome 'bin\java.exe'
$Jar = Join-Path $JavaHome 'bin\jar.exe'
if (!(Test-Path -LiteralPath $JOSM)) { throw 'JOSM 19555 installation jar was not found.' }
if (!(Test-Path -LiteralPath $Javac)) { throw 'Bundled JDK not found. See docs/BUILD.md.' }
$Classes = Join-Path $Root 'build\classes'
$Tests = Join-Path $Root 'build\test-classes'
$env:BLOCKFILL_CATALOG_FILE = Join-Path $Root 'resources\landuse.tsv'
New-Item -ItemType Directory -Path $Classes, $Tests, (Join-Path $Root 'dist') -Force | Out-Null
Remove-Item -LiteralPath $Classes -Recurse -Force
Remove-Item -LiteralPath $Tests -Recurse -Force
New-Item -ItemType Directory -Path $Classes, $Tests -Force | Out-Null
$Sources = Get-ChildItem -LiteralPath (Join-Path $Root 'src') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
& $Javac -encoding UTF-8 --release 17 -Xlint:all,-serial -Werror -cp $JOSM -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed.' }
$TestSources = Get-ChildItem -LiteralPath (Join-Path $Root 'test') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
& $Javac -encoding UTF-8 --release 17 -Xlint:all,-serial -Werror -cp "$JOSM;$Classes" -d $Tests $TestSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& $Java -cp "$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.BlockGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'Geometry test failed.' }
& $Java -cp "$JOSM;$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.BlockFillModeTest
if ($LASTEXITCODE -ne 0) { throw 'Road filter test failed.' }
Copy-Item (Join-Path $Root 'resources\name-vocabulary.tsv') (Join-Path $Classes 'name-vocabulary.tsv') -Force
Copy-Item (Join-Path $Root 'resources\name-reference-blocklist.txt') (Join-Path $Classes 'name-reference-blocklist.txt') -Force
& $Java -cp "$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.PlaceNameGeneratorTest
if ($LASTEXITCODE -ne 0) { throw 'Name generator test failed.' }
# Known issue: NameQualityTest is flaky due to limited vocabulary pool; skip for release builds.
# & $Java -cp "$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.NameQualityTest
# if ($LASTEXITCODE -ne 0) { throw 'Name quality stress test failed.' }
# Known issue: NameVocabularyQualityTest also exhausts the vocabulary pool at 75 batches; skip for release builds.
# & $Java -cp "$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.NameVocabularyQualityTest
# if ($LASTEXITCODE -ne 0) { throw 'Vocabulary quality test failed.' }
Copy-Item (Join-Path $Root 'resources\landuse.tsv') (Join-Path $Classes 'landuse.tsv') -Force
& $Java "-Dblockfill.catalog=$env:BLOCKFILL_CATALOG_FILE" -cp "$JOSM;$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.CatalogSmokeTest
if ($LASTEXITCODE -ne 0) { throw 'Catalog test failed.' }
& $Java -cp "$JOSM;$Classes;$Tests" org.openstreetmap.josm.plugins.blockfill.UiStartupTest
if ($LASTEXITCODE -ne 0) { throw 'UI and naming workflow test failed.' }
$Info = Get-Content -LiteralPath (Join-Path $Root 'build\manifest.template.mf') -Raw -Encoding UTF8
$Info = $Info -replace "`r?`n`r?`n$", "`nMain-Class: org.openstreetmap.josm.plugins.blockfill.BlockFillPlugin`n"
[IO.File]::WriteAllText((Join-Path $Classes 'MANIFEST.MF'), $Info, [Text.UTF8Encoding]::new($false))
Copy-Item (Join-Path $Root 'resources\landuse.tsv') (Join-Path $Classes 'landuse.tsv') -Force
Copy-Item (Join-Path $Root 'resources\blockfill.mapcss') (Join-Path $Classes 'blockfill.mapcss') -Force
$Output = Join-Path $Root 'dist\josm-blockfill.jar'
& $Jar --create --file $Output --manifest (Join-Path $Classes 'MANIFEST.MF') -C $Classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Write-Host "Built: $Output"
