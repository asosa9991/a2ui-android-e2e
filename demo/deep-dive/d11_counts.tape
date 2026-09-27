Set FontSize 17
Set Width 1600
Set Height 900
Set Padding 26
Set Theme "Dracula"
Set TypingSpeed 26ms
Type "cd /private/tmp/claude-501/-Users-vijayakella-pocs-a2ui/16a34f75-c4b7-4e67-b065-1afbdace1d10/scratchpad/aar && for j in */classes.jar; do printf '%-32s %s types\n' $(dirname $j) $(unzip -l $j | awk '{print $4}' | grep '[.]class$' | grep -v '\$' | wc -l); done"
Sleep 400ms
Enter
Sleep 34s
