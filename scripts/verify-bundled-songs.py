"""Validate the shipped native teaching library and demonstration packages."""
import importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('native_audit',Path(__file__).with_name('audit-native-teaching.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
audit=module.audit
if __name__=='__main__':print(json.dumps(audit(),ensure_ascii=False,indent=2))
