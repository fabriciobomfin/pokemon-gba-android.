// Backups carry data keyed by the SHA-256 of each ROM; ROM files are never included.
let completeBackupBusy=false;
const backupPreferenceKey=key=>/^(meta|pocket|gba-visual-settings):[a-f0-9]{64}$/.test(key)||['fire-game-theme','shelfSort'].includes(key);
function backupPreferences(){const result={};for(let i=0;i<localStorage.length;i++){const key=localStorage.key(i);if(backupPreferenceKey(key))result[key]=localStorage.getItem(key)}return result}
function backupCatalog(){
 const catalog={...read('restored-game-catalog',{})};
 for(const id of Object.keys(games))if(/^[a-f0-9]{64}$/.test(id))catalog[id]={name:gameMeta(id).name||games[id],system:systems[id]||'gba'};
 for(const key of Object.keys(backupPreferences())){const id=key.split(':')[1];if(id&&!catalog[id])catalog[id]={name:gameMeta(id).name||('Jogo '+id.slice(0,8)),system:'gba'}}
 return catalog;
}
function setBackupBusy(value){completeBackupBusy=value;document.body.classList.toggle('backup-working',value);for(const id of ['full-export','full-import'])if(el(id))el(id).disabled=value}
function fullBackupResult(message){setBackupBusy(false);if(el('full-backup-status'))el('full-backup-status').textContent=message;updateWaitingGames()}
function openFullBackup(){
 show('Backup completo');
 detail.append(make('p','Guarde os saves de todos os jogos, capas, controles e configurações em um único arquivo ZIP. As ROMs não são incluídas.'),make('p','Na restauração, os saves do cartucho e as configurações presentes no backup substituem os correspondentes atuais. Outros jogos são mantidos; pontos de save existentes são preservados.'));
 const exp=action('Exportar tudo',exportFullBackup);exp.id='full-export';exp.className='primary';
 const imp=action('Restaurar backup completo',()=>{
  if(completeBackupBusy)return;
  detail.replaceChildren(make('h2','Restaurar backup completo?'),make('p','Os saves do cartucho e as configurações desse backup serão aplicados. Exporte uma cópia atual antes, se quiser preservá-los.'),make('p','Jogos ausentes não causam erro: os dados ficam guardados até você importar a mesma ROM.'),action('Cancelar',openFullBackup),action('Escolher backup',()=>{setBackupBusy(true);if(!Android.fullBackupRequest(false,'{}',0))fullBackupResult('Não foi possível iniciar a restauração.')}));
  const status=make('p');status.id='full-backup-status';status.setAttribute('role','status');detail.append(status);
 });imp.id='full-import';
 const status=make('p');status.id='full-backup-status';status.setAttribute('role','status');
 detail.append(exp,imp,status);setBackupBusy(completeBackupBusy);
}
function bytesToBase64(bytes){let raw='';for(let i=0;i<bytes.length;i+=8192)raw+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(raw)}
async function exportFullBackup(){
 if(completeBackupBusy)return;setBackupBusy(true);el('full-backup-status').textContent='Preparando todos os saves…';let db;
 try{
  db=await historyDb();const keys=await query(db,s=>s.getAllKeys());if(keys.length>10000)throw Error('Limite de 10 mil pontos por arquivo.');
  let total=0;
  for(const [i,key] of keys.entries()){
   const point=await query(db,s=>s.get(key));const data=JSON.stringify({...point,bytes:bytesToBase64(point.bytes)});total+=data.length;
   if(total>240*1024*1024)throw Error('Os pontos excedem 240 MB. Exporte alguns jogos separadamente.');
   if(!Android.stagePoint(i,data))throw Error('Não foi possível preparar um ponto de save.');
   el('full-backup-status').textContent='Preparando saves: '+(i+1)+' de '+keys.length;
  }
  const metadata=JSON.stringify({preferences:backupPreferences(),catalog:backupCatalog()});
  if(!Android.fullBackupRequest(true,metadata,keys.length))throw Error('Não foi possível abrir o destino do backup.');
  el('full-backup-status').textContent='Escolha onde guardar o arquivo.';
 }catch(e){fullBackupResult('Falha: '+e.message)}finally{db?.close()}
}
function plain(value){return !!value&&typeof value==='object'&&!Array.isArray(value)}
function validateFullMetadata(manifest){
 if(manifest.format!=='fire-game-full'||manifest.version!==1||!Number.isSafeInteger(manifest.count)||manifest.count<0||manifest.count>10000)throw Error('Formato de backup incompatível.');
 const metadata=manifest.settings;if(!plain(metadata)||!plain(metadata.preferences)||!plain(metadata.catalog))throw Error('Configurações inválidas.');
 for(const [key,value] of Object.entries(metadata.preferences)){
  if(!backupPreferenceKey(key)||typeof value!=='string')throw Error('Configuração não reconhecida.');
  const parsed=JSON.parse(value);
  if(key.includes(':')&&!plain(parsed))throw Error('Perfil inválido.');
  if(key.startsWith('meta:')&&parsed.cover&&!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(parsed.cover))throw Error('Capa inválida.');
 }
 for(const [id,entry] of Object.entries(metadata.catalog))if(!/^[a-f0-9]{64}$/.test(id)||!plain(entry)||typeof entry.name!=='string'||entry.name.length>500||!['gba','nds'].includes(entry.system))throw Error('Catálogo inválido.');
 return metadata;
}
async function restoreFullBackup(){
 setBackupBusy(true);let db,nativeInstalled=false,previous=null;
 try{
  const manifest=JSON.parse(Android.fullBackupManifest()),metadata=validateFullMetadata(manifest),points=[];
  const seen=new Set();
  for(let i=0;i<manifest.count;i++){
   const p=JSON.parse(Android.fullBackupPoint(i));
   if(!/^[a-f0-9]{64}$/.test(p.game)||!Number.isSafeInteger(p.time)||p.time<0||typeof p.bytes!=='string'||!p.bytes||p.name!==undefined&&typeof p.name!=='string')throw Error('Ponto de save inválido.');
   if(p.picture&&!/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(p.picture))throw Error('Prévia de save inválida.');
   const key=p.game+':'+p.time;if(seen.has(key))throw Error('Pontos duplicados no arquivo.');seen.add(key);
   p.bytes=Uint8Array.from(atob(p.bytes),c=>c.charCodeAt(0));points.push(p);
  }
  previous={};for(const key of Object.keys(metadata.preferences))previous[key]=localStorage.getItem(key);
  previous['restored-game-catalog']=localStorage.getItem('restored-game-catalog');
  // Journal permits recovery if Android terminates the app during restoration.
  localStorage.setItem('full-backup-rollback',JSON.stringify(previous));
  for(const [key,value] of Object.entries(metadata.preferences))localStorage.setItem(key,value);
  write('restored-game-catalog',{...read('restored-game-catalog',{}),...metadata.catalog});
  if(!Android.fullBackupInstall())throw Error('Não foi possível aplicar os saves; os originais foram preservados.');nativeInstalled=true;
  db=await historyDb();
  await new Promise((resolve,reject)=>{
   const tx=db.transaction('history','readwrite'),history=tx.objectStore('history');tx.oncomplete=resolve;tx.onabort=()=>reject(tx.error||Error('Falha ao guardar os pontos.'));
   for(const point of points){const request=history.get([point.game,point.time]);request.onsuccess=()=>{if(!request.result)history.add(point)}}
  });
  if(!Android.fullBackupFinish())throw Error('Não foi possível finalizar a restauração.');nativeInstalled=false;
  localStorage.removeItem('full-backup-rollback');
  applyTheme(read('fire-game-theme','ameixa'));render();
  const missing=Object.keys(metadata.catalog).filter(id=>!Object.hasOwn(games,id)).length;
  Android.fullBackupComplete('Backup restaurado. '+manifest.count+' pontos processados.'+(missing?' Dados de '+missing+' jogo(s) aguardam a importação da ROM.':''));
 }catch(e){
  if(nativeInstalled)Android.fullBackupRollback();
  if(previous)for(const [key,value] of Object.entries(previous)){if(value===null)localStorage.removeItem(key);else localStorage.setItem(key,value)}
  localStorage.removeItem('full-backup-rollback');Android.fullBackupComplete('Falha ao restaurar: '+e.message);
 }finally{db?.close();setBackupBusy(false)}
}
function updateWaitingGames(){
 const box=el('waiting-games');if(!box)return;
 const waiting=Object.entries(read('restored-game-catalog',{})).filter(([id])=>!Object.hasOwn(games,id));
 box.hidden=!waiting.length;box.replaceChildren();if(!waiting.length)return;
 box.append(make('strong',waiting.length+' jogo(s) com dados guardados'),make('p','Importe a mesma ROM para recuperar saves, capa e controles.'));
 for(const [,entry] of waiting)box.append(make('small',entry.name));
}
// Restore the old preferences if a previous restore was interrupted before it completed.
try{const old=JSON.parse(localStorage.getItem('full-backup-rollback')||'null');if(old){for(const [key,value] of Object.entries(old))if(backupPreferenceKey(key)||key==='restored-game-catalog'){if(value===null)localStorage.removeItem(key);else localStorage.setItem(key,value)}localStorage.removeItem('full-backup-rollback');applyTheme(read('fire-game-theme','ameixa'));render()}}catch{}
el('full-backup-open').onclick=openFullBackup;
updateWaitingGames();
