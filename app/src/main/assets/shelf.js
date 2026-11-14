const info=JSON.parse(Android.info()),games=JSON.parse(Android.library()),systems=JSON.parse(Android.librarySystems());
el('help').onclick=()=>{show('Como jogar');detail.append(make('p','Importe uma ROM .gba, .nds ou um ZIP e toque em Jogar. A partida abre em tela cheia. Toque em 1× para alternar a velocidade.'),make('p','Na engrenagem, toque em Salvar jogo. Voltar ao save recupera aquele instante. Para exportar os pontos de save e o progresso do cartucho, abra Mais e escolha Backup do último jogo.'))};
el('import').onclick=()=>Android.pick();el('continue').onclick=()=>Android.play();
el('saved').hidden=el('tools').hidden=el('progress').hidden=!info.available;
function historyDb(){
 return new Promise((resolve,reject)=>{
  const req=indexedDB.open('fire-game-checkpoints',2);
  req.onupgradeneeded=()=>{const history=req.result.createObjectStore('history',{keyPath:['game','time']});
   if(req.result.objectStoreNames.contains('games')){const cursor=req.transaction.objectStore('games').openCursor();cursor.onsuccess=()=>{const c=cursor.result;if(c){history.put({...c.value,game:c.key});c.continue()}}}};
  req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);
 });
}
function query(db,fn){return new Promise((resolve,reject)=>{const req=fn(db.transaction('history').objectStore('history'));req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error)})}
async function exportPoints(){
 const db=await historyDb();
 try{
  const keys=await query(db,s=>s.getAllKeys(IDBKeyRange.bound([info.id,0],[info.id,Number.MAX_SAFE_INTEGER])));
  for(const [i,key] of keys.entries()){
   const item=await query(db,s=>s.get(key));let raw='';
   for(let n=0;n<item.bytes.length;n+=8192)raw+=String.fromCharCode(...item.bytes.subarray(n,n+8192));
   if(!Android.stagePoint(i,JSON.stringify({...item,bytes:btoa(raw)})))throw Error();
  }
  if(!keys.length && !Object.keys(JSON.parse(Android.loadFiles())).length){Android.finishBackup('Nenhum save neste jogo.');return}
  Android.exportPoints(keys.length);
 }finally{db.close()}
}
el('exp').onclick=()=>exportPoints().catch(()=>Android.finishBackup('Falha ao preparar backup.'));
async function restorePoints(count,id){
 let db;
 try{
  if(id!==info.id)throw Error();db=await historyDb();
  await new Promise((resolve,reject)=>{const tx=db.transaction('history','readwrite');tx.oncomplete=resolve;tx.onabort=()=>reject(tx.error);
   try{for(let i=0;i<count;i++){const item=JSON.parse(Android.staged(i));if(item.game!==id||!Number.isSafeInteger(item.time)||typeof item.bytes!=='string'||!item.bytes)throw Error();item.bytes=Uint8Array.from(atob(item.bytes),c=>c.charCodeAt(0));tx.objectStore('history').put(item)}}catch(e){tx.abort();reject(e)}
  });Android.finishBackup('Backup restaurado: '+count+' pontos de save.');
 }catch{Android.finishBackup('Falha ao restaurar os pontos de save.')}finally{db?.close()}
}
el('imp').onclick=()=>{if(confirm('Restaurar o progresso do último jogo usando um backup?'))Android.backup(false)};
const dialog=el('modal'),detail=el('detail');
el('close').onclick=()=>dialog.close();
function show(title){detail.replaceChildren(make('h2',title));if(!dialog.open)dialog.showModal()}
function edit(id){
 const meta=gameMeta(id);show('Detalhes do jogo');
 const name=make('input');name.value=meta.name||games[id];name.maxLength=100;name.setAttribute('aria-label','Nome na biblioteca');
 const notes=make('textarea');notes.placeholder='Anotações da aventura';notes.value=meta.notes||'';notes.maxLength=5000;
 detail.append(name,notes,make('p','Sessões iniciadas: '+(meta.sessions||0)),make('p','Tempo jogado: '+Math.floor((meta.seconds||0)/60)+' min'),make('small','ID da ROM · '+id.slice(0,12)),action('Salvar',()=>{updateMeta(id,{name:name.value.trim()||games[id],notes:notes.value});dialog.close();render()}))
}
function render(){
 const ids=Object.keys(games),q=el('search').value.toLocaleLowerCase(),order=el('sort').value;
 el('romName').textContent=gameMeta(info.id).name||info.name;el('list').replaceChildren();
 const recent=ids.filter(id=>gameMeta(id).last).sort((a,b)=>gameMeta(b).last-gameMeta(a).last).slice(0,5);
 el('recent').hidden=!recent.length;
 el('recent-games').replaceChildren(...recent.map(id=>action(gameMeta(id).name||games[id],()=>Android.select(id))));
 for(const [system,label] of [['gba','game-count'],['nds','nds-count']]){const count=ids.filter(id=>(systems[id]||'gba')===system).length;el(label).textContent=count+(count===1?' jogo':' jogos');}
 const rows=ids.map(id=>({id,...gameMeta(id),name:gameMeta(id).name||games[id]})).filter(g=>g.name.toLocaleLowerCase().includes(q)&&(!el('fav').checked||g.star)&&(!el('system-filter').value||(systems[g.id]||'gba')===el('system-filter').value));
 rows.sort((a,b)=>order==='name'?a.name.localeCompare(b.name,'pt-BR'):order==='time'?(b.seconds||0)-(a.seconds||0):(b.last||0)-(a.last||0));
 for(const g of rows){const row=make('article');row.className='gamecard';const b=action(g.name,()=>Android.select(g.id));b.className='title';b.append(make('small',(systems[g.id]==='nds'?'Nintendo DS':'GBA')));b.append(make('small',g.last?'Última sessão · '+new Date(g.last).toLocaleDateString('pt-BR'):'Importado'));const star=action(g.star?'★':'☆',()=>{updateMeta(g.id,{star:!g.star});render()});star.setAttribute('aria-label',g.star?'Remover favorito':'Favoritar');row.append(b,star,action('Detalhes',()=>edit(g.id)));el('list').append(row)}
 if(!rows.length)el('list').append(make('p',ids.length?'Nenhum jogo corresponde ao filtro.':'Sua biblioteca começa com a primeira ROM.'))
}
el('sort').value=read('shelfSort','recent');el('sort').onchange=()=>{write('shelfSort',el('sort').value);render()};el('search').oninput=el('fav').onchange=render;render();
for(const [selector,icon] of [['#home-page h1','library'],['#help','guide'],['#progress summary','backup'],['#tools summary','search'],['.utility a','document']]){
 document.querySelector(selector).prepend(sectionIcon(icon));
}

function openPage(name){
 for(const button of document.querySelectorAll('#home-nav button')){
  const selected=button.dataset.page===name;
  el(button.dataset.page+'-page').hidden=!selected;
  button.setAttribute('aria-current',selected?'page':'false');
 }
 window.scrollTo(0,0);
}
for(const [name,icon] of [['home','library'],['categories','controller'],['themes','palette']]){
 const button=document.querySelector('[data-page="'+name+'"]');
 button.prepend(sectionIcon(icon));
 button.onclick=()=>openPage(name);
}
for(const system of ['gba','nds']){
 el(system+'-category').prepend(sectionIcon('controller'));
 el(system+'-category').onclick=()=>{
  el('search').value='';el('fav').checked=false;el('system-filter').value=system;render();openPage('home');
  document.querySelector('#home-page h1').scrollIntoView();
 };
}
el('system-filter').onchange=render;
function selectTheme(id){
 applyTheme(id);write('fire-game-theme',id);
 for(const tile of el('gallery').children)tile.setAttribute('aria-pressed',String(tile.dataset.id===id));
}
for(const [id,theme] of Object.entries(themes)){
 const tile=action('',()=>selectTheme(id));tile.dataset.id=id;
 if(theme.pattern)tile.style.setProperty('--sample-pattern',theme.pattern);
 tile.style.setProperty('--sample-base',theme.colors[0]);tile.style.setProperty('--sample-panel',theme.colors[1]);
 tile.style.setProperty('--sample-line',theme.colors[2]);tile.style.setProperty('--sample-accent',theme.colors[5]);
 const preview=make('span');preview.className='console-preview';preview.setAttribute('aria-hidden','true');
 for(const label of ['✚','B','A'])preview.append(make('i',label));
 tile.append(preview,make('strong',theme.name));
 tile.setAttribute('aria-pressed',String(document.documentElement.dataset.theme===id));
 el('gallery').append(tile);
}
openPage('home');
