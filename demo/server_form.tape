Set FontSize 18
Set Width 1560
Set Height 880
Set Padding 28
Set Theme "Dracula"
Set TypingSpeed 30ms
Type "cd ~/pocs/a2ui-android-e2e/server"
Enter
Sleep 300ms
Type "sed -n '/^def form_messages/,/^    ]/p' server.py | head -42"
Sleep 500ms
Enter
Sleep 18s
