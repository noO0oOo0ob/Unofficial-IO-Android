#!/usr/bin/env python3
import sys,zipfile,hashlib,json,re,subprocess
original,enhanced,aapt,output=sys.argv[1:]
def digest(p):
    h=hashlib.sha256()
    with open(p,'rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()
def signature(n):
    return n.upper().startswith('META-INF/') and (n.upper().endswith(('.SF','.RSA','.DSA','.EC')) or n.upper()=='META-INF/MANIFEST.MF')
with zipfile.ZipFile(original) as a,zipfile.ZipFile(enhanced) as b:
    original_names={n for n in a.namelist() if not n.endswith('/') and not signature(n)}
    enhanced_names={n for n in b.namelist() if not n.endswith('/') and not signature(n)}
    assert enhanced_names-original_names=={'classes4.dex'},'Unexpected additions'
    assert not original_names-enhanced_names,'Missing original files'
    changed=sorted(n for n in original_names if a.read(n)!=b.read(n))
    assert changed==['AndroidManifest.xml','classes2.dex'],changed
    assert b'InProcessRayNeoRuntime' not in b.read('classes4.dex'),'Legacy runtime packaged'
    assert b'CodexBridgeClient' not in b.read('classes4.dex'),'Legacy bridge packaged'
    names=[n for n in original_names if n.startswith(('assets/','lib/','res/'))]
# Compare compiled Manifest tree, ignoring XML line numbers and the two added components.
def tree(apk):
    text=subprocess.check_output([aapt,'dump','xmltree',apk,'--file','AndroidManifest.xml'],text=True)
    lines=text.splitlines();clean=[];i=0
    while i<len(lines):
        line=lines[i]
        if line.lstrip().startswith(('E: activity ','E: service ')):
            indent=len(line)-len(line.lstrip());j=i+1
            while j<len(lines) and len(lines[j])-len(lines[j].lstrip())>indent:j+=1
            if any(('io.turboio.enhanced.EnhancedActivity' in x or 'io.turboio.enhanced.AmapNavigationListenerService' in x) for x in lines[i:j]):i=j;continue
        clean.append(re.sub(r' \(line=\d+\)','',line));i+=1
    return clean
base,actual=tree(original),tree(enhanced)
# aapt normalizes platformBuildVersionName from a string to an int; Android does not use this build annotation.
def normalize(lines):
    return [re.sub(r'A: platformBuildVersionName="16" \(Raw: "16"\)', 'A: platformBuildVersionName=16',x) for x in lines]
if normalize(base)!=normalize(actual):
    import difflib
    print('\n'.join(difflib.unified_diff(base,actual)))
    raise AssertionError('Unexpected Manifest change')
report={'originalSha256':digest(original),'enhancedSha256':digest(enhanced),'changedOriginalEntries':changed,'addedEntries':['classes4.dex'],'identicalVendorFiles':len(original_names)-2,'identicalResourcesAssetsNativeFiles':len(names),'manifestOnlyAddsDemoActivityAndAmapListener':True,'hardwareVerified':False}
with open(output,'w') as f:json.dump(report,f,ensure_ascii=False,indent=2);f.write('\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
