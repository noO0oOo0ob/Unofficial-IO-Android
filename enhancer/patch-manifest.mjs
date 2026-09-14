import fs from 'node:fs';

const file = process.argv[2];
if (!file) throw new Error('manifest path required');
let xml = fs.readFileSync(file, 'utf8');
if (!xml.includes('android:name="com.rayneo.venus.VenusApplication"')) {
  throw new Error('unexpected RayNeo manifest');
}
if (!xml.includes('io.turboio.enhanced.EnhancedActivity')) {
  const activity = `
        <activity
            android:name="io.turboio.enhanced.EnhancedActivity"
            android:label="Turbo IO"
            android:taskAffinity="com.rayneo.venus.pub.turbo"
            android:launchMode="singleTask"
            android:icon="@drawable/ic_launcher"
            android:theme="@android:style/Theme.Material.Light.NoActionBar"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
`;
  const index = xml.lastIndexOf('</application>');
  if (index < 0) throw new Error('application end tag not found');
  xml = xml.slice(0, index) + activity + xml.slice(index);
}
if (!xml.includes('io.turboio.enhanced.AmapNavigationListenerService')) {
  const service = `
        <service
            android:name="io.turboio.enhanced.AmapNavigationListenerService"
            android:label="Turbo IO 高德导航"
            android:exported="false"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService"/>
            </intent-filter>
        </service>
`;
  const index = xml.lastIndexOf('</application>');
  if (index < 0) throw new Error('application end tag not found');
  xml = xml.slice(0, index) + service + xml.slice(index);
}
fs.writeFileSync(file, xml);
