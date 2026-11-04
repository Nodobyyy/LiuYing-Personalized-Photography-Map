// Dedicated emulator fixture only. Never include this library in the APK.
import fs from 'node:fs';
import path from 'node:path';
import {DatabaseSync} from 'node:sqlite';
const root=path.resolve('.tooling/emulator-fixture');
if(!root.startsWith(path.resolve('.tooling')+path.sep))throw Error('Unsafe fixture path');
const db=new DatabaseSync(path.join(root,'photomap.db'));
db.exec("UPDATE locations SET name='测试地点';UPDATE records SET title='摄影记录（测试）';");db.close();
fs.mkdirSync(path.join(root,'thumbnails'),{recursive:true});
for(const name of fs.readdirSync(path.join(root,'originals')))fs.copyFileSync(path.join(root,'originals',name),path.join(root,'thumbnails',name.replace('.photo','.jpg')));
