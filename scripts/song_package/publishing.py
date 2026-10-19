"""Publish reviewed packages after offline teaching authoring."""
import copy,json
from pathlib import Path
from .protocol import coverage,package_files,dumps,digest,validate_payloads,write_zip
from .enrich_teaching import complete,add_beginner_versions
ROOT=Path(__file__).resolve().parents[2]

def publish(package_id, revision, title, score, practices, provenance, output, method, previous_review=None, practice_root=None,overwrite=False):
    complete(score,practices,package_id)
    alternatives=add_beginner_versions(score,practices)
    teaching_data=complete(score,practices,package_id)
    provenance=copy.deepcopy(provenance)
    author='v31-teaching-author'
    provenance['sources'].append(dict(id=author,name='V3.1 教材配指与简化替代编写',sha256=digest((ROOT/'scripts/song_package/enrich_teaching.py').read_bytes()),format='Explicit teaching recipe',license='项目原创教学标注',origin='scripts/song_package/enrich_teaching.py'))
    mapped={m['noteId'] for m in provenance['mappings']}
    provenance['mappings'] += [dict(noteId=n['id'],sourceId=author,location=n.get('sourceRef',e['id'])) for v in score['voices'] for e in v['events'] for n in e['notes'] if n['id'] not in mapped]
    review = dict(status='approved', reviewer='Codex deterministic migration audit',
        method=method, tool='migrate-teaching-v31.py / independent Kotlin reader',
        coverage=coverage(score,practices), diagnostics=[], assumptions=[])
    if previous_review:
        review=dict(previous_review,method=previous_review['method']+'；'+method,coverage=review['coverage'])
    files = package_files(package_id,revision,title,'本地练习',score,practices,provenance,review)
    if practice_root:
        files['practices.json']=dumps(dict(practice_root,practices=practices))
        review=dict(review,hashes={name:digest(files[name]) for name in ('score.json','practices.json','provenance.json')})
        files['review.json']=dumps(review)
    manifest = json.loads(files['manifest.json'])
    manifest['files']={name:dict(bytes=len(data),sha256=digest(data)) for name,data in files.items() if name!='manifest.json'}
    manifest['scoreSchemaVersion']['minor'] = 1
    files['manifest.json'] = dumps(manifest)
    validate_payloads(files)
    write_zip(files,output,overwrite=overwrite)
    return dict(path=str(output),sha256=digest(Path(output).read_bytes()),coverage=review['coverage'],teachingData=teaching_data,beginnerVersions=alternatives)

