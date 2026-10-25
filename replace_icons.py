import os
import re

pattern = re.compile(r'Icons\.(?:Default|Filled|Outlined)\.(\w+)')
import_icon_prov = 'import com.habitgame.ui.IconProvider\n'
import_local_pack = 'import com.habitgame.ui.LocalIconPack\n'

for root, _, files in os.walk('app/src/main/java'):
    for f in files:
        if f.endswith('.kt') and 'IconProvider.kt' not in f and 'Theme.kt' not in f:
            filepath = os.path.join(root, f)
            with open(filepath, 'r', encoding='utf-8') as file:
                content = file.read()
            
            if pattern.search(content):
                new_content = pattern.sub(r'IconProvider.generalIcon("\1", LocalIconPack.current)', content)
                
                if 'com.habitgame.ui.IconProvider' not in new_content:
                    new_content = new_content.replace('import androidx.compose', import_icon_prov + 'import androidx.compose', 1)
                if 'com.habitgame.ui.LocalIconPack' not in new_content:
                    new_content = new_content.replace('import androidx.compose', import_local_pack + 'import androidx.compose', 1)
                
                with open(filepath, 'w', encoding='utf-8') as file:
                    file.write(new_content)
                print(f'Updated {f}')
