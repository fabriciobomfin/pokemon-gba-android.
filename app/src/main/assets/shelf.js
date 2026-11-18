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
 const cover=make('input');cover.type='file';cover.accept='image/*';cover.setAttribute('aria-label','Escolher capa');
 const crop=make('section');crop.className='cover-editor';crop.hidden=true;
 const preview=make('canvas');preview.id='cover-preview';preview.width=240;preview.height=320;preview.setAttribute('aria-label','Prévia da capa na biblioteca');
 const zoom=make('input'),horizontal=make('input'),vertical=make('input');
 for(const [control,id,label,min,max,value] of [[zoom,'cover-zoom','Zoom',100,300,100],[horizontal,'cover-x','Posição horizontal',0,100,50],[vertical,'cover-y','Posição vertical',0,100,50]]){control.id=id;control.type='range';control.min=min;control.max=max;control.value=value;control.step=1;control.setAttribute('aria-label',label)}
 const zoomLabel=make('label','Zoom');zoomLabel.htmlFor=zoom.id;const zoomValue=make('output','100%');zoomValue.id='cover-zoom-value';zoomLabel.append(zoomValue);
 const xLabel=make('label','Posição horizontal');xLabel.htmlFor=horizontal.id;const yLabel=make('label','Posição vertical');yLabel.htmlFor=vertical.id;
 const caption=make('p','Arraste a imagem ou use os ajustes. A prévia mostra exatamente o recorte que será salvo.');
 const status=make('small');status.id='cover-status';status.setAttribute('role','status');
 let artwork=meta.cover||'',source=null,serial=0,drag=null;
 function drawCover(){
  if(!source)return;
  const scale=Math.max(240/source.naturalWidth,320/source.naturalHeight)*Number(zoom.value)/100;
  const width=source.naturalWidth*scale,height=source.naturalHeight*scale;
  const ctx=preview.getContext('2d');ctx.clearRect(0,0,240,320);ctx.imageSmoothingEnabled=true;ctx.imageSmoothingQuality='high';
  ctx.drawImage(source,(240-width)*Number(horizontal.value)/100,(320-height)*Number(vertical.value)/100,width,height);
  artwork=preview.toDataURL('image/jpeg',.9);zoomValue.textContent=zoom.value+'%';
 }
 for(const control of [zoom,horizontal,vertical])control.oninput=drawCover;
 const center=action('Centralizar',()=>{horizontal.value=vertical.value=50;drawCover()});center.id='cover-center';
 preview.onpointerdown=e=>{if(!source)return;e.preventDefault();drag=[e.clientX,e.clientY];preview.setPointerCapture(e.pointerId)};
 preview.onpointermove=e=>{if(!drag||!preview.hasPointerCapture(e.pointerId))return;const r=preview.getBoundingClientRect();const scale=Math.max(240/source.naturalWidth,320/source.naturalHeight)*Number(zoom.value)/100;
  const excessX=source.naturalWidth*scale-240,excessY=source.naturalHeight*scale-320;
  if(excessX>0)horizontal.value=Math.max(0,Math.min(100,Number(horizontal.value)-(e.clientX-drag[0])*240/r.width/excessX*100));
  if(excessY>0)vertical.value=Math.max(0,Math.min(100,Number(vertical.value)-(e.clientY-drag[1])*320/r.height/excessY*100));
  drag=[e.clientX,e.clientY];drawCover();
 };
 preview.onlostpointercapture=()=>drag=null;
 crop.append(preview,caption,zoomLabel,zoom,xLabel,horizontal,yLabel,vertical,center);
 const notes=make('textarea');notes.placeholder='Anotações da aventura';notes.value=meta.notes||'';notes.maxLength=5000;
 const save=action('Salvar',()=>{try{updateMeta(id,{name:name.value.trim()||games[id],notes:notes.value,cover:artwork});dialog.close();render()}catch{status.textContent='Sem espaço para salvar a capa. Tente uma imagem menor.'}});save.id='save-game-details';
 cover.onchange=()=>{
  const file=cover.files[0];if(!file)return;const ticket=++serial;
  if(file.size>20*1024*1024){save.disabled=false;status.textContent='Escolha uma imagem de até 20 MB.';return}
  save.disabled=true;status.textContent='Carregando imagem…';const image=new Image(),url=URL.createObjectURL(file);
  image.onload=()=>{URL.revokeObjectURL(url);if(ticket!==serial)return;save.disabled=false;
   if(!image.naturalWidth||image.naturalWidth*image.naturalHeight>40000000){status.textContent='Imagem grande demais. Escolha uma com até 40 megapixels.';return}
   source=image;zoom.value=100;horizontal.value=vertical.value=50;crop.hidden=false;drawCover();status.textContent='Ajuste o recorte e toque em Salvar.';
  };
  image.onerror=()=>{URL.revokeObjectURL(url);if(ticket!==serial)return;save.disabled=false;status.textContent='Não foi possível abrir esta imagem.'};image.src=url;
 };
 if(artwork){const current=new Image();current.onload=()=>{if(source||serial)return;source=current;crop.hidden=false;drawCover()};current.src=artwork}
 detail.append(name,make('p','Capa personalizada · disponível offline'),cover,status,crop,notes,make('p','Sessões iniciadas: '+(meta.sessions||0)),make('p','Tempo jogado: '+Math.floor((meta.seconds||0)/60)+' min'),save);
}
function render(){
 const ids=Object.keys(games),q=el('search').value.toLocaleLowerCase(),order=el('sort').value;
 el('romName').textContent=gameMeta(info.id).name||info.name;el('list').replaceChildren();
 el('library-count').textContent=ids.length;
 const hero=el('hero-cover');hero.replaceChildren();if(gameMeta(info.id).cover){const img=make('img');img.src=gameMeta(info.id).cover;img.alt='';hero.append(img)}else hero.append(sectionIcon('controller'));
 const recent=ids.filter(id=>gameMeta(id).last).sort((a,b)=>gameMeta(b).last-gameMeta(a).last).slice(0,5);
 el('recent').hidden=!recent.length;
 el('recent-games').replaceChildren(...recent.map(id=>action(gameMeta(id).name||games[id],()=>Android.select(id))));
 for(const [system,label] of [['gba','game-count'],['nds','nds-count']]){const count=ids.filter(id=>(systems[id]||'gba')===system).length;el(label).textContent=count+(count===1?' jogo':' jogos');}
 const rows=ids.map(id=>({id,...gameMeta(id),name:gameMeta(id).name||games[id]})).filter(g=>g.name.toLocaleLowerCase().includes(q)&&(!el('fav').checked||g.star)&&(!el('system-filter').value||(systems[g.id]||'gba')===el('system-filter').value));
 rows.sort((a,b)=>order==='name'?a.name.localeCompare(b.name,'pt-BR'):order==='time'?(b.seconds||0)-(a.seconds||0):(b.last||0)-(a.last||0));
 for(const g of rows){const row=make('article');row.className='gamecard';const b=action(g.name,()=>Android.select(g.id));b.className='title';b.append(make('small',(systems[g.id]==='nds'?'Nintendo DS':'GBA')));b.append(make('small',g.last?'Última sessão · '+new Date(g.last).toLocaleDateString('pt-BR'):'Importado'));const star=action(g.star?'★':'☆',()=>{updateMeta(g.id,{star:!g.star});render()});star.className='favorite-button';star.setAttribute('aria-pressed',String(!!g.star));star.setAttribute('aria-label',g.star?'Remover favorito':'Favoritar');const art=action('',()=>Android.select(g.id));art.className='game-cover';art.setAttribute('aria-label','Jogar '+g.name);if(g.cover){const image=make('img');image.src=g.cover;image.alt='Capa de '+g.name;art.append(image)}else{art.append(sectionIcon('controller'),make('span',g.name.replace(/\.[^.]+$/,'').slice(0,24)))}row.append(art,b,star,action('Detalhes',()=>edit(g.id)));el('list').append(row)}
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

