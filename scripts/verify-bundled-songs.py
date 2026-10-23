"""Validate native lessons and the user's five retained song packages."""
import importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('native_audit',Path(__file__).with_name('audit-native-teaching.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
native_audit=module.audit
spec=importlib.util.spec_from_file_location('retained_audit',Path(__file__).with_name('audit-retained-songs.py'))
retained=importlib.util.module_from_spec(spec);spec.loader.exec_module(retained)
def audit():
    report=native_audit()+retained.audit()
    actual={p.name for p in module.ASSETS.glob('*.gpractice')}
    assert actual=={r['file'] for r in report},'Unexpected or missing bundled song package'
    return report
if __name__=='__main__':print(json.dumps(audit(),ensure_ascii=False,indent=2))
