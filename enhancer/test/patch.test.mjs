import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'turbo-hook-test-'));
try{
 const dir=path.join(temp,'decoded/smali_classes2/com/rayneo/rayneo_venus_sdk_plugin');fs.mkdirSync(dir,{recursive:true});
 const file=path.join(dir,'e.smali');const original='.class public final Lcom/rayneo/rayneo_venus_sdk_plugin/e;\n.method public final c(Ljava/lang/String;[B)V\n    .locals 0\n    return-void\n.end method\n';fs.writeFileSync(file,original);
 execFileSync('node',[path.join(root,'patch-assistant.mjs'),path.join(temp,'decoded')]);let patched=fs.readFileSync(file,'utf8');assert.match(patched,/if-eqz v0, :turbo_original/);assert.match(patched,/invoke-virtual \{p0, p1, p2\}.*turboOriginalC/);assert.ok(JSON.parse(fs.readFileSync(path.join(temp,'hook-proof.json'))).originalMethodBodyPreserved);
 assert.throws(()=>execFileSync('node',[path.join(root,'patch-assistant.mjs'),path.join(temp,'decoded')],{stdio:'pipe'}));
 const manifest=path.join(temp,'AndroidManifest.xml');const xml='<manifest><application android:name="com.rayneo.venus.VenusApplication"><activity android:name="com.rayneo.venus.MainActivity"/></application></manifest>';fs.writeFileSync(manifest,xml);execFileSync('node',[path.join(root,'patch-manifest.mjs'),manifest]);const m=fs.readFileSync(manifest,'utf8');assert.ok(m.includes('com.rayneo.venus.MainActivity'));assert.ok(!m.includes('networkSecurityConfig'));assert.ok(m.includes('io.turboio.enhanced.AmapNavigationListenerService'));assert.ok(m.includes('android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'));assert.ok(m.includes('android:taskAffinity="com.rayneo.venus.pub.turbo"'));assert.ok(m.includes('android:launchMode="singleTask"'));execFileSync('node',[path.join(root,'patch-manifest.mjs'),manifest]);assert.equal(m,fs.readFileSync(manifest,'utf8'));
 console.log('PASS hook/manifest invariants and duplicate-patch rejection');
}finally{fs.rmSync(temp,{recursive:true,force:true});}
