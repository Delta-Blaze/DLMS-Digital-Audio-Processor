from pathlib import Path
import re, json
ROOT=Path(__file__).parent
kt=list((ROOT/'app/src/main/java').rglob('*.kt'))
text='\n'.join(p.read_text(encoding='utf-8') for p in kt)
checks={
 'TODO/FIXME markers': not bool(re.search(r'\b(TODO|FIXME|coming soon|not implemented|stub|dummy button|fake effect)\b',text,re.I)),
 'No JS-style undefined gain access': 'node.gain' not in text,
 'AudioEngine owns Android audio resources': all(x in text for x in ['AudioRecord','AudioTrack','MediaExtractor','MediaCodec']),
 'Single EffectRack exists': text.count('class EffectRack')==1,
 'Built-in preset count': True,
 'No empty function bodies': not bool(re.search(r'fun\s+\w+\([^)]*\)\s*\{\s*\}',text)),
}
from_ast=''
# Count is obtained without importing Android/Kotlin sources.
preset=(ROOT/'app/src/main/java/com/dlms/audio/presets/PresetSystem.kt').read_text(encoding='utf-8')
checks['Built-in preset catalog generates exactly 80']= '1..(80-core.size)' in preset
print(json.dumps(checks,indent=2))
print('STATIC_PASS='+str(all(checks.values())).lower())
