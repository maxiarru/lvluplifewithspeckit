import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, openSync, closeSync } from 'node:fs';
import { createServer } from 'node:net';
import assert from 'node:assert/strict';

// Start the built runtime locally, validate isolated installations, then run the normal native group.
// Run from the repository root, under Firebase emulators:exec, after compiling APKs and installDist.
const [adb, serial = 'emulator-5554'] = process.argv.slice(2);
assert.ok(adb, 'Pass the absolute adb executable path');
assert.match(serial, /^emulator-\d+$/);
assert.ok(process.env.JAVA_HOME, 'Set JAVA_HOME to the JDK 17 directory');
assert.equal(process.env.LVLUP_ENV, 'emulator');
assert.equal(process.env.GOOGLE_CLOUD_PROJECT, 'demo-lvluplife');
assert.equal(process.env.GCLOUD_PROJECT, 'demo-lvluplife');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:9099');
await new Promise((resolve, reject) => {
  const probe = createServer();
  probe.once('error', () => reject(new Error('Port 8081 must be free for the validation runtime')));
  probe.listen(8081, '127.0.0.1', () => probe.close(resolve));
});
mkdirSync('.tooling', { recursive: true });
const log = openSync('.tooling/us1-server.log', 'w');
const java = `${process.env.JAVA_HOME}/bin/${process.platform === 'win32' ? 'java.exe' : 'java'}`;
const server = spawn(java, ['-cp', 'server/build/install/server/lib/*', 'lvluplife.api.ServerKt'],
  { env: { ...process.env, PORT: '8081' }, windowsHide: true, stdio: ['ignore', log, log] });
try {
  let ready = false;
  for (let n = 0; n < 100; n++) {
    assert.equal(server.exitCode, null, 'Local server exited before readiness');
    try { ready = (await fetch('http://127.0.0.1:8081/v1/me', { signal: AbortSignal.timeout(1000) })).status === 401; } catch {}
    if (ready) break;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  assert.ok(ready, 'Local Netty server did not become ready');
  console.log('PASS local runtime Netty on 8081; unauthenticated request rejected');
  const installs = spawnSync(process.execPath, ['scripts/test-account-installations.mjs', adb, serial],
    { stdio: 'inherit', timeout: 300000, windowsHide: true });
  assert.equal(installs.status, 0, 'Two-installation validation failed');
  const native = spawnSync(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
    'lvluplife.data.GoogleCredentialRequestsTest,lvluplife.data.FirebaseSdkIdentityTest,lvluplife.ui.SessionNavigationTest,lvluplife.ui.AccessFlowTest,lvluplife.ui.ProfileFlowTest,lvluplife.data.PendingStoreTest',
    'lvluplife.android.test/androidx.test.runner.AndroidJUnitRunner'], { encoding: 'utf8', timeout: 180000, windowsHide: true });
  process.stdout.write(native.stdout ?? ''); process.stderr.write(native.stderr ?? '');
  assert.equal(native.status, 0); assert.match(native.stdout ?? '', /OK \(28 tests\)/);
} finally { server.kill(); closeSync(log); }
