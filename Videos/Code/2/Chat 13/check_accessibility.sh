#!/bin/bash
# Check for ImageView/ImageButton without contentDescription

for file in app/src/main/res/layout/*.xml; do
    echo "=== Checking $file ==="
    
    # Extract all ImageView/ImageButton blocks
    grep -A 10 "<ImageView\|<ImageButton\|<FloatingActionButton" "$file" | \
    awk '
    BEGIN { block = "" }
    /^--$/ { 
        if (block != "") {
            if (block !~ /contentDescription/ && block !~ /<\/ImageView>/ && block !~ /<\/ImageButton>/) {
                print block
                print "---"
            }
        }
        block = ""
        next
    }
    { block = block "\n" $0 }
    END {
        if (block != "") {
            if (block !~ /contentDescription/ && block !~ /<\/ImageView>/ && block !~ /<\/ImageButton>/) {
                print block
            }
        }
    }
    '
done
