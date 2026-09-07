"""Deploy a backed-up fixture, run a real client, restart, and restore exact staged files.

Only the Java process started here is stopped. Existing servers/occupied ports
abort the run. Test inventory changes are restricted to SXIALifeTest.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import threading
import time

REPO = Path(__file__).resolve().parents[2]
ROOT = Path('E:/Minecraft-Server/incisionTest')
version = sys.argv[1]
if version not in ('26.2', '1.21.11'):
    raise SystemExit('Expected version: 26.2 or 1.21.11')
folder = ROOT / ('run-26.2-paper' if version == '26.2' else 'run-1.21.11-paper')
java = Path('D:/Java/zulu25.32.21-ca-jdk25.0.2-win_x64/bin/java.exe') if version == '26.2' else Path('D:/Java/azul-21.0.10/bin/java.exe')
server_jar = folder / ('paper-26.2-84.jar' if version == '26.2' else 'paper-1.21.11-132.jar')
output = ROOT / 'sxia-lifecycle-results' / (version + '-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
output.mkdir(parents=True)
backups = {}
proc = None
reader = None
captured = []


def backup(path):
    """Remember absence too, so cleanup removes only files explicitly staged by this run."""
    if path in backups:
        return
    target = output / 'backup' / path.relative_to(folder)
    if path.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        backups[path] = target
    else:
        backups[path] = None


def deploy(source, target):
    backup(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def command(text):
    proc.stdin.write((text + '\n').encode('utf-8'))
    proc.stdin.flush()


def start(phase):
    """Keep console pipes and fresh logs so historical startup output cannot count as evidence."""
    global proc, reader, captured
    captured = []
    proc = subprocess.Popen([str(java), '-Djdk.net.unixdomain.tmpdir=E:/Temp/sxia-java', '-Xms512M', '-Xmx1536M', '-jar', str(server_jar), 'nogui'], cwd=folder,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    def read():
        with (output / (phase + '-server.log')).open('wb') as log:
            for line in iter(proc.stdout.readline, b''):
                log.write(line); log.flush()
                captured.append(line.decode('utf-8', errors='replace'))
    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    deadline = time.time() + 180
    while not any('Done (' in line for line in captured):
        if proc.poll() is not None or time.time() > deadline:
            raise RuntimeError('Server did not start: ' + ''.join(captured[-30:]))
        time.sleep(0.5)
    command('op SXIALifeTest')
    print('STARTED ' + version + ' ' + phase, flush=True)


def stop():
    """Graceful stop before restoring jars; the fallback kill applies only to our child process."""
    global proc
    if proc is not None and proc.poll() is None:
        command('stop')
        try:
            proc.wait(timeout=55)
        except subprocess.TimeoutExpired:
            proc.kill(); proc.wait()
            raise RuntimeError('Server required forced termination')
    if reader is not None:
        reader.join(timeout=5)
    proc = None


result = {'version': version, 'status': 'failed', 'output': str(output)}
try:
    with socket.socket() as port:
        if port.connect_ex(('127.0.0.1', 25565)) == 0:
            raise RuntimeError('Port 25565 occupied; existing server left untouched')
    if 'eula=true' not in (folder / 'eula.txt').read_text():
        raise RuntimeError('Server EULA not already accepted')
    for old in (folder / 'plugins').glob('SX-Item-Action-*.jar'):
        backup(old); old.unlink()
    # 该独立探针会自动改写服务器类并运行其他测试，暂时移出后仍按原路径恢复。
    for unrelated_probe in (folder / 'plugins').glob('Incision-Test-*.jar'):
        backup(unrelated_probe); unrelated_probe.unlink()
    addon = REPO / 'build/libs/SX-Item-Action-1.1.0.jar'
    result['sha256'] = hashlib.sha256(addon.read_bytes()).hexdigest()
    deploy(addon, folder / 'plugins' / addon.name)
    deploy(REPO / 'build/SXIA-Lifecycle-Probe.jar', folder / 'plugins/SXIA-Lifecycle-Probe.jar')
    deploy(REPO / 'tests/server/items.yml', folder / 'plugins/SX-Item/Item/SXIALifecycleProbe.yml')
    deploy(REPO / 'examples/lifecycle-items.yml', folder / 'plugins/SX-Item/Item/SXIALifecycleExamples.yml')
    deploy(REPO / 'examples/import-items.yml', folder / 'plugins/SX-Item/Item/SXIALifecycleImport.yml')
    deploy(REPO / 'examples/scripts/kether/lifecycle-break.ks', folder / 'plugins/SX-Item-Action/scripts/kether/lifecycle-break.ks')
    backup(folder / 'ops.json')
    env = dict(os.environ, NODE_PATH=str(ROOT / 'devdump/node_modules'))
    for phase in ('exercise', 'verify'):
        start(phase)
        with (output / (phase + '-client.log')).open('wb') as log:
            client = subprocess.run(['node', str(REPO / 'tests/server/client.js'), phase, str(output / (phase + '.json'))],
                                    cwd=REPO, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=180)
        stop()
        if client.returncode:
            raise RuntimeError('Client failed in ' + phase + '; see ' + str(output))
        # Force expiry to elapse while stopped rather than relying on online periodic scanning.
        time.sleep(4)
    result['status'] = 'passed'
except Exception as error:
    result['error'] = str(error)
finally:
    try:
        stop()
    finally:
        for target, source in backups.items():
            if source is None:
                target.unlink(missing_ok=True)
            else:
                shutil.copy2(source, target)
        (output / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False), flush=True)
sys.exit(0 if result['status'] == 'passed' else 1)
