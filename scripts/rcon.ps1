param(
    [string]$Host_ = "127.0.0.1",
    [int]$Port = 25575,
    [string]$Password = "aicoretest",
    [string[]]$Commands = @("list")
)

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Host_, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 5000

function Send-Packet([int]$id, [int]$type, [string]$body) {
    $payload = [System.Text.Encoding]::ASCII.GetBytes($body)
    $length = 4 + 4 + $payload.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int]$length)
    $bw.Write([int]$id)
    $bw.Write([int]$type)
    $bw.Write($payload)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
    $bytes = $ms.ToArray()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
}

function Read-Packet {
    $hdr = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $stream.Read($hdr, $read, 4 - $read)
        if ($n -le 0) { throw "connection closed" }
        $read += $n
    }
    $len = [BitConverter]::ToInt32($hdr, 0)
    if ($len -lt 10) { throw "bad length $len" }
    $buf = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $stream.Read($buf, $read, $len - $read)
        if ($n -le 0) { throw "connection closed" }
        $read += $n
    }
    [pscustomobject]@{
        Id   = [BitConverter]::ToInt32($buf, 0)
        Type = [BitConverter]::ToInt32($buf, 4)
        Body = [System.Text.Encoding]::ASCII.GetString($buf, 8, $len - 10)
        Raw  = $buf
    }
}

Send-Packet 1 3 $Password
$auth = Read-Packet
if ($auth.Id -eq -1) { throw "auth failed" }
"--- auth ok ---"

$i = 2
foreach ($cmd in $Commands) {
    Send-Packet $i 2 $cmd
    $resp = Read-Packet
    # Some responses arrive as several packets for long output; drain what is buffered.
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append($resp.Body)
    while ($stream.DataAvailable) {
        $more = Read-Packet
        [void]$sb.Append($more.Body)
    }
    "--- $cmd"
    $sb.ToString().Trim()
    $i++
}

$client.Close()
