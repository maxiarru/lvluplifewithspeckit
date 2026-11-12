import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, openSync, closeSync, writeFileSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { randomUUID, createHash } from 'node:crypto';
import { createServer } from 'node:net';
import assert from 'node:assert/strict';

// Local-only staged test. AVDs LvlUp_Matrix_API* must be created in ANDROID_AVD_HOME first.
// Filters permit debugging one cell; unexecuted cells remain explicitly NO DISPONIBLE.
const [sdkArgument, ...filters] = process.argv.slice(2);
assert.ok(sdkArgument && process.env.ANDROID_AVD_HOME && process.env.JAVA_HOME);
for (const [key,value] of Object.entries({ LVLUP_ENV:'emulator', GOOGLE_CLOUD_PROJECT:'demo-lvluplife', GCLOUD_PROJECT:'demo-lvluplife',
  FIRESTORE_EMULATOR_HOST:'127.0.0.1:8080', FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099' })) assert.equal(process.env[key],value);
assert.ok(filters.every(f => /^(api(29|31|37)|phone|tablet|portrait|landscape)$/.test(f)));
const sdk=resolve(sdkArgument), adb=join(sdk,'platform-tools','adb.exe'), emulator=join(sdk,'emulator','emulator.exe');
const serial='emulator-5560', target='lvluplife.android', runner=`${target}.test/androidx.test.runner.AndroidJUnitRunner`;
const output=resolve('specs/001-lvl-up-life-mvp/matrix/t080');mkdirSync(output,{recursive:true});
mkdirSync('.tooling',{recursive:true});
const apks=['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'];
const apkHashes=Object.fromEntries(apks.map(path => [path,createHash('sha256').update(readFileSync(path)).digest('hex')]));
const cells=[];
for(const api of [29,31,37]) for(const [kind,width,height] of [['phone',360,640],['tablet',800,1280]]) for(const orientation of ['portrait','landscape']) {
  cells.push({id:`api${api}-${kind}-${orientation}`,api,kind,orientation,widthDp:orientation==='portrait'?width:height,
    heightDp:orientation==='portrait'?height:width,image:`system-images;android-${api===37?'37.0':api};google_apis;x86_64`,
    avd:`LvlUp_Matrix_API${api===37?'37_0':api}`,
    status:'NO DISPONIBLE',reason:'No ejecutada en esta invocación'});
}
const report={date:new Date().toISOString(),environment:'local emulators',serial,apkHashes,cells};
function save() {
  writeFileSync(join(output,'results.json'),JSON.stringify(report,null,2)+'\n');
  writeFileSync(join(output,'results.csv'),'cell,api,size_dp,orientation,status,reason\n'+cells.map(c =>
    [c.id,c.api,`${c.widthDp}x${c.heightDp}`,c.orientation,c.status,c.reason??''].map(v=>`"${String(v).replaceAll('"','""')}"`).join(',')).join('\n')+'\n');
}
function invoke(args,{check=true,timeout=30000,binary=false}={}) {
  const result=spawnSync(adb,['-s',serial,...args],{encoding:binary?null:'utf8',timeout,windowsHide:true,maxBuffer:8*1024*1024});
  if(check) assert.equal(result.status,0,`adb failed: ${args.slice(0,4).join(' ')}`);
  return binary?result.stdout:(result.stdout??'');
}
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function assertNoSystemError() {
  const windows=invoke(['shell','dumpsys','window','windows']);
  assert.doesNotMatch(windows,/Application (Not Responding|Error):/,'System error dialog invalidates this cell');
}
function currentAvd() { return invoke(['emu','avd','name'],{check:false}).split(/\r?\n/)[0].trim(); }
const ownedNames=new Set(['LvlUp_Matrix_API29','LvlUp_Matrix_API31','LvlUp_Matrix_API37_0']);
async function stopOwned() {
  const name=currentAvd();
  if(name) { assert.ok(ownedNames.has(name),'Refusing to stop an unrelated emulator');invoke(['emu','kill'],{check:false});
    for(let n=0;n<60 && invoke(['get-state'],{check:false}).trim()==='device';n++) await sleep(500); }
}
async function boot(api) {
  const name=`LvlUp_Matrix_API${api===37?'37_0':api}`;
  if(currentAvd()!==name) {
    await stopOwned();
    const fd=openSync(`.tooling/t080-emulator-api${api}.log`,'w');
    const child=spawn(emulator,['-avd',name,'-port','5560','-no-window','-no-snapshot','-no-boot-anim','-no-audio','-gpu','swiftshader','-memory','4096','-cores','4'],
      {env:{...process.env,ANDROID_HOME:sdk},windowsHide:true,stdio:['ignore',fd,fd]});
    child.on('error',()=>{});closeSync(fd);
  }
  let ready=false;
  for(let n=0;n<180;n++) { if(invoke(['shell','getprop','sys.boot_completed'],{check:false,timeout:5000}).trim()==='1') { ready=true;break; } await sleep(1000); }
  assert.ok(ready,`AVD API ${api} failed to boot`);assert.equal(Number(invoke(['shell','getprop','ro.build.version.sdk']).trim()),api);
  // boot_completed alone does not guarantee System UI has finished its startup services.
  await sleep(30_000);
  assertNoSystemError();
  invoke(['shell','input','keyevent','KEYCODE_WAKEUP']);invoke(['shell','wm','dismiss-keyguard']);
  // Keep launcher/app orientation preferences from overriding the requested matrix rotation.
  if(api>=37) invoke(['shell','wm','fixed-to-user-rotation','enabled']);
  for(const setting of ['window_animation_scale','transition_animation_scale','animator_duration_scale']) invoke(['shell','settings','put','global',setting,'0']);
  for(const apk of apks) assert.match(invoke(['install','-r','-t',apk],{timeout:180000}),/Success/);
  if(api>=37) invoke(['shell','pm','grant',target,'android.permission.ACCESS_LOCAL_NETWORK']);
}
function stage(cell,run,method) {
  assertNoSystemError();
  const result=spawnSync(adb,['-s',serial,'shell','am','instrument','-w','-r','-e','matrixRun',run,'-e','matrixCell',cell.id,
    '-e','matrixApi',String(cell.api),'-e','matrixDp',`${cell.widthDp}x${cell.heightDp}`,'-e','matrixOrientation',cell.orientation,
    '-e','class',`lvluplife.ui.DeviceMatrixStages#${method}`,runner],{encoding:'utf8',timeout:300000,windowsHide:true,maxBuffer:8*1024*1024});
  const raw=(result.stdout??'')+(result.stderr??'');writeFileSync(join(output,`${cell.id}-${method}.log`),raw);
  assert.equal(result.status,0,`${cell.id}: instrumentation command failed`);
  assert.match(raw,/OK \(1 test\)/,`${cell.id}: ${method} failed; inspect its log`);
  assert.doesNotMatch(raw,/Process crashed|INSTRUMENTATION_FAILED/);
  assertNoSystemError();
  const field=key => {const match=raw.match(new RegExp(`INSTRUMENTATION_STATUS: ${key}=([^\\r\\n]+)`));assert.ok(match,`Missing ${key}`);return match[1];};
  return {pid:Number(field('matrixPid')),owner:field('matrixOwner'),state:field('matrixState')};
}
async function runCell(cell) {
  const run=randomUUID();cell.run=run;cell.startedAt=new Date().toISOString();save();
  const directory=join(output,cell.id);mkdirSync(directory,{recursive:true});
  for(const name of ['failure.png','tree.txt','diagnostic.json','auth-failure.txt'])rmSync(join(directory,name),{force:true});
  try {
    invoke(['shell','am','force-stop',target]);
    const [w,h]=cell.kind==='phone'?[720,1280]:[1600,2560];
    invoke(['shell','wm','size',`${w}x${h}`]);invoke(['shell','wm','density','320']);
    invoke(['shell','settings','put','system','accelerometer_rotation','0']);
    const rotation=cell.orientation==='portrait'?'0':'1';
    if(cell.api>=37) invoke(['shell','wm','user-rotation','lock',rotation]);
    else invoke(['shell','settings','put','system','user_rotation',rotation]);
    await sleep(1500);
    cell.display={size:invoke(['shell','wm','size']).trim(),density:invoke(['shell','wm','density']).trim(),
      rotation:invoke(cell.api>=37?['shell','wm','user-rotation']:['shell','settings','get','system','user_rotation']).trim(),
      fingerprint:invoke(['shell','getprop','ro.build.fingerprint']).trim(),release:invoke(['shell','getprop','ro.build.version.release']).trim()};
    const before=stage(cell,run,'criticalFlowsBeforeClosingProcess');
    invoke(['shell','am','force-stop',target]);
    assert.equal(invoke(['shell','pidof',target],{check:false}).trim(),'','Target did not stop');
    const after=stage(cell,run,'confirmedDataAndDraftSurviveProcessReopen');
    assert.notEqual(before.pid,after.pid);assert.equal(before.owner,after.owner);assert.equal(before.state,after.state);
    cell.checkpoints={before,after};cell.captures=[];
    for(const label of ['profile','delete','history-error','classes','reset','confirmed-before-close','draft-reopened','history-reopened','reopened']) {
      const png=invoke(['exec-out','run-as',target,'cat',`cache/matrix-${run}-${label}.png`],{binary:true});
      assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');writeFileSync(join(directory,`${label}.png`),png);cell.captures.push(`${cell.id}/${label}.png`);
    }
    cell.status='APROBADO';cell.reason='Recorrido crítico y reapertura real del proceso aprobados';
    console.log(`PASS ${cell.id}: critical flow, persisted draft and confirmed state; PID changed`);
  } catch(error) {
    cell.status='FALLIDO';cell.reason=String(error.message).slice(0,300);
    let png=invoke(['exec-out','run-as',target,'cat',`cache/matrix-${run}-failure-live.png`],{binary:true,check:false});
    if(png?.subarray(0,8).toString('hex')!=='89504e470d0a1a0a') png=invoke(['exec-out','screencap','-p'],{binary:true,check:false});
    if(png?.length)writeFileSync(join(directory,'failure.png'),png);
    for(const suffix of ['tree.txt','diagnostic.json','auth-failure.txt']) {
      const content=invoke(['exec-out','run-as',target,'cat',`cache/matrix-${run}-${suffix}`],{check:false});
      if(content && !content.startsWith('cat:'))writeFileSync(join(directory,suffix),content);
    }
    throw error;
  } finally {cell.finishedAt=new Date().toISOString();save();}
}
await new Promise((resolve,reject)=> {const probe=createServer();probe.once('error',()=>reject(new Error('Port 8081 must be free')));probe.listen(8081,'127.0.0.1',()=>probe.close(resolve));});
const serverLog=openSync('.tooling/t080-server.log','w');
const server=spawn(join(process.env.JAVA_HOME,'bin','java.exe'),['-cp','server/build/install/server/lib/*','lvluplife.api.ServerKt'],
  {env:{...process.env,PORT:'8081'},windowsHide:true,stdio:['ignore',serverLog,serverLog]});
save();
try {
  let ready=false;
  for(let n=0;n<100;n++) {assert.equal(server.exitCode,null,'Local server exited');try {ready=(await fetch('http://127.0.0.1:8081/v1/me',{signal:AbortSignal.timeout(1000)})).status===401;}catch{}
    if(ready)break;await sleep(200);}
  assert.ok(ready,'Local Netty server not ready');
  // Exercise the newest platform first so compatibility failures stop before repeating older cells.
  for(const api of [37,29,31]) {
    const chosen=cells.filter(c=>c.api===api && filters.every(f=>[c.id.split('-')[0],c.kind,c.orientation].includes(f)));
    if(!chosen.length)continue;
    const name=`LvlUp_Matrix_API${api===37?'37_0':api}`;
    if(!existsSync(join(process.env.ANDROID_AVD_HOME,`${name}.ini`))) {for(const c of chosen)c.reason='AVD requerido no disponible';save();continue;}
    await boot(api);for(const cell of chosen)await runCell(cell);
  }
  if(!filters.length)assert.ok(cells.every(c=>c.status==='APROBADO'),'Matrix incomplete; unexecuted cells remain NO DISPONIBLE');
} finally {save();await stopOwned();server.kill();closeSync(serverLog);}
