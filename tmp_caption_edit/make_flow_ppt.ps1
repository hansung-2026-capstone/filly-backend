$ErrorActionPreference = 'Stop'

$base = 'C:\Users\777\2026\filly-backend\tmp_caption_edit'
$input = Get-ChildItem -LiteralPath $base -Filter '*.pptx' |
    Where-Object { $_.Name -notlike 'flow_modified*' } |
    Select-Object -First 1 -ExpandProperty FullName
$outPptx = Join-Path $base 'flow_modified.pptx'
$zipOutput = Join-Path $base 'flow_modified.zip'

if ($null -eq $input) {
    throw 'Source PPTX not found.'
}

Copy-Item -LiteralPath $input -Destination $outPptx -Force

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function New-TextRun {
    param(
        [string] $Text,
        [string] $Size = '2200',
        [string] $Color = '111827',
        [string] $Bold = '0'
    )

    $boldAttr = if ($Bold -eq '1') { ' b="1"' } else { '' }
    return "<a:r><a:rPr lang=""ko-KR"" sz=""$Size""$boldAttr><a:solidFill><a:srgbClr val=""$Color""/></a:solidFill><a:latin typeface=""Malgun Gothic""/><a:ea typeface=""Malgun Gothic""/></a:rPr><a:t>$Text</a:t></a:r>"
}

function New-Shape {
    param(
        [int] $Id,
        [string] $Name,
        [long] $X,
        [long] $Y,
        [long] $W,
        [long] $H,
        [string] $Fill,
        [string] $Line,
        [string[]] $Lines,
        [string] $TextColor = '111827',
        [string] $Size = '2200',
        [string] $Bold = '0',
        [string] $Radius = 'roundRect'
    )

    $paragraphs = foreach ($line in $Lines) {
        "<a:p><a:pPr algn=""ctr""/>$(New-TextRun -Text $line -Size $Size -Color $TextColor -Bold $Bold)</a:p>"
    }
    $text = [string]::Join('', $paragraphs)

    return @"
<p:sp><p:nvSpPr><p:cNvPr id="$Id" name="$Name"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="$X" y="$Y"/><a:ext cx="$W" cy="$H"/></a:xfrm><a:prstGeom prst="$Radius"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$Fill"/></a:solidFill><a:ln w="19050"><a:solidFill><a:srgbClr val="$Line"/></a:solidFill></a:ln></p:spPr><p:txBody><a:bodyPr wrap="square" lIns="91440" tIns="45720" rIns="91440" bIns="45720" rtlCol="0" anchor="ctr"><a:spAutoFit/></a:bodyPr><a:lstStyle/>$text</p:txBody></p:sp>
"@
}

function New-Label {
    param(
        [int] $Id,
        [string] $Name,
        [long] $X,
        [long] $Y,
        [long] $W,
        [long] $H,
        [string] $Text,
        [string] $Color,
        [string] $Size = '1700'
    )

    return @"
<p:sp><p:nvSpPr><p:cNvPr id="$Id" name="$Name"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="$X" y="$Y"/><a:ext cx="$W" cy="$H"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr wrap="square" rtlCol="0" anchor="ctr"/><a:lstStyle/><a:p><a:pPr algn="ctr"/>$(New-TextRun -Text $Text -Size $Size -Color $Color -Bold '1')</a:p></p:txBody></p:sp>
"@
}

function New-Arrow {
    param(
        [int] $Id,
        [string] $Name,
        [long] $X,
        [long] $Y,
        [long] $W,
        [string] $Color,
        [string] $Direction
    )

    $arrowEnd = if ($Direction -eq 'left') { '<a:headEnd type="triangle"/>' } else { '<a:tailEnd type="triangle"/>' }
    return @"
<p:cxnSp><p:nvCxnSpPr><p:cNvPr id="$Id" name="$Name"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr><p:spPr><a:xfrm><a:off x="$X" y="$Y"/><a:ext cx="$W" cy="0"/></a:xfrm><a:prstGeom prst="straightConnector1"><a:avLst/></a:prstGeom><a:ln w="38100"><a:solidFill><a:srgbClr val="$Color"/></a:solidFill>$arrowEnd</a:ln></p:spPr><p:style><a:lnRef idx="2"><a:schemeClr val="dk1"/></a:lnRef><a:fillRef idx="0"><a:schemeClr val="dk1"/></a:fillRef><a:effectRef idx="1"><a:schemeClr val="dk1"/></a:effectRef><a:fontRef idx="minor"><a:schemeClr val="tx1"/></a:fontRef></p:style></p:cxnSp>
"@
}

$shapes = @()
$shapes += New-Shape -Id 2 -Name 'UserNextBox' -X 670000 -Y 2450000 -W 1900000 -H 980000 -Fill 'FFFFFF' -Line 'CBD5E1' -Lines @('&#49324;&#50857;&#51088; &#51077;&#47141;', '(Next.js)') -TextColor '111827' -Size '2100' -Bold '1'
$shapes += New-Shape -Id 3 -Name 'SpringBox' -X 3400000 -Y 2450000 -W 1900000 -H 980000 -Fill 'EEF7EA' -Line '74A853' -Lines @('Spring') -TextColor '1F4D1C' -Size '2400' -Bold '1'
$shapes += New-Shape -Id 4 -Name 'FastAPIBox' -X 6130000 -Y 2450000 -W 1900000 -H 980000 -Fill 'EAF7F4' -Line '0B8F79' -Lines @('FastAPI') -TextColor '075E54' -Size '2400' -Bold '1'
$shapes += New-Shape -Id 5 -Name 'LLMBox' -X 8860000 -Y 2450000 -W 2100000 -H 980000 -Fill 'F5F0FF' -Line '7C3AED' -Lines @('LLM MODEL') -TextColor '4C1D95' -Size '2400' -Bold '1'

$shapes += New-Arrow -Id 10 -Name 'RequestArrow1' -X 2670000 -Y 2760000 -W 640000 -Color '2563EB' -Direction 'right'
$shapes += New-Arrow -Id 11 -Name 'RequestArrow2' -X 5400000 -Y 2760000 -W 640000 -Color '2563EB' -Direction 'right'
$shapes += New-Arrow -Id 12 -Name 'RequestArrow3' -X 8130000 -Y 2760000 -W 640000 -Color '2563EB' -Direction 'right'

$shapes += New-Arrow -Id 13 -Name 'ReturnArrow1' -X 2670000 -Y 3150000 -W 640000 -Color '16A34A' -Direction 'left'
$shapes += New-Arrow -Id 14 -Name 'ReturnArrow2' -X 5400000 -Y 3150000 -W 640000 -Color '16A34A' -Direction 'left'
$shapes += New-Arrow -Id 15 -Name 'ReturnArrow3' -X 8130000 -Y 3150000 -W 640000 -Color '16A34A' -Direction 'left'

$shapes += New-Label -Id 20 -Name 'RequestLabel' -X 5070000 -Y 2020000 -W 1800000 -H 320000 -Text '&#50836;&#52397; &#55120;&#47492;' -Color '2563EB'
$shapes += New-Label -Id 21 -Name 'ReturnLabel' -X 5070000 -Y 3550000 -W 1800000 -H 320000 -Text '&#48152;&#54872; &#55120;&#47492;' -Color '16A34A'

$shapes += New-Label -Id 22 -Name 'Title' -X 2400000 -Y 1050000 -W 7400000 -H 560000 -Text '&#49324;&#50857;&#51088; &#51077;&#47141;&#48512;&#53552; LLM &#51025;&#45813;&#44620;&#51648;&#51032; &#52376;&#47532; &#55120;&#47492;' -Color '111827' -Size '2600'
$shapes += New-Label -Id 23 -Name 'Subtitle' -X 2950000 -Y 1600000 -W 6300000 -H 360000 -Text '&#50836;&#52397;&#51008; Next.js &#8594; Spring &#8594; FastAPI &#8594; LLM MODEL, &#48152;&#54872;&#51008; &#50669;&#49692;&#51004;&#47196; &#51204;&#45804;' -Color '475569' -Size '1450'

$shapeXml = [string]::Join('', $shapes)

$slideXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr><p:sp><p:nvSpPr><p:cNvPr id="100" name="Background"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="12192000" cy="6858000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody></p:sp>$shapeXml</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>
"@

$zipModeType = $null
foreach ($assembly in [AppDomain]::CurrentDomain.GetAssemblies()) {
    $candidate = $assembly.GetType('System.IO.Compression.ZipArchiveMode')
    if ($null -ne $candidate) {
        $zipModeType = $candidate
        break
    }
}
if ($null -eq $zipModeType) {
    throw 'System.IO.Compression.ZipArchiveMode type not found'
}
$zipMode = [Enum]::Parse($zipModeType, 'Update')
$fs = [System.IO.File]::Open([string] $outPptx, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
$archive = New-Object System.IO.Compression.ZipArchive($fs, $zipMode)
try {
    $entry = $archive.GetEntry('ppt/slides/slide1.xml')
    if ($null -eq $entry) {
        throw 'ppt/slides/slide1.xml not found'
    }
    $entry.Delete()
    $newEntry = $archive.CreateEntry('ppt/slides/slide1.xml', [System.IO.Compression.CompressionLevel]::Optimal)
    $writer = New-Object System.IO.StreamWriter($newEntry.Open(), (New-Object System.Text.UTF8Encoding($false)))
    try {
        $writer.Write($slideXml)
    } finally {
        $writer.Dispose()
    }
} finally {
    $archive.Dispose()
    $fs.Dispose()
}

if (Test-Path -LiteralPath $zipOutput) {
    Remove-Item -LiteralPath $zipOutput -Force
}
Compress-Archive -LiteralPath $outPptx -DestinationPath $zipOutput

Write-Output $outPptx
Write-Output $zipOutput
