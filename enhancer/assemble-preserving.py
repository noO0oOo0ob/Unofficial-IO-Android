#!/usr/bin/env python3
"""Use the original APK as template; never ship apktool's rebuilt vendor resources."""
import sys, zipfile, subprocess, re
original, rebuilt, output, aapt = sys.argv[1:]
def dump(apk):
    return subprocess.check_output([aapt, 'dump', 'resources', apk], text=True)
def ids(s):
    return sorted(re.findall(r'resource (0x[0-9a-f]+) ([^\s]+)', s))
assert ids(dump(original)) == ids(dump(rebuilt)), 'Resource IDs changed: cannot use rebuilt Manifest with original resources'
def signature(name):
    return name.upper().startswith('META-INF/') and (name.upper().endswith(('.RSA','.DSA','.EC','.SF')) or name.upper()=='META-INF/MANIFEST.MF')
replacements={'AndroidManifest.xml','classes2.dex'}
with zipfile.ZipFile(original) as src, zipfile.ZipFile(rebuilt) as built, zipfile.ZipFile(output,'w') as dst:
    assert 'classes4.dex' not in src.namelist(), 'Unexpected source DEX layout'
    for item in src.infolist():
        if signature(item.filename): continue
        dst.writestr(item, built.read(item.filename) if item.filename in replacements else src.read(item.filename))
    dst.writestr('classes4.dex',built.read('classes4.dex'),compress_type=zipfile.ZIP_DEFLATED)
print('Assembled from original ZIP; replaced only Manifest/classes2; added classes4.')
