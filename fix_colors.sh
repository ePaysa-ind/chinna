#!/bin/bash

# Fix colors in fragment_smart_advisory.xml
sed -i 's/@color\/background/@color\/dark_background/g' app/src/main/res/layout/fragment_smart_advisory.xml
sed -i 's/@color\/surface/@color\/dark_surface/g' app/src/main/res/layout/fragment_smart_advisory.xml
sed -i 's/@color\/text_primary/@color\/dark_text_primary/g' app/src/main/res/layout/fragment_smart_advisory.xml
sed -i 's/@color\/text_secondary/@color\/dark_text_secondary/g' app/src/main/res/layout/fragment_smart_advisory.xml

# Find and fix any teal_200 references in all layout files
find app/src/main/res/layout -name "*.xml" -exec sed -i 's/@color\/teal_200/@color\/dark_accent/g' {} \;

echo "Color references fixed!"