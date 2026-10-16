#!/usr/bin/env python3
"""Independent Docker services + real Spring/MySQL/RabbitMQ business validation.

Run: python3 scripts/run-business-validation.py
Requires Docker Desktop, Java 17, Maven and Python 3.9+. No .env is loaded.
Each invocation preserves uniquely named reports under backend/target and a
compact JSON evidence manifest under docs/validation-evidence/.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[1]


def run(command, *, input_text=None, env=None, cwd=ROOT, check=True):
    result = subprocess.run(command, cwd=cwd, input=input_text, text=True,
                            env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if check and result.returncode:
        raise RuntimeError("Command failed: {}\n{}".format(command, result.stdout[-8000:]))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stages', nargs='+', choices=['imports', 'growth', 'mq', 'assistant', 'precision', 'regression', 'llm'],
                        default=['imports', 'growth', 'mq', 'assistant', 'regression'])
    parser.add_argument('--live-llm', action='store_true', help='Explicit opt-in: 25 fixed synthetic requests maximum; reads only four Assistant settings from .env')
    parser.add_argument('--keep-test-volumes', action='store_true', help='Keep disposable test volumes after stopping test containers; never removes development volumes')
    args = parser.parse_args()
    stamp = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime('%Y%m%d-%H%M%S')
    project = 'maimaidx-validation-' + stamp
    compose = ['docker', 'compose', '-p', project, '-f', str(ROOT / 'compose.validation.yml')]
    evidence = ROOT / 'docs' / 'validation-evidence' / stamp
    evidence.mkdir(parents=True, exist_ok=False)
    manifest = {'runId': stamp, 'project': project, 'database': 'maimaidx_validation',
                'environment': {}, 'stages': [], 'externalLlmCalled': False}
    env = os.environ.copy()
    if 'llm' in args.stages and not args.live_llm:
        raise RuntimeError('LLM evaluation requires explicit --live-llm; no external requests made')
    # Isolate application properties from a shell's development secrets/settings.
    for key in list(env):
        if key.startswith(('SPRING_', 'DB_', 'REDIS_', 'RABBITMQ_', 'ASSISTANT_', 'JWT_', 'MAIDX_VALIDATION_')):
            env.pop(key)
    if args.live_llm:
        if 'llm' not in args.stages:
            raise RuntimeError('Specify --stages llm with --live-llm')
        allowed = {'ASSISTANT_AI_API_KEY', 'ASSISTANT_AI_BASE_URL', 'ASSISTANT_AI_MODEL', 'ASSISTANT_AI_ENABLED'}
        settings = {}
        for line in (ROOT / '.env').read_text().splitlines():
            key, separator, value = line.removeprefix('export ').partition('=')
            if separator and key.strip() in allowed:
                settings[key.strip()] = value.strip().strip('\"\'')
        endpoint = urlsplit(settings.get('ASSISTANT_AI_BASE_URL', ''))
        if endpoint.scheme != 'https' or not endpoint.hostname or not endpoint.hostname.endswith('.aliyuncs.com') or endpoint.query or endpoint.fragment or endpoint.username:
            raise RuntimeError('Refusing unexpected external endpoint')
        if endpoint.path.rstrip('/') == '/compatible-mode/v1':
            endpoint = endpoint._replace(path=endpoint.path.rstrip('/') + '/chat/completions')
        if not endpoint.path.endswith('/chat/completions') or settings.get('ASSISTANT_AI_MODEL') != 'qwen-plus' or not settings.get('ASSISTANT_AI_API_KEY'):
            raise RuntimeError('Incomplete Qwen configuration; no request sent')
        env.update({'MAIDX_EVAL_AUTHORIZED':'true', 'MAIDX_EVAL_URL':urlunsplit(endpoint),
                    'MAIDX_EVAL_API_KEY':settings['ASSISTANT_AI_API_KEY'], 'MAIDX_EVAL_RESULT':str(evidence / 'llm-results.json')})
        manifest['evaluationCaseSetSha256'] = hashlib.sha256((ROOT/'backend/src/test/resources/llm-evaluation-cases.json').read_bytes()).hexdigest()

    def container(service):
        cid = run(compose + ['ps', '-q', service]).stdout.strip()
        labels = json.loads(run(['docker', 'inspect', cid]).stdout)[0]['Config']['Labels']
        if not project.startswith('maimaidx-validation-') or labels.get('com.docker.compose.project') != project:
            raise RuntimeError('Isolation verification failed')
        return cid

    def sql(statement):
        cid = container('mysql')
        return run(['docker', 'exec', '-i', cid, 'mysql', '-uroot',
                    '-pvalidation-only-password', '--batch', '--skip-column-names',
                    'maimaidx_validation'], input_text=statement).stdout

    try:
        print('Starting isolated services: ' + project, flush=True)
        run(compose + ['up', '-d', '--wait', '--wait-timeout', '180'])
        ports = {}
        for service, internal in [('mysql', 3306), ('rabbitmq', 5672), ('redis', 6379)]:
            ports[service] = int(run(compose + ['port', service, str(internal)]).stdout.strip().split(':')[-1])
        if any(port in [3306, 5672, 6379] for port in ports.values()):
            raise RuntimeError('Refusing standard development service ports')
        empty = sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='maimaidx_validation';")
        if empty.strip().splitlines()[-1] != '0':
            raise RuntimeError('Refusing to initialize a nonempty database')
        for name in ['init.sql', 'mvp_migration.sql', 'async_import_migration.sql', 'index_optimization.sql',
                     'score_precision_migration.sql', 'score_precision_migration.sql']:
            source = ROOT / 'database' / name
            statement = source.read_text(encoding='utf-8-sig').replace('maimai_dx', 'maimaidx_validation')
            sql(statement)
        sql("CREATE TABLE validation_guard (run_id VARCHAR(64) NOT NULL); INSERT INTO validation_guard VALUES ('{}');".format(stamp))
        manifest['environment'] = {
            'os': run(['uname', '-sm']).stdout.strip(),
            'java': run(['java', '-version']).stdout.splitlines()[0],
            'mysql': sql('SELECT VERSION();').strip().splitlines()[-1],
            'rabbitmq': run(['docker', 'exec', container('rabbitmq'), 'rabbitmq-diagnostics', '-q', 'server_version']).stdout.strip(),
            'ports': ports,
        }
        env.update({'MAIDX_VALIDATION_ENABLED': 'true', 'MAIDX_VALIDATION_RUN': stamp,
                    'MAIDX_VALIDATION_MYSQL_PORT': str(ports['mysql']),
                    'MAIDX_VALIDATION_RABBIT_PORT': str(ports['rabbitmq']),
                    'MAIDX_VALIDATION_REDIS_PORT': str(ports['redis'])})
        selectors = {'imports': 'ScoreImportValidationIT', 'growth': 'GrowthValidationIT',
                     'mq': 'RabbitImportValidationIT', 'assistant': 'AssistantValidationIT',
                     'precision': 'ScorePrecisionHistoryValidationIT',
                     'llm': 'LlmEvaluationIT',
                     'regression': '*Test,*ValidationIT'}
        for stage in args.stages:
            suffix = 'validation-' + stamp + '-' + stage
            log = ROOT / 'backend' / 'target' / (suffix + '.log')
            print('Running stage: ' + stage, flush=True)
            result = run(['mvn', 'test', '-Dtest=' + selectors[stage],
                          '-Dsurefire.reportNameSuffix=' + suffix, '-l', str(log)],
                         cwd=ROOT / 'backend', env=env, check=False)
            totals = dict(tests=0, failures=0, errors=0, skipped=0)
            suites = []
            for report in sorted((ROOT / 'backend' / 'target' / 'surefire-reports').glob('TEST-*-' + suffix + '.xml')):
                suite = ET.parse(report).getroot()
                counts = {key: int(suite.get(key, '0')) for key in totals}
                for key in totals:
                    totals[key] += counts[key]
                cases = []
                for case in suite.findall('testcase'):
                    failed = case.find('failure')
                    error = case.find('error')
                    skipped = case.find('skipped')
                    outcome = 'failure' if failed is not None else 'error' if error is not None else 'skipped' if skipped is not None else 'passed'
                    details = failed if failed is not None else error
                    cases.append({'name': case.get('name'), 'status': outcome,
                                  'message': details.get('message', '') if details is not None else ''})
                suites.append({'name': suite.get('name'), **counts, 'report': str(report.relative_to(ROOT)),
                               'cases': cases})
            entry = {'stage': stage, 'exitCode': result.returncode, **totals,
                     'log': str(log.relative_to(ROOT)), 'suites': suites}
            manifest['stages'].append(entry)
            if stage == 'llm' and (evidence / 'llm-results.json').exists():
                evaluation = json.loads((evidence / 'llm-results.json').read_text())
                manifest['externalLlmRequestsAttempted'] = evaluation['externalRequestsAttempted']
                manifest['externalLlmCalled'] = evaluation['externalRequestsAttempted'] > 0
            print(json.dumps({k: v for k, v in entry.items() if k != 'suites'}, ensure_ascii=False), flush=True)
            if result.returncode:
                print(log.read_text()[-6000:], flush=True)
                # Preserve failure evidence and continue with other independent stages.
        for path in sorted((ROOT / 'backend' / 'src' / 'test' / 'java').rglob('*ValidationIT.java')):
            manifest.setdefault('sourceSha256', {})[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    finally:
        manifest['testVolumeCleanupEnabled'] = not args.keep_test_volumes
        if args.keep_test_volumes:
            manifest['retainedTestVolumes'] = []
            ids = run(compose + ['ps', '-aq'], check=False).stdout.split()
            for cid in ids:
                inspection = run(['docker', 'inspect', cid], check=False)
                if inspection.returncode == 0:
                    info = json.loads(inspection.stdout)[0]
                    if info.get('Config', {}).get('Labels', {}).get('com.docker.compose.project') == project:
                        manifest['retainedTestVolumes'].extend(
                            {'name': mount['Name'], 'destination': mount['Destination']}
                            for mount in info.get('Mounts', []) if mount.get('Type') == 'volume')
        (evidence / 'summary.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        run(compose + (['down'] if args.keep_test_volumes else ['down', '--volumes']), check=False)
        print('Evidence: ' + str(evidence / 'summary.json'), flush=True)
    return 1 if any(stage['exitCode'] for stage in manifest['stages']) else 0


if __name__ == '__main__':
    sys.exit(main())
