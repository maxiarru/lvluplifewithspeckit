import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

// Run under firebase emulators:exec with :server:run listening on local port 8081.
// A newly created Android user provides a distinct app UID, SDK session and private storage.
const [adb, serial = 'emulator-5554'] = process.argv.slice(2);
assert.ok(adb, 'Pass the absolute adb executable path');
assert.match(serial, /^emulator-\d+$/, 'Use a disposable Android emulator, never a physical device');
assert.equal(process.env.GCLOUD_PROJECT, 'demo-lvluplife');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:9099');
const run = randomUUID();
const target = 'lvluplife.android';
const runner = `${target}.test/androidx.test.runner.AndroidJUnitRunner`;
const invoke = (...args) => {
  const result = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 180_000 });
  assert.equal(result.status, 0, `adb command failed: ${args.slice(0, 4).join(' ')}`);
  return result.stdout ?? '';
};
const stage = (user, method) => {
  const output = invoke('shell', 'am', 'instrument', '--user', String(user), '-w', '-r',
    '-e', 'accountRun', run, '-e', 'class', `lvluplife.data.AccountInstallationStages#${method}`, runner);
  process.stdout.write(output);
  assert.match(output, /OK \(1 test\)/, `Instrumentation failed: ${method}`);
  const field = (key, pattern) => {
    const match = output.match(new RegExp(`INSTRUMENTATION_STATUS: ${key}=(${pattern})`));
    assert.ok(match, `Missing checkpoint: ${key}`); return match[1];
  };
  return { profile: field('profileFingerprint', '[a-f0-9]{64}'), owner: field('ownerFingerprint', '[a-f0-9]{64}'), uid: field('installationUid', '\\d+') };
};

let secondUser;
try {
  assert.match(invoke('shell', 'am', 'get-current-user').trim(), /^0$/, 'Owner user must remain foreground');
  for (const apk of ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'])
    assert.match(invoke('install', '--user', '0', '-r', '-t', apk), /Success/);
  const created = invoke('shell', 'pm', 'create-user', `LvlUpValidation-${run}`);
  const match = created.match(/Success: created user id (\d+)/);
  assert.ok(match, 'Could not create an isolated validation user');
  secondUser = Number(match[1]); assert.ok(secondUser > 0);
  assert.match(invoke('shell', 'am', 'start-user', '-w', String(secondUser)), /Success/);
  for (const pkg of [target, `${target}.test`])
    assert.match(invoke('shell', 'cmd', 'package', 'install-existing', '--user', String(secondUser), pkg), /installed/);

  const first = stage(0, 'firstInstallationCommitsButKeepsItsLostResponsePending');
  invoke('shell', 'am', 'force-stop', '--user', '0', target);
  const second = stage(secondUser, 'secondInstallationReadsSameIdentityWithoutCreatingProfileOrImportingPending');
  assert.equal(second.owner, first.owner, 'Both installations must authenticate the same Firebase UID');
  assert.equal(second.profile, first.profile, 'Both installations must read the same saved account/cycle/revision');
  assert.notEqual(second.uid, first.uid, 'Installations must have distinct Linux app UIDs/private sandboxes');
  const reopened = stage(0, 'firstInstallationStillOwnsOriginalPendingAfterOtherInstallationReads');
  assert.deepEqual(reopened, first, 'The first installation must retain its identity and original saved profile');
  console.log('PASS two isolated Android installations; same Firebase identity and profile; no pending import; original pending retained');
} finally {
  // Remove only the fresh user created by this invocation; never alter the original user or its app data.
  if (secondUser > 0) {
    invoke('shell', 'am', 'stop-user', '-w', '-f', String(secondUser));
    assert.match(invoke('shell', 'pm', 'remove-user', String(secondUser)), /Success/);
  }
}
