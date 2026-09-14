// Safe build identity only. Never serializes environment variables or credentials.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../mobile-react-native'),gradle=fs.readFileSync(path.join(root,'android/app/build.gradle'),'utf8');
const manifest={version:gradle.match(/versionName "([^"]+)"/)[1],buildNumber:Number(gradle.match(/versionCode (\d+)/)[1]),commit:process.env.GITHUB_SHA||'LOCAL_UNCOMMITTED',channel:'INTERNAL',builtAt:new Date().toISOString(),status:'DEBUG_ARTIFACT',signInConfigured:!!(process.env.HONORABLE_GOOGLE_WEB_CLIENT_ID&&process.env.HONORABLE_ACCOUNT_API_URL),releaseNotes:'beta-operations/RELEASE-NOTES-1.0.1.md',deviceVerified:false};
fs.writeFileSync(path.join(root,'release-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log('Release identity recorded; no credential values included.');
