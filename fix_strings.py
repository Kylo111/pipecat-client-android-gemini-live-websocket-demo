
import os
import re

def escape_xml_string(content):
    # This is a bit tricky because we want to preserve already escaped ones
    # and not escape quotes in tags
    
    # We'll split by strings
    parts = re.split(r'(<string[^>]*>)(.*?)(</string>)', content, flags=re.DOTALL)
    
    new_parts = []
    for i in range(len(parts)):
        if i % 4 == 2: # This is the content part
            s = parts[i]
            # Escape " if not preceded by \
            s = re.sub(r'(?<!\\)"', r'\"', s)
            # Escape ' if not preceded by \
            s = re.sub(r"(?<!\\)'", r"\'", s)
            new_parts.append(s)
        else:
            new_parts.append(parts[i])
            
    return "".join(new_parts)

files = [
    r"gemini-multimodal-websocket-demo\src\main\res\values\strings.xml",
    r"gemini-multimodal-websocket-demo\src\main\res\values-en\strings.xml",
    r"gemini-multimodal-websocket-demo\src\main\res\values-de\strings.xml",
    r"gemini-multimodal-websocket-demo\src\main\res\values-fr\strings.xml",
    r"gemini-multimodal-websocket-demo\src\main\res\values-es\strings.xml",
    r"gemini-multimodal-websocket-demo\src\main\res\values-uk\strings.xml",
]

for f in files:
    full_path = os.path.join(r"c:\Users\Komp\Live-bot\Live-bot\pipecat-client-android-gemini-live-websocket-demo", f)
    if os.path.exists(full_path):
        with open(full_path, 'r', encoding='utf-8') as file:
            content = file.read()
        
        new_content = escape_xml_string(content)
        
        if new_content != content:
            with open(full_path, 'w', encoding='utf-8') as file:
                file.write(new_content)
            print(f"Fixed {f}")
        else:
            print(f"No changes needed for {f}")
    else:
        print(f"File not found: {f}")
