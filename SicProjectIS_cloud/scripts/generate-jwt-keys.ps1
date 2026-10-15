param(
    [string]$OutputDirectory = "./secrets"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$privatePem = Join-Path $OutputDirectory "jwt-private.pem"
$publicPem = Join-Path $OutputDirectory "jwt-public.pem"
$privateDer = Join-Path $OutputDirectory "jwt-private.der"
$publicDer = Join-Path $OutputDirectory "jwt-public.der"

openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $privatePem
openssl pkey -in $privatePem -pubout -out $publicPem
openssl pkcs8 -topk8 -inform PEM -outform DER -in $privatePem -nocrypt -out $privateDer
openssl pkey -pubin -in $publicPem -outform DER -out $publicDer

$privateBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $privateDer)))
$publicBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $publicDer)))
Set-Content -LiteralPath (Join-Path $OutputDirectory "jwt-private.base64") `
    -Value $privateBase64 -Encoding ascii -NoNewline
Set-Content -LiteralPath (Join-Path $OutputDirectory "jwt-public.base64") `
    -Value $publicBase64 -Encoding ascii -NoNewline

Write-Host "Keys generated under $OutputDirectory. Copy the two .base64 values into your local .env only."
