// DS display modes use one running core; no duplicated emulation or save state.
let dsLayout='',dsWindow,dsPreview,dsSwap,dsPopup,dsFrame=0,dsPointer=null;
function initDSDisplay(){
 if(!isDS)return;
 dsSwap=action('',()=>{settings.dsActive=settings.dsActive==='bottom'?'top':'bottom';persist();apply()});
 dsSwap.id='ds-swap';dsSwap.append(sectionIcon('screen'),make('span','1↔2'));dsSwap.title='Alternar tela do DS';
 dsPopup=action('',()=>{settings.dsMode=settings.dsMode==='overlay'?'single':'overlay';persist();el('pref-dsMode').value=settings.dsMode;apply()});
 dsPopup.id='ds-popup';dsPopup.append(sectionIcon('screen'),make('span','+'));dsPopup.title='Mostrar ou ocultar segunda tela';
 document.body.append(dsSwap,dsPopup);floats.push(dsSwap,dsPopup);
 dsWindow=make('section');dsWindow.id='ds-window';dsWindow.hidden=true;dsWindow.setAttribute('aria-label','Segunda tela do DS');
 const bar=make('div');bar.className='ds-window-bar';bar.title='Arraste para mover a janela';
 const label=make('span','Tela inferior · toque');label.id='ds-window-label';
 const close=make('button','×');close.type='button';close.setAttribute('aria-label','Ocultar segunda tela');close.onclick=()=>dsPopup.click();
 bar.append(label,close);dsPreview=make('canvas');dsPreview.width=256;dsPreview.height=192;dsPreview.setAttribute('aria-label','Tela inferior sensível ao toque');
 dsWindow.append(bar,dsPreview);document.body.append(dsWindow);
 let drag;
 bar.onpointerdown=e=>{if(e.target===close)return;e.preventDefault();bar.setPointerCapture(e.pointerId);const r=dsWindow.getBoundingClientRect();drag=[e.clientX-r.left,e.clientY-r.top];};
 bar.onpointermove=e=>{if(!drag||!bar.hasPointerCapture(e.pointerId))return;settings.dsWindowPosition=[100*(e.clientX-drag[0])/innerWidth,100*(e.clientY-drag[1])/innerHeight];positionDSWindow();};
 bar.onlostpointercapture=()=>{drag=null;persist()};
 dsPreview.addEventListener('pointerdown',e=>{if(settings.dsActive==='bottom'||dsPointer!==null)return;e.preventDefault();dsPointer=e.pointerId;dsPreview.setPointerCapture(e.pointerId);forwardDSTouch(e,'touchstart')});
 dsPreview.addEventListener('pointermove',e=>{if(dsPointer===e.pointerId){e.preventDefault();forwardDSTouch(e,'touchmove')}});
 for(const kind of ['pointerup','pointercancel','lostpointercapture'])dsPreview.addEventListener(kind,e=>{if(dsPointer!==e.pointerId)return;forwardDSTouch(e,'touchend');dsPointer=null;});
 window.addEventListener('blur',releaseDSTouch);
 document.addEventListener('visibilitychange',()=>{if(document.hidden)releaseDSTouch()});
}
function releaseDSTouch(){
 if(dsPointer===null)return;
 const c=emulator()?.canvas;if(c)c.dispatchEvent(new TouchEvent('touchcancel',{bubbles:true,cancelable:true,touches:[],targetTouches:[],changedTouches:[]}));dsPointer=null;
}
function forwardDSTouch(event,type){
 const canvas=emulator()?.canvas;if(!canvas)return;
 const r=dsPreview.getBoundingClientRect(),target=canvas.getBoundingClientRect();
 const x=Math.max(0,Math.min(1,(event.clientX-r.left)/r.width)),y=Math.max(0,Math.min(1,(event.clientY-r.top)/r.height));
 const touch=new Touch({identifier:77,target:canvas,clientX:target.left+x*target.width,clientY:target.top+(0.5+y/2)*target.height});
 const active=type==='touchend'?[]:[touch];
 canvas.dispatchEvent(new TouchEvent(type,{bubbles:true,cancelable:true,touches:active,targetTouches:active,changedTouches:[touch]}));
}
function positionDSWindow(){
 if(!dsWindow||dsWindow.hidden)return;
 const width=Math.max(112,Math.min(innerWidth*.48,innerWidth*(settings.dsWindowSize||28)/100,260));
 dsWindow.style.width=width+'px';
 const height=width*.75+30,pos=settings.dsWindowPosition;
 const x=pos?innerWidth*pos[0]/100:innerWidth-width-16,y=pos?innerHeight*pos[1]/100:72;
 dsWindow.style.left=Math.max(4,Math.min(innerWidth-width-4,x))+'px';
 dsWindow.style.top=Math.max(4,Math.min(innerHeight-height-4,y))+'px';
}
function drawDSPreview(){
 dsFrame=0;if(!isDS||settings.dsMode!=='overlay')return;
 const e=emulator(),c=e?.canvas;
 if(c&&c.width&&c.height&&!document.hidden){
  const context=dsPreview.getContext('2d');context.imageSmoothingEnabled=false;
  context.drawImage(c,0,settings.dsActive==='bottom'?0:c.height/2,c.width,c.height/2,0,0,256,192);
 }
 dsFrame=requestAnimationFrame(drawDSPreview);
}
function applyDSDisplay(){
 if(!isDS)return;
 const mode=settings.dsMode||'single',bottom=settings.dsActive==='bottom';
 document.body.dataset.dsMode=mode;
 dsSwap.setAttribute('aria-label',bottom?'Mostrar tela superior do DS':'Mostrar tela inferior do DS');
 dsSwap.setAttribute('aria-pressed',String(bottom));dsPopup.setAttribute('aria-label',mode==='overlay'?'Ocultar segunda tela':'Mostrar segunda tela em janela');dsPopup.setAttribute('aria-pressed',String(mode==='overlay'));
 dsWindow.hidden=mode!=='overlay';el('ds-window-label').textContent=bottom?'Tela superior':'Tela inferior · toque';
 dsPreview.setAttribute('aria-label',bottom?'Tela superior do DS':'Tela inferior sensível ao toque');
 const e=emulator(),canvas=e?.canvas;if(!e?.started||!canvas)return;
 const layout=mode==='dual'?(landscape.matches?'left/right':'top/bottom'):mode==='overlay'?'top/bottom':bottom?'bottom only':'top only';
 if(dsLayout!==layout){releaseDSTouch();e.gameManager.setVariable('desmume_screens_layout',layout);dsLayout=layout;}
 e.gameManager.setVariable('desmume_pointer_type','touch');
 for(const [key,value] of Object.entries({position:'absolute',left:'0px',top:'0px',width:'100%',height:'100%',transform:'none','clip-path':'none','object-fit':'contain','max-width':'none','max-height':'none'}))canvas.style.setProperty(key,value,'important');
 if(mode==='overlay'){
  const box=el('game'),w=Math.min(box.clientWidth,box.clientHeight*4/3),h=w*3/4;
  for(const [key,value] of Object.entries({left:(box.clientWidth-w)/2+'px',top:((box.clientHeight-h)/2-(bottom?h:0))+'px',width:w+'px',height:2*h+'px','object-fit':'fill','clip-path':bottom?'inset(50% 0 0 0)':'inset(0 0 50% 0)'}))canvas.style.setProperty(key,value,'important');
  positionDSWindow();if(!dsFrame)dsFrame=requestAnimationFrame(drawDSPreview);
 }else{releaseDSTouch();if(dsFrame)cancelAnimationFrame(dsFrame);dsFrame=0;}
}
