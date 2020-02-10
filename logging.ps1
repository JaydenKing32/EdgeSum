$serial = $Args[0]
$verbosity = $Args[1]

adb -s $serial logcat --pid ((adb -s $serial shell ps | findstr com.example.edgesum) -split "\s+")[1] *:$verbosity
