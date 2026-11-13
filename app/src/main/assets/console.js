const deck=el('console'),panel=el('settings-panel'),profileKey='pocket:'+rom.id;
const defaults={theme:'ameixa',orientation:'auto',preset:'classic',volume:75,filter:'original',brightness:100,saturation:100,fit:'contain',speed:'1',interval:5,positions:{},haptic:true};
let settings=Object.assign({},defaults,read(profileKey,{})),editing=false,activeSeconds=0;
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
function gameAction(label,fn){quick.append(action(label,async()=>{const e=emulator();if(!e?.started){say('Inicie o jogo primeiro');return}try{await fn(e)}catch{say('Não foi possível concluir a ação')}}))}
gameAction('Pausar / continuar',e=>{e.paused?e.play():e.pause();say(e.paused?'Pausado':'Jogando');toggleSettings(false)});
gameAction('Capturar tela',async e=>{const blob=new Blob([await e.gameManager.screenshot()],{type:'image/png'});await photos('readwrite',s=>s.put({time:Date.now(),name:rom.name,blob}));say('Captura na galeria local')});
gameAction('Reiniciar',e=>{if(confirm('Reiniciar o jogo agora?')){saveGame(false);e.gameManager.restart();toggleSettings(false)}});
quick.append(action('Biblioteca',()=>leaveGame()),action('Mover controles',()=>{settings.show=true;const box=el('show-controls');if(!box.checked)box.click();editing=true;document.body.classList.add('editing');toggleSettings(false);editDone.hidden=false;say('Arraste os controles e toque em Concluir')}));
const editDone=action('Concluir',()=>{editing=false;document.body.classList.remove('editing');editDone.hidden=true;persist();say('Posições salvas')});editDone.style.cssText='position:absolute;z-index:23;top:2px;left:42%';editDone.hidden=true;document.body.append(editDone);
const choices=(...items)=>items.map(item=>item.split('|'));
setting('preset','Posição dos botões',choices('classic|Clássica','left|Canhoto','compact|Compacta'));
setting('theme','Cor do console',choices('ameixa|Ameixa','brasa|Brasa','jade|Jade','noite|Meia-noite'));
setting('orientation','Orientação',choices('auto|Automática','vertical|Em pé','horizontal|Deitado'));
setting('fit','Enquadramento',choices('contain|Proporção original','cover|Preencher com corte','fill|Esticar'));
setting('filter','Imagem',choices('original|Original','lcd|LCD','crt|CRT','mono|Monocromática','warm|Quente'));
setting('brightness','Brilho da imagem',[50,150,5],'range');setting('saturation','Intensidade das cores',[0,170,5],'range');
setting('volume','Volume',[0,100,5],'range');
setting('speed','Velocidade',choices('1|Normal','2|2×','3|3×'));
setting('interval','Intervalo de gravação',choices('5|5 segundos','15|15 segundos','30|30 segundos'));
for(const [key,label] of Object.entries({mute:'Silenciar áudio',focus:'Tela cheia',haptic:'Vibração dos botões',contrast:'Alto contraste',bigtext:'Texto ampliado no menu',clock:'Relógio no cabeçalho',timer:'Tempo da sessão',swap:'Trocar A e B',diagonal:'Permitir diagonais no direcional'})){
 if(key==='diagonal'&&settings[key]===undefined)settings[key]=true;setting(key,label,[],'checkbox')
}
const clock=make('span');clock.id='timer';el('app-header').querySelector('.brand-line').append(clock);
el('reopen').onclick=()=>toggleSettings(true);
quick.append(action('Restaurar posições',()=>{settings.positions={};persist();place()}));
const ids=['D','A','B','L','R','Select','Start'],codes={A:8,B:0,L:10,R:11,Select:2,Start:3};
const landscape=matchMedia('(orientation: landscape)');
const buttons={},held=new Map(),visits=new Map();
function inputs(pointer,next){const old=held.get(pointer)||[];held.set(pointer,next);const gm=emulator()?.gameManager;if(!gm)return;for(const n of new Set([...old,...next])){const now=[...held.values()].some(list=>list.includes(n));if(visits.get(n)!==now){gm.simulateInput(0,n,now?1:0);visits.set(n,now)}}if(!next.length)held.delete(pointer)}
function keyLayout(){return (landscape.matches?'wide':'tall')+settings.preset}
function place(){
 let points=landscape.matches?[[10,56],[94,52],[85,68],[9,16],[91,16],[7,88],[93,88]]:[[22,52],[85,43],[69,58],[16,16],[84,16],[40,83],[60,83]];
 ids.forEach((id,i)=>{let [x,y]=points[i];if(settings.preset==='left')x=100-x;if(settings.preset==='compact'){y=50+(y-50)*.78;x=x<50?x+2:x-2}
 const saved=settings.positions[keyLayout()]?.[id];if(saved)[x,y]=saved;const b=buttons[id],rect=deck.getBoundingClientRect(),scale=Number(el('control-size').value)/100;
 const limit=(v,size,dim)=>dim?Math.max(size*50/dim,Math.min(100-size*50/dim,v)):v;
 b.style.left=limit(x,b.offsetWidth*scale,rect.width)+'%';b.style.top=limit(y,b.offsetHeight*scale,rect.height)+'%';
 })
}
for(const id of ids){const b=make('button',id==='D'?'✦':id);b.className='pad '+(id==='D'?'cross':id==='A'||id==='B'?'action':'slim');b.setAttribute('aria-label',id==='D'?'Direcional':id);deck.append(b);buttons[id]=b;
 function update(event){
 if(editing){const r=deck.getBoundingClientRect(),group=settings.positions[keyLayout()]||(settings.positions[keyLayout()]={});group[id]=[100*(event.clientX-r.left)/r.width,100*(event.clientY-r.top)/r.height];place();return}
 if(!emulator()?.started)return;
 if(id!=='D'){inputs(event.pointerId,[codes[settings.swap&&id==='A'?'B':settings.swap&&id==='B'?'A':id]]);return}
 const r=b.getBoundingClientRect(),x=(event.clientX-r.left)/r.width-.5,y=(event.clientY-r.top)/r.height-.5;
 const horizontal=Math.abs(x)>.10&&(settings.diagonal||Math.abs(x)>Math.abs(y)),vertical=Math.abs(y)>.10&&(settings.diagonal||!horizontal);
 inputs(event.pointerId,[...(horizontal?[x<0?6:7]:[]),...(vertical?[y<0?4:5]:[])])
 }
 b.onpointerdown=e=>{e.preventDefault();b.setPointerCapture(e.pointerId);b.classList.add('pressed');if(settings.haptic)Android.pulse();update(e)};
 b.onpointermove=e=>{if(b.hasPointerCapture(e.pointerId))update(e)};
 b.onlostpointercapture=e=>{inputs(e.pointerId,[]);b.classList.remove('pressed');if(editing)persist()}
}
function apply(){
 const palettes={ameixa:['#120f1e','#251d37','#ffb65e'],brasa:['#221015','#382026','#ff996a'],jade:['#0c2021','#153638','#8fe2c3'],noite:['#080d19','#1a2438','#a9caff']};
 const palette=palettes[settings.theme]||palettes.ameixa;['--base','--panel','--accent'].forEach((key,i)=>document.documentElement.style.setProperty(key,palette[i]));
 for(const key of ['focus','contrast','bigtext'])document.body.classList.toggle(key,!!settings[key]);document.body.classList.toggle('scan',['lcd','crt'].includes(settings.filter));
 const canvas=emulator()?.canvas;if(canvas){canvas.style.setProperty('object-fit',settings.fit,'important');canvas.style.setProperty('image-rendering',el('sharp-image').checked?'pixelated':'auto','important');canvas.style.filter='brightness('+settings.brightness+'%) saturate('+settings.saturation+'%) '+({mono:'grayscale(1)',warm:'sepia(.35)',lcd:'contrast(.9)',crt:'contrast(1.18)'}[settings.filter]||'')}
 Android.orientation(settings.orientation==='vertical'?1:settings.orientation==='horizontal'?2:0);Android.immersive(!!settings.focus);
 const e=emulator();if(e?.started){e.volume=settings.mute?0:settings.volume/100;e.setVolume(e.volume);e.gameManager.setFastForwardRatio(+settings.speed);e.gameManager.toggleFastForward(+settings.speed>1?1:0)}
 place()
}
el('sharp-image').addEventListener('change',apply);el('control-size').addEventListener('input',place);
new ResizeObserver(place).observe(deck);apply();
window.EJS_onGameStart=()=>{updateMeta(rom.id,{last:Date.now(),sessions:(gameMeta(rom.id).sessions||0)+1});apply();say('Jogando offline');setInterval(()=>{
 const e=emulator();if(document.hidden||!e?.started||e.paused)return;activeSeconds++;updateMeta(rom.id,{seconds:(gameMeta(rom.id).seconds||0)+1});if(activeSeconds%Number(settings.interval)===0)saveGame(false);
 clock.textContent=[settings.clock?new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'',settings.timer?Math.floor(activeSeconds/60)+'m '+activeSeconds%60+'s':''].filter(Boolean).join(' · ')
 },1000)};
