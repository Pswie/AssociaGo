"""Exercise the actual gateway image on the dedicated local PostgreSQL sandbox.

No public ports are opened. Never accepts remote database coordinates.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import secrets
import subprocess
import time
from urllib.parse import quote
import uuid


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sandbox-directory', type=Path, required=True)
    parser.add_argument('--build-directory', type=Path, required=True)
    args = parser.parse_args()
    sandbox = args.sandbox_directory.resolve()
    database = json.loads((sandbox / 'connection.json').read_text())
    state = json.loads((sandbox / 'state.json').read_text())
    build = json.loads((args.build_directory / 'state.json').read_text())
    if (database['host'] != '127.0.0.1' or database['database'] != 'suite_validation'
            or not state['container_name'].startswith('codex-supabase-pg-')
            or state['stage'] != 'ready' or build['status'] != 'succeeded'):
        raise RuntimeError('Only a ready dedicated local sandbox and a successful image are allowed')
    password = json.loads((sandbox / 'roles.json').read_text())['bandi_portal_app']
    suite_id, project_id = str(uuid.uuid4()), str(uuid.uuid4())
    environment = args.build_directory / 'smoke.env'
    environment.write_text('\n'.join([
        'BANDI_MODE=gateway',
        'COLLAB_DATABASE_URL=postgresql://bandi_portal_app:' + quote(password, safe='')
        + '@localhost:5432/suite_validation',
        'COLLAB_CLERK_ISSUER=https://synthetic.clerk.accounts.dev',
        'COLLAB_OWNER_SUBJECT=user_image_smoke', 'COLLAB_NODE_ID=image-smoke',
        'COLLAB_NODE_KEY=' + secrets.token_urlsafe(32),
        'COLLAB_CRON_KEY=' + secrets.token_urlsafe(32), 'AGNO_ENABLED=false', '',
        'SUITE_ENABLED=true', 'SUITE_ORGANIZATION_ID=' + suite_id,
        'SUITE_LIBREPM_KEY=' + secrets.token_urlsafe(32),
        'SMOKE_SUITE_PROJECT_ID=' + project_id, '',
    ]), encoding='utf-8')
    container = None
    image = build['repository_digests'][0]
    try:
        created = subprocess.run([
            'docker', 'create', '--name', 'codex-portal-image-' + uuid.uuid4().hex[:10],
            '--label', 'codex.task=portal-image-smoke', '--network', 'container:' + state['container_id'],
            '--env-file', str(environment.resolve()), image,
        ], check=True, capture_output=True, text=True, timeout=30)
        container = created.stdout.strip()
        subprocess.run(['docker', 'start', container], check=True, capture_output=True, timeout=20)
        program = '''
import json,os,time,urllib.request,urllib.error,sys
base='http://127.0.0.1:8080'
def request(path, expected, headers=None, method='GET', payload=None):
    headers=dict(headers or {})
    data=json.dumps(payload).encode() if payload is not None else None
    if data is not None: headers['Content-Type']='application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(base+path,headers=headers,data=data,method=method),timeout=3) as r:
            code=r.status; body=r.read()
    except urllib.error.HTTPError as e:
        code=e.code; body=e.read()
    assert code==expected,(path,code,expected)
    return json.loads(body)
for attempt in range(30):
    try:
        request('/health',200)
        break
    except (OSError,AssertionError):
        if attempt==29: raise
        time.sleep(1)
checks=[]
for path,code in [('/health',200),('/api/collaboration/v1/me',401),('/api/calls',404),('/docs',404),('/api/collaboration/v1/maintenance/heartbeat',401)]:
    request(path,code); checks.append({'path':path,'status':code})
assert request('/api/collaboration/v1/maintenance/heartbeat',200,{'X-Cron-Key':os.environ['COLLAB_CRON_KEY']})['database']=='ok'
path='/api/integrations/v1/peer/projects/'+os.environ['SMOKE_SUITE_PROJECT_ID']
request(path,401)
headers={'X-Suite-Peer':'librepm','X-Suite-Key':os.environ['SUITE_LIBREPM_KEY'],'X-Suite-Organization':os.environ['SUITE_ORGANIZATION_ID']}
projection={'schemaVersion':1,'organizationId':os.environ['SUITE_ORGANIZATION_ID'],'suiteProjectId':os.environ['SMOKE_SUITE_PROJECT_ID'],'sourceVersion':1,'title':'Synthetic container project','description':None,'plannedBudget':None,'projectDeadline':None,'targetLocalId':None,'links':{'librepm':None,'associago':None,'bandi':None}}
created=request(path,200,headers,'PUT',projection)
assert created['suiteProjectId']==os.environ['SMOKE_SUITE_PROJECT_ID'] and created['preparation'] is None
assert request(path,200,headers)==created
request(path,403,{**headers,'X-Suite-Organization':'00000000-0000-0000-0000-000000000000'})
request('/api/collaboration/v1/projects',401)
request('/api/integrations/v1/projects',404)
assert not any(x in sys.modules for x in ('agno','pgvector','apps.api.database','apps.api.local_app'))
print(json.dumps({'checks':checks,'cronDatabaseQuery':'ok','localImports':'absent','suitePeerPutGet':'ok','suiteOrganizationIsolation':'ok','suiteAnonymousDenied':'ok'}))
'''
        result = subprocess.run(['docker', 'exec', container, 'python', '-c', program],
                                capture_output=True, text=True, timeout=90)
        if result.returncode:
            raise RuntimeError('Gateway image smoke failed; database credentials were not logged')
        evidence = json.loads(result.stdout)
        evidence['image'] = image
        evidence['status'] = 'succeeded'
        encoded = json.dumps(evidence, indent=2)
        (args.build_directory / 'smoke-result.json').write_text(encoded, encoding='utf-8')
        print(encoded)
    finally:
        if container:
            subprocess.run(['docker', 'rm', '--force', '--volumes', container],
                           check=True, capture_output=True, timeout=20)
        subprocess.run(['docker', 'exec', state['container_id'], 'psql', '-U', database['user'],
                        '-d', database['database'], '-v', 'ON_ERROR_STOP=1', '-c',
                        "DELETE FROM bandi_portal.portal_suite_projects WHERE id='" + project_id + "'::uuid"],
                       check=True, capture_output=True, timeout=20)
        environment.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
