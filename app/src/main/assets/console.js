const deck=el('console'),panel=el('settings-panel'),profileKey='pocket:'+rom.id;
deck.setAttribute('aria-label',isDS?'Controle Nintendo DS':'Controle GBA');
const defaults={skin:'round',accent:'tema',focus:true,theme:'ameixa',orientation:'horizontal',preset:'classic',volume:75,filter:'original',brightness:100,saturation:100,fit:'contain',speed:'1',interval:1,positions:{},sizes:{},screen:'full',haptic:true};
let settings=Object.assign({},defaults,read(profileKey,{})),editing=false,activeSeconds=0;
// Restore this ROM's orientation instead of overwriting its profile.
if(['cover','fill'].includes(settings.fit))settings.fit='contain';
if(settings.batterySaveVersion!==1){settings.interval=1;settings.batterySaveVersion=1;}
settings.positions=settings.positions||{};settings.sizes=settings.sizes||{};
settings.theme=read('fire-game-theme',settings.theme);delete settings.accent;
// Older floating shortcuts had unrelated positions; align just those once.
if(settings.toolsVersion!==2){for(const layout of Object.values(settings.positions))for(const id of ['reopen','speed-button','restore-shortcut','save-shortcut'])delete layout[id];settings.toolsVersion=2;persist();}
if(isDS&&settings.dsScreenVersion!==1){settings.dsMode='single';settings.dsActive='top';settings.screen='full';settings.focus=true;settings.dsScreenVersion=1;persist();}
const emulator=()=>window.EJS_emulator;
const say=text=>{el('status').textContent=text};
function persist(){write(profileKey,settings)}
function setting(key,label,choices,type='select'){
 const row=make('div');row.className='setting';const lab=make('label',label),field=make(type==='select'?'select':'input');field.id='pref-'+key;
 if(type==='select'){for(const [value,title] of choices){const o=make('option',title);o.value=value;field.append(o)}field.value=settings[key]}
 else if(type==='checkbox'){field.type=type;field.checked=!!settings[key]}
 else{field.type='range';field.min=choices[0];field.max=choices[1];field.step=choices[2];field.value=settings[key]}
 field.oninput=()=>{settings[key]=type==='checkbox'?field.checked:type==='range'?+field.value:field.value;persist();apply()};lab.append(field);row.append(lab);panel.append(row)
}
const quick=make('div');quick.id='quick';panel.querySelector('.panel-head').after(quick);
const checkpoints=new Promise((resolve,reject)=>{
 const req=indexedDB.open('fire-game-checkpoints',2);
 req.onupgradeneeded=()=>{
  const history=req.result.createObjectStore('history',{keyPath:['game','time']});
  if(req.result.objectStoreNames.contains('games')){
   const cursor=req.transaction.objectStore('games').openCursor();
   cursor.onsuccess=()=>{const c=cursor.result;if(c){history.put({...c.value,game:c.key});c.continue()}};
  }
 };
 req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);
});
const saveRange=IDBKeyRange.bound([rom.id,0],[rom.id,Number.MAX_SAFE_INTEGER]);
async function saveQuery(mode,operation){
 const db=await checkpoints;
 return new Promise((resolve,reject)=>{
  const tx=db.transaction('history',mode),req=operation(tx.objectStore('history'));
  tx.oncomplete=()=>resolve(req.result);tx.onabort=()=>reject(tx.error);
 });
}
async function checkpoint(){const c=await saveQuery('readonly',s=>s.openCursor(saveRange,'prev'));return c?.value}
const historyBox=make('dialog');historyBox.id='save-history';document.body.append(historyBox);
async function historyView(){
 const keys=await saveQuery('readonly',s=>s.getAllKeys(saveRange));historyBox.replaceChildren(make('h2','Todos os saves'));
 for(const key of keys.reverse()){
  const item=await saveQuery('readonly',s=>s.get(key));if(!item)continue;
  const title=item.name||new Date(item.time).toLocaleString('pt-BR');
  const row=action(title,()=>{if(!longPress){historyBox.close();useCheckpoint(false,key)}});
  if(item.picture){const img=make('img');img.src=item.picture;img.alt='';row.prepend(img)}
  let timer,longPress=false;
  function manage(){longPress=true;historyBox.replaceChildren(make('h2','Editar save'));const field=make('input');field.value=title;field.maxLength=80;field.setAttribute('aria-label','Nome do save');historyBox.append(field,action('Renomear',async()=>{item.name=field.value.trim();await saveQuery('readwrite',s=>s.put(item));historyView()}),action('Apagar',async()=>{if(confirm('Apagar este save?')){await saveQuery('readwrite',s=>s.delete(key));hasCheckpoint=!!await checkpoint();refreshCheckpoint();feedback.textContent=hasCheckpoint?'Save disponível.':'Nenhum ponto salvo.'}historyView()}),action('Voltar',historyView))}
  row.oncontextmenu=e=>{e.preventDefault();clearTimeout(timer);manage()};
  row.onpointerdown=()=>{longPress=false;timer=setTimeout(manage,550)};
  row.onpointerup=row.onpointercancel=row.onpointerleave=()=>clearTimeout(timer);
  historyBox.append(row);
 }
 if(!keys.length)historyBox.append(make('p','Nenhum save para este jogo.'));
 const erase=action('Apagar todos os saves',confirmEraseSaves);erase.id='erase-all-saves';erase.className='danger-button';historyBox.append(erase,action('Fechar',()=>historyBox.close()));if(!historyBox.open)historyBox.showModal();
}
function confirmEraseSaves(){
 if(checkpointBusy)return;
 historyBox.replaceChildren(make('h2','Apagar todos os saves?'));
 const description=make('p','Apaga os pontos criados pelo botão Salvar do emulador. O progresso salvo dentro do jogo, suas ROMs e capas serão mantidos.');
 description.className='delete-description';
 const scope=make('select');scope.id='erase-scope';scope.setAttribute('aria-label','Quais saves apagar');
 for(const [value,label] of [['game','Somente deste jogo'],['all','De todos os jogos']]){const option=make('option',label);option.value=value;scope.append(option)}
 const warning=make('p','Esta ação não pode ser desfeita. Exporte um backup antes se quiser guardar esses pontos.');
 warning.className='delete-warning';
 const cancel=action('Cancelar',historyView);cancel.id='cancel-erase-saves';
 const confirm=action('Apagar definitivamente',async()=>{
  if(checkpointBusy)return;checkpointBusy=true;refreshCheckpoint();confirm.disabled=cancel.disabled=scope.disabled=true;
  try{
   const db=await checkpoints;const stores=['history'];if(db.objectStoreNames.contains('games'))stores.push('games');
   await new Promise((resolve,reject)=>{
    const tx=db.transaction(stores,'readwrite');tx.oncomplete=resolve;tx.onabort=()=>reject(tx.error);
    if(scope.value==='all'){for(const name of stores)tx.objectStore(name).clear()}
    else{tx.objectStore('history').delete(saveRange);if(stores.includes('games'))tx.objectStore('games').delete(rom.id)}
   });
   hasCheckpoint=!!await checkpoint();feedback.textContent='Saves apagados. Progresso do cartucho preservado.';
   await historyView();
  }catch{warning.textContent='Não foi possível apagar os saves. Tente novamente.';confirm.disabled=cancel.disabled=scope.disabled=false}
  finally{checkpointBusy=false;refreshCheckpoint()}
 });confirm.id='confirm-erase-saves';confirm.className='danger-button';
 historyBox.append(description,scope,warning,cancel,confirm);cancel.focus();
}
const saveButton=action('Salvar jogo',()=>useCheckpoint(true));
const loadButton=action('Voltar ao save',()=>useCheckpoint(false));
saveButton.id='save-point';loadButton.id='load-point';
saveButton.prepend(sectionIcon('backup'));loadButton.prepend(sectionIcon('restore'));
const feedback=make('small');feedback.id='checkpoint-status';feedback.setAttribute('role','status');
const allButton=action("Todos os saves",historyView);allButton.id="all-points";quick.append(saveButton,loadButton,allButton,feedback);
let hasCheckpoint=false,checkpointBusy=false;
function refreshCheckpoint(){
 saveButton.disabled=checkpointBusy||!emulator()?.started;
 loadButton.disabled=saveButton.disabled||!hasCheckpoint;const shortcut=el('restore-shortcut');if(shortcut)shortcut.disabled=loadButton.disabled;const saveShortcut=el('save-shortcut');if(saveShortcut)saveShortcut.disabled=saveButton.disabled;
}
async function useCheckpoint(saving,key){
 const e=emulator();
 if(checkpointBusy||!e?.started)return;
 checkpointBusy=true;refreshCheckpoint();
 try{
  if(saving){
   const bytes=e.gameManager.getState();
   if(!bytes?.byteLength)throw Error();
   const time=Date.now();
   const imageBytes=await e.gameManager.screenshot();
   const picture='data:image/png;base64,'+toBase64(imageBytes);
   await saveQuery('readwrite',s=>s.add({game:rom.id,time,bytes,picture}));hasCheckpoint=true;
   feedback.textContent='Jogo salvo neste aparelho.';
  }else{
   const point=key?await saveQuery("readonly",s=>s.get(key)):await checkpoint();
   if(!point?.bytes?.byteLength)throw Error();
   e.gameManager.loadState(point.bytes);
   feedback.textContent='Save restaurado.';toggleSettings(false);
  }
 }catch{feedback.textContent=saving?'Falha ao salvar. Tente novamente.':'Falha ao carregar o save.'}
 finally{checkpointBusy=false;refreshCheckpoint()}
}
refreshCheckpoint();
checkpoint().then(point=>{
 hasCheckpoint=!!point;feedback.textContent=point?'Último save · '+new Date(point.time).toLocaleString('pt-BR'):'Nenhum ponto salvo para este jogo.';
 refreshCheckpoint();
}).catch(()=>{feedback.textContent='Armazenamento de saves indisponível.'});
const restart=action('Reiniciar',()=>{const e=emulator();if(e?.started&&confirm('Reiniciar o jogo agora?')){saveGame(false);e.gameManager.restart();toggleSettings(false)}});
const library=action('Biblioteca',()=>leaveGame());
const move=action('Mover e redimensionar botões',()=>startEditing());move.id='edit-controls';
const editor=make('section');editor.id='layout-editor';editor.hidden=true;editor.setAttribute('aria-label','Editor dos botões');
const editorHead=make('div');editorHead.className='editor-head';editorHead.append(make('strong','Editar botões'));
const editDone=action('Concluir',()=>finishEditing());editDone.id='edit-done';editorHead.append(editDone);
const editorSelect=make('select');editorSelect.id='edit-target';editorSelect.setAttribute('aria-label','Botão selecionado');
const resizeLabel=make('label','Tamanho individual');resizeLabel.htmlFor='edit-size';
const resizeValue=make('output','100%');resizeValue.id='edit-size-value';resizeLabel.append(resizeValue);
const resizeInput=make('input');resizeInput.id='edit-size';resizeInput.type='range';resizeInput.min=60;resizeInput.max=200;resizeInput.step=5;
editor.append(editorHead,make('small','Toque no botão para editar. Arraste o título para mover este painel.'),editorSelect,resizeLabel,resizeInput);document.body.append(editor);
// The editor itself can move so no gameplay control becomes unreachable.
let editorDrag=null;
editorHead.style.touchAction='none';editorHead.style.cursor='grab';editorHead.title='Arraste para mover o painel';
editorHead.onpointerdown=event=>{if(event.target.closest('button'))return;event.preventDefault();const r=editor.getBoundingClientRect();editorDrag=[event.clientX-r.left,event.clientY-r.top];editorHead.setPointerCapture(event.pointerId);};
editorHead.onpointermove=event=>{if(!editorDrag||!editorHead.hasPointerCapture(event.pointerId))return;const r=editor.getBoundingClientRect();editor.style.left=Math.max(4,Math.min(innerWidth-r.width-4,event.clientX-editorDrag[0]))+'px';editor.style.top=Math.max(4,Math.min(innerHeight-r.height-4,event.clientY-editorDrag[1]))+'px';editor.style.bottom='auto';editor.style.transform='none';};
editorHead.onlostpointercapture=()=>{editorDrag=null;};
let selectedControl='A';
const controlNames={D:'Direcional',A:'Botão A',B:'Botão B',X:'Botão X',Y:'Botão Y',Mic:'Microfone',L:'Botão L',R:'Botão R',Select:'Select',Start:'Start','speed-button':'Velocidade','reopen':'Configurações','restore-shortcut':'Voltar ao save','save-shortcut':'Salvar jogo','ds-swap':'Alternar telas DS','ds-popup':'Janela DS'};
function scaleFor(id){const value=Number(settings.sizes[id]);return Math.max(.6,Math.min(2,Number.isFinite(value)&&value?value/100:1));}
function selectedElement(){return buttons[selectedControl]||el(selectedControl);}
function selectControl(id){selectedControl=id;editorSelect.value=id;resizeInput.value=Math.round(scaleFor(id)*100);resizeValue.value=resizeInput.value+'%';resizeValue.textContent=resizeInput.value+'%';resizeInput.setAttribute('aria-label','Tamanho de '+controlNames[id]);document.querySelectorAll('.selected-control').forEach(b=>b.classList.remove('selected-control'));selectedElement()?.classList.add('selected-control');}
function startEditing(){
 const box=el('show-controls');if(!box.checked)box.click();for(const pointer of [...held.keys()])inputs(pointer,[]);
 editing=true;document.body.classList.add('editing');toggleSettings(false);editor.removeAttribute('style');editor.hidden=false;editorSelect.replaceChildren();
 for(const id of [...ids,...floats.filter(b=>!b.hidden).map(b=>b.id)]){const option=make('option',controlNames[id]);option.value=id;editorSelect.append(option);}
 for(const button of floats)if(!button.hidden)button.disabled=false;
 selectControl(editorSelect.querySelector('option[value="'+selectedControl+'"]')?selectedControl:'A');say('Selecione, mova e ajuste o tamanho de cada botão');
}
function finishEditing(){editing=false;document.body.classList.remove('editing');editor.hidden=true;document.querySelectorAll('.selected-control').forEach(b=>b.classList.remove('selected-control'));persist();refreshCheckpoint();say('Posições e tamanhos salvos');}
editorSelect.onchange=()=>selectControl(editorSelect.value);
resizeInput.oninput=()=>{settings.sizes[selectedControl]=Number(resizeInput.value);persist();selectControl(selectedControl);place();};
const choices=(...items)=>items.map(item=>item.split('|'));
setting('shortcut','Voltar ao save na tela',[],'checkbox');
setting('saveShortcut','Salvar jogo na tela',[],'checkbox');
setting('skin','Design do controle',choices('round|Disco','cross|Cruz clássica','arcade|Arcade'));
setting('preset','Posição dos botões',choices('classic|Clássica','left|Canhoto','compact|Compacta'));
setting('theme','Tema completo',Object.entries(themes).map(([id,theme])=>[id,theme.name]));
setting('orientation','Orientação',choices('auto|Automática','vertical|Em pé','horizontal|Deitado'));
setting('screen','Modo de tela',choices('console|Console · controles separados','full|Tela cheia · imagem máxima'));
if(isDS){setting('dsMode','Telas do Nintendo DS',choices('single|Uma tela ampliada','overlay|Segunda tela em janela','dual|Duas telas lado a lado / empilhadas'));setting('dsWindowSize','Tamanho da janela DS',[18,45,1],'range');}
setting('fit','Enquadramento',isDS?choices('contain|Proporção original'):choices('contain|Máximo · imagem inteira','integer|Pixels exatos · escala inteira'));
setting('filter','Imagem',choices('original|Original','lcd|LCD','crt|CRT','mono|Monocromática','warm|Quente'));
setting('brightness','Brilho da imagem',[50,150,5],'range');setting('saturation','Intensidade das cores',[0,170,5],'range');
setting('volume','Volume',[0,100,5],'range');
setting('interval','Gravação do cartucho',choices('1|1 segundo','5|5 segundos','15|15 segundos','30|30 segundos'));
for(const [key,label] of Object.entries({mute:'Silenciar áudio',focus:'Ocultar cabeçalho e barras',haptic:'Vibração dos botões',contrast:'Alto contraste',bigtext:'Texto ampliado no menu',clock:'Relógio no cabeçalho',timer:'Tempo da sessão',swap:'Trocar A e B',diagonal:'Permitir diagonais no direcional'})){
 if(key==='diagonal'&&settings[key]===undefined)settings[key]=false;setting(key,label,[],'checkbox')
}
const clock=make('span');clock.id='timer';el('app-header').querySelector('.brand-line').append(clock);
el('reopen').onclick=()=>toggleSettings(true);
const reset=action('Restaurar posições e tamanhos',()=>{settings.positions={};settings.sizes={};persist();place()});
function addCategory(title, icon, fields, actions=[]) {
 const group=make('details');group.className='category';
 const summary=make('summary',title);summary.prepend(sectionIcon(icon));group.append(summary);panel.append(group);
 for(const field of fields)group.append(el(field).closest('.setting'));
 for(const button of actions)group.append(button);
 group.addEventListener('toggle',()=>{if(group.open)for(const other of panel.querySelectorAll('.category'))if(other!==group)other.open=false});
}

library.prepend(sectionIcon('library'));
for(const id of ['reopen','settings-toggle']){
 const button=el(id);button.replaceChildren(sectionIcon('gear'));
 button.setAttribute('aria-label','Configurações do jogo');
 button.setAttribute('aria-controls','settings-panel');button.setAttribute('aria-expanded','false');
}
addCategory('Tela e vídeo','screen',[...(isDS?['pref-dsMode','pref-dsWindowSize']:[]),'pref-screen','pref-focus','pref-orientation','pref-fit','pref-filter','sharp-image','pref-brightness','pref-saturation']);
addCategory('Áudio','sound',['pref-volume','pref-mute']);
addCategory('Controles','controller',['pref-shortcut','pref-saveShortcut','pref-skin','pref-preset','control-size','control-opacity','show-controls','pref-haptic','pref-swap','pref-diagonal'],[move,reset]);
addCategory('Aparência e acessibilidade','palette',['pref-theme','pref-contrast','pref-bigtext']);
const dsActions=[];
if(isDS){
 const lid=action('Abrir/fechar tampa do DS',()=>{
  const game=emulator()?.gameManager;if(!game||!emulator().started)return;
  lid.disabled=true;game.simulateInput(0,12,1);
  setTimeout(()=>game.simulateInput(0,12,0),100);
  setTimeout(()=>{lid.disabled=false;},1000);
 });
 lid.id='ds-lid';dsActions.push(lid);
}
addCategory('Avançado','sliders',['pref-interval','pref-clock','pref-timer'],[...dsActions,library,restart]);
const speedButton=el('speed-button');
const restoreShortcut=action('',()=>useCheckpoint(false));restoreShortcut.append(sectionIcon('restore'));restoreShortcut.id='restore-shortcut';restoreShortcut.setAttribute('aria-label','Voltar ao save');document.body.append(restoreShortcut);
const saveShortcut=action('',()=>useCheckpoint(true));saveShortcut.id='save-shortcut';saveShortcut.append(sectionIcon('backup'));saveShortcut.setAttribute('aria-label','Salvar jogo');document.body.append(saveShortcut);
const floats=[el('reopen'),speedButton,restoreShortcut,saveShortcut];
initDSDisplay();
function placeTools(){
 const visible=floats.filter(button=>!button.hidden);
 const widths=visible.map(button=>46*scaleFor(button.id));
 const total=widths.reduce((sum,width)=>sum+width,0)+Math.max(0,visible.length-1)*8;
 let offset=(innerWidth-total)/2;
 const centers=new Map();
 visible.forEach((button,i)=>{centers.set(button.id,offset+widths[i]/2);offset+=widths[i]+8;});
 const rowMargin=Math.max(23,...widths.map(width=>width/2))+8;
 const rowTop=!landscape.matches&&document.body.dataset.screen==='console'?el('game').getBoundingClientRect().bottom+rowMargin:rowMargin;
 for(const button of floats){
  const pos=settings.positions[keyLayout()]?.[button.id];
  const scale=scaleFor(button.id),margin=26*scale;
  button.style.setProperty('--button-scale',scale);
  const x=pos?innerWidth*pos[0]/100:centers.get(button.id)||innerWidth/2;
  const y=pos?innerHeight*pos[1]/100:rowTop;
  button.style.setProperty('left',Math.max(margin,Math.min(innerWidth-margin,x))+'px','important');
  button.style.setProperty('top',Math.max(margin,Math.min(innerHeight-margin,y))+'px','important');
 }
}
for(const b of floats){
 b.classList.add('floating-tool');
 b.addEventListener('click',e=>{if(editing){e.preventDefault();e.stopImmediatePropagation()}},true);
 b.onpointerdown=e=>{if(editing){selectControl(b.id);b.setPointerCapture(e.pointerId)}};
 b.onlostpointercapture=()=>{if(editing)persist()};
 b.onpointermove=e=>{if(!editing||!b.hasPointerCapture(e.pointerId))return;
 const group=settings.positions[keyLayout()]||(settings.positions[keyLayout()]={});
 group[b.id]=[100*e.clientX/innerWidth,100*e.clientY/innerHeight];placeTools()};
}
function updateSpeed(){
 restoreShortcut.hidden=!settings.shortcut;saveShortcut.hidden=!settings.saveShortcut;document.body.dataset.skin=settings.skin;
 document.body.dataset.fit=settings.fit;

 const speed=Number(settings.speed);
 speedButton.textContent=speed+'×';
 speedButton.setAttribute('aria-label','Velocidade '+speed+'×. Toque para mudar.');
 const e=emulator();
 if(e?.started){e.gameManager.setFastForwardRatio(speed);e.gameManager.toggleFastForward(speed>1?1:0)}
}
speedButton.onclick=()=>{settings.speed=String(Number(settings.speed)%3+1);persist();updateSpeed()};
const ids=['D','A','B','L','R','Select','Start',...(isDS?['X','Y','Mic']:[])],codes={A:8,B:0,X:9,Y:1,Mic:14,L:10,R:11,Select:2,Start:3};
const landscape=matchMedia('(orientation: landscape)');
const buttons={},held=new Map(),visits=new Map();
function inputs(pointer,next){const old=held.get(pointer)||[];held.set(pointer,next);const gm=emulator()?.gameManager;if(!gm)return;for(const n of new Set([...old,...next])){const now=[...held.values()].some(list=>list.includes(n));if(visits.get(n)!==now){gm.simulateInput(0,n,now?1:0);visits.set(n,now)}}if(!next.length)held.delete(pointer)}
function keyLayout(){return (landscape.matches?'wide':'tall')+settings.preset}
function place(){
 placeTools();
 let points=landscape.matches?[[10,56],[94,52],[85,68],[9,16],[91,16],[7,88],[93,88]]:[[23,52],[84,45],[66,60],[16,25],[84,25],[40,85],[60,85]];
 if(isDS)points=landscape.matches?[[9,55],[96,53],[90,72],[8,18],[92,18],[8,89],[92,89],[90,35],[84,53],[50,89]]:[[23,53],[87,51],[72,69],[16,25],[84,25],[40,88],[60,88],[72,34],[57,51],[50,25]];
 ids.forEach((id,i)=>{let [x,y]=points[i];if(settings.preset==='left')x=100-x;if(settings.preset==='compact'){y=50+(y-50)*.78;x=x<50?x+2:x-2}
 const saved=settings.positions[keyLayout()]?.[id];if(saved)[x,y]=saved;const b=buttons[id],rect=deck.getBoundingClientRect(),scale=Number(el('control-size').value)/100*scaleFor(id);b.style.setProperty('--button-scale',scaleFor(id));
 const limit=(v,size,dim)=>dim?Math.max(size*50/dim,Math.min(100-size*50/dim,v)):v;
 b.style.left=limit(x,b.offsetWidth*scale,rect.width)+'%';b.style.top=limit(y,b.offsetHeight*scale,rect.height)+'%';
 })
}
function directionCodes(x,y,diagonal){
 const ax=Math.abs(x),ay=Math.abs(y);
 if(Math.max(ax,ay)<.07)return [];
 const horizontal=x<0?6:7,vertical=y<0?4:5;
 if(diagonal&&Math.min(ax,ay)>.63*Math.max(ax,ay))return [horizontal,vertical];
 return ax>ay?[horizontal]:[vertical];
}
for(const id of ids){const b=make('button',id==='D'?'':id==='Mic'?'Mic':id);b.className='pad '+(id==='D'?'cross':['A','B','X','Y'].includes(id)?'action':'slim');b.setAttribute('aria-label',id==='D'?'Direcional':id);deck.append(b);buttons[id]=b;
 if(id==='D'){
  for(const [code,arrow] of [[4,'▴'],[5,'▾'],[6,'◂'],[7,'▸']]){
   const mark=make('span',arrow);mark.dataset.code=code;mark.setAttribute('aria-hidden','true');b.append(mark);
  }
 }
 function update(event){
 if(editing){selectControl(id);const r=deck.getBoundingClientRect(),group=settings.positions[keyLayout()]||(settings.positions[keyLayout()]={});group[id]=[100*(event.clientX-r.left)/r.width,100*(event.clientY-r.top)/r.height];place();return}
 if(!emulator()?.started)return;
 if(id!=='D'){inputs(event.pointerId,[codes[settings.swap&&id==='A'?'B':settings.swap&&id==='B'?'A':id]]);return}
 const r=b.getBoundingClientRect(),x=(event.clientX-r.left)/r.width-.5,y=(event.clientY-r.top)/r.height-.5;
 const directions=directionCodes(x,y,settings.diagonal);
 inputs(event.pointerId,directions);
 for(const mark of b.children)mark.classList.toggle('lit',directions.includes(+mark.dataset.code));

 }
 b.onpointerdown=e=>{e.preventDefault();b.setPointerCapture(e.pointerId);b.classList.add('pressed');if(settings.haptic)Android.pulse();update(e)};
 b.onpointermove=e=>{if(b.hasPointerCapture(e.pointerId))update(e)};
 b.onlostpointercapture=e=>{inputs(e.pointerId,[]);b.classList.remove('pressed');for(const mark of b.children)mark.classList.remove('lit');if(editing)persist()}
}
const screenChoices=make('section');screenChoices.id='screen-choices';
const screenLabel=make('small','COMO VOCÊ QUER JOGAR?');screenChoices.append(screenLabel);
const screenOptions=make('div');
for(const [mode,title,caption,icon] of [['console','Console','Controles em área própria','controller'],['full','Tela cheia','Imagem inteira, no máximo','expand']]){
 const button=action('',()=>{settings.screen=mode;settings.fit='contain';settings.focus=mode==='full';el('pref-screen').value=mode;el('pref-fit').value='contain';el('pref-focus').checked=settings.focus;persist();apply()});
 button.id='screen-choice-'+mode;button.append(sectionIcon(icon),make('strong',title),make('span',caption));screenOptions.append(button);
}
screenChoices.append(screenOptions);panel.querySelector('.panel-head').after(screenChoices);
const frameNote=make('p','GBA • 3:2 · A imagem inteira é ampliada sem corte ou distorção. Em telas mais largas, as faixas laterais preservam o jogo completo.');frameNote.id='frame-note';if(!isDS)el('pref-fit').closest('.setting').append(frameNote);
function updateScreenChoices(){for(const mode of ['console','full'])el('screen-choice-'+mode).setAttribute('aria-pressed',String(document.body.dataset.screen===mode));}
el('pref-screen').addEventListener('input',()=>{if(settings.screen==='full'){settings.focus=true;el('pref-focus').checked=true;}settings.fit='contain';el('pref-fit').value='contain';persist();apply()});
let observedCanvas;
function updateCanvasCrop(){
 const e=emulator(),canvas=e?.canvas;if(!canvas)return;
 if(isDS)return;
 const box=el('game').getBoundingClientRect(),dpr=devicePixelRatio||1;
 const integer=Math.floor(Math.min(box.width*dpr/240,box.height*dpr/160));
 const root=el('game').querySelector('.ejs_parent');
 if(root){root.style.setProperty('--viewport-width',settings.fit==='integer'&&integer>=1?(integer*240/dpr)+'px':'100%');root.style.setProperty('--viewport-height',settings.fit==='integer'&&integer>=1?(integer*160/dpr)+'px':'100%');}
 const aspect=Number(e.gameManager?.getVideoDimensions('aspect'))||1.5;
 const bufferAspect=canvas.width/canvas.height||aspect;
 canvas.style.setProperty('--crop-x',Math.max(1,bufferAspect/aspect));
 canvas.style.setProperty('--crop-y',Math.max(1,aspect/bufferAspect));
 if(observedCanvas!==canvas){observedCanvas=canvas;new MutationObserver(updateCanvasCrop).observe(canvas,{attributes:true,attributeFilter:['width','height']});}
}
function apply(){
 applyTheme(settings.theme);write('fire-game-theme',settings.theme);document.body.dataset.screen=settings.fit==='immersive'?'full':settings.screen;
 for(const key of ['focus','contrast','bigtext'])document.body.classList.toggle(key,!!settings[key]);document.body.classList.toggle('scan',['lcd','crt'].includes(settings.filter));
 const canvas=emulator()?.canvas;if(canvas){canvas.style.setProperty('object-fit','contain','important');canvas.style.setProperty('image-rendering',el('sharp-image').checked?'pixelated':'auto','important');canvas.style.filter='brightness('+settings.brightness+'%) saturate('+settings.saturation+'%) '+({mono:'grayscale(1)',warm:'sepia(.35)',lcd:'contrast(.9)',crt:'contrast(1.18)'}[settings.filter]||'')}
 Android.orientation(settings.orientation==='vertical'?1:settings.orientation==='horizontal'?2:0);Android.immersive(!!settings.focus);
 const e=emulator();
 applyDSDisplay();
 if(e?.started){e.volume=settings.mute?0:settings.volume/100;e.setVolume(e.volume)}
 updateSpeed()
 updateCanvasCrop();place();updateScreenChoices()
}
landscape.addEventListener('change',apply);
el('sharp-image').addEventListener('change',apply);el('control-size').addEventListener('input',place);
new ResizeObserver(place).observe(deck);new ResizeObserver(()=>{updateCanvasCrop();applyDSDisplay()}).observe(el('game'));apply();
function hideEmulatorMenu(){const menu=emulator()?.elements?.menuToggle;if(menu)menu.remove();}
new MutationObserver(hideEmulatorMenu).observe(el('game'),{childList:true,subtree:true});hideEmulatorMenu();
window.EJS_onGameStart=()=>{hideEmulatorMenu();refreshCheckpoint();updateMeta(rom.id,{last:Date.now(),sessions:(gameMeta(rom.id).sessions||0)+1});apply();say('Jogando offline');setInterval(()=>{
 const e=emulator();if(document.hidden||!e?.started||e.paused)return;activeSeconds++;updateMeta(rom.id,{seconds:(gameMeta(rom.id).seconds||0)+1});if(activeSeconds%Number(settings.interval)===0)saveGame(false);
 clock.textContent=[settings.clock?new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'',settings.timer?Math.floor(activeSeconds/60)+'m '+activeSeconds%60+'s':''].filter(Boolean).join(' · ')
 },1000)};

