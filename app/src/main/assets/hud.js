// Measures emulated frames, not the Android screen refresh rate.
for(const [key,value] of Object.entries({hudBattery:true,hudClock:true,hudPerformance:false}))if(typeof settings[key]!=='boolean')settings[key]=value;
for(const [key,label] of [['hudBattery','Mostrar bateria'],['hudClock','Mostrar horário'],['hudPerformance','Mostrar desempenho (FPS emulados)']])setting(key,label,null,'checkbox');
addCategory('Indicadores na tela','sliders',['pref-hudBattery','pref-hudClock','pref-hudPerformance']);
const gameHud=make('aside');gameHud.id='game-hud';gameHud.setAttribute('aria-label','Indicadores do jogo');
const hudBattery=make('span'),hudClock=make('span'),hudPerformance=make('span');hudBattery.id='hud-battery';hudClock.id='hud-clock';hudPerformance.id='hud-performance';
gameHud.append(hudBattery,hudClock,hudPerformance);document.body.append(gameHud);
let hudSample=null,hudDevice={battery:-1,charging:false},hudBatteryTime=0;
function updateHud(){
 const now=performance.now(),e=emulator(),running=!!e?.started&&!e.paused&&!document.hidden;
 gameHud.hidden=document.hidden||editing||!(settings.hudBattery||settings.hudClock||settings.hudPerformance);
 hudBattery.hidden=!settings.hudBattery;hudClock.hidden=!settings.hudClock;hudPerformance.hidden=!settings.hudPerformance;
 if(settings.hudClock)hudClock.textContent=new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
 if(settings.hudBattery&&(now-hudBatteryTime>30000||!hudBatteryTime)){
  try{hudDevice=JSON.parse(Android.deviceStatus())}catch{}hudBatteryTime=now;
 }
 if(settings.hudBattery){hudBattery.textContent=(hudDevice.charging?'⚡ ':'')+(hudDevice.battery<0?'Bateria —':hudDevice.battery+'%');hudBattery.classList.toggle('low-battery',hudDevice.battery>=0&&hudDevice.battery<=15);hudBattery.setAttribute('aria-label','Bateria '+hudBattery.textContent)}
 if(settings.hudPerformance){
  const frame=running?Number(e.gameManager.getFrameNum()):0;
  if(!running){hudPerformance.textContent=e?.started?'Pausado':'Iniciando';hudSample=null}
  else if(hudSample&&now-hudSample.time>=500){const delta=frame-hudSample.frame;hudPerformance.textContent=delta>=0?Math.round(delta*1000/(now-hudSample.time))+' FPS':'— FPS';hudSample={time:now,frame}}
  else if(!hudSample){hudSample={time:now,frame};hudPerformance.textContent='— FPS'}
 }else hudSample=null;
}
for(const key of ['hudBattery','hudClock','hudPerformance'])el('pref-'+key).addEventListener('input',updateHud);
document.addEventListener('visibilitychange',()=>{hudSample=null;hudBatteryTime=0;updateHud()});
setInterval(updateHud,1000);updateHud();
