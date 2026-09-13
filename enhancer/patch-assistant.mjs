import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const root=process.argv[2];
if(!root)throw new Error('decoded directory required');
const file=path.join(root,'smali_classes2/com/rayneo/rayneo_venus_sdk_plugin/e.smali');
const original=fs.readFileSync(file,'utf8');
const signature='.method public final c(Ljava/lang/String;[B)V';
if(!original.startsWith('.class public final Lcom/rayneo/rayneo_venus_sdk_plugin/e;'))throw new Error('wrong class');
if(original.split(signature).length!==2||original.includes('turboOriginalC'))throw new Error('unexpected method or already patched');
const start=original.indexOf(signature),end=original.indexOf('.end method',start)+'.end method'.length;
if(end<start)throw new Error('method boundary');
const body=original.slice(start,end);
const wrapper=`${signature}
    .locals 1
    invoke-static {p1, p2}, Lio/turboio/enhanced/DemoHooks;->onAssistantMessage(Ljava/lang/String;[B)Z
    move-result v0
    if-eqz v0, :turbo_original
    return-void
    :turbo_original
    invoke-virtual {p0, p1, p2}, Lcom/rayneo/rayneo_venus_sdk_plugin/e;->turboOriginalC(Ljava/lang/String;[B)V
    return-void
.end method

`;
const patched=original.slice(0,start)+wrapper+body.replace(signature,signature.replace(' c(', ' turboOriginalC('))+original.slice(end);
fs.writeFileSync(file,patched);
// Verifiable invariant: remove wrapper + restore method name gives byte-for-byte original smali.
const restored=patched.replace(wrapper,'').replace(' turboOriginalC(', ' c(');
if(restored!==original)throw new Error('official body changed');
fs.writeFileSync(path.join(root,'../hook-proof.json'),JSON.stringify({class:'com.rayneo.rayneo_venus_sdk_plugin.e',method:'c(String, byte[])',originalSha256:crypto.createHash('sha256').update(original).digest('hex'),originalMethodBodyPreserved:true},null,2)+'\n');
console.log('Assistant hook inserted; original class text recoverable byte-for-byte.');
