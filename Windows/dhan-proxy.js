// Local Reimagined Winner backend. Loads secrets from the adjacent .env file.
// Start with: node dhan-proxy.js   then open http://127.0.0.1:8765/
'use strict';
const http=require('http'),https=require('https'),fs=require('fs'),path=require('path');
const envFile=path.join(__dirname,'.env'),loadedEnvValues=new Map();

function loadEnv(file){
  if(!fs.existsSync(file))return 0;
  let count=0;
  for(const line of fs.readFileSync(file,'utf8').replace(/^\uFEFF/,'').split(/\r?\n/)){
    const s=line.trim();if(!s||s.startsWith('#'))continue;
    const m=s.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);if(!m)continue;
    let value=m[2].trim();
    if(value.startsWith('"')&&value.endsWith('"')){try{value=JSON.parse(value);}catch(e){value=value.slice(1,-1);}}
    else if(value.startsWith("'")&&value.endsWith("'"))value=value.slice(1,-1);
    else value=value.replace(/\s+#.*$/,'').trim();
    if(process.env[m[1]]===undefined)process.env[m[1]]=value;
    loadedEnvValues.set(m[1],value);
    count++;
  }
  return count;
}
const envCount=loadEnv(envFile);
const PORT=+(process.env.PORT||8765),HOST='127.0.0.1';
const UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36';
const CORS={'Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'accept,content-type','Access-Control-Max-Age':'600','Vary':'Origin'};
const allowedOrigins=new Set([`http://${HOST}:${PORT}`,`http://localhost:${PORT}`]);

const val=n=>String(process.env[n]||'').trim();
const has=(...names)=>names.every(n=>!!val(n));
const hint=s=>s?`••••${s.slice(-4)}`:'';
function configStatus(){return{
  ok:true,port:PORT,aiProvider:val('AI_PROVIDER')||'claude',defaultBroker:val('DEFAULT_BROKER').toLowerCase(),
  google:{configured:has('GOOGLE_CLIENT_ID'),clientId:val('GOOGLE_CLIENT_ID')},
  ai:{claude:{configured:has('ANTHROPIC_API_KEY'),model:val('ANTHROPIC_MODEL')||'claude-sonnet-4-20250514'},gemini:{configured:has('GEMINI_API_KEY'),model:val('GEMINI_MODEL')||'gemini-2.5-flash'},openai:{configured:has('OPENAI_API_KEY'),model:val('OPENAI_MODEL')||'gpt-4o-mini'}},
  brokers:{
    dhan:{configured:has('DHAN_CLIENT_ID','DHAN_ACCESS_TOKEN'),clientHint:hint(val('DHAN_CLIENT_ID'))},
    zerodha:{configured:has('ZERODHA_API_KEY','ZERODHA_ACCESS_TOKEN'),clientHint:hint(val('ZERODHA_CLIENT_ID'))},
    groww:{configured:has('GROWW_ACCESS_TOKEN'),clientHint:hint(val('GROWW_CLIENT_ID'))},
    upstox:{configured:has('UPSTOX_ACCESS_TOKEN'),marketData:has('UPSTOX_ANALYTICS_TOKEN')||has('UPSTOX_ACCESS_TOKEN'),orders:has('UPSTOX_ACCESS_TOKEN'),clientHint:hint(val('UPSTOX_CLIENT_ID'))},
    angel:{configured:has('ANGELONE_API_KEY','ANGELONE_ACCESS_TOKEN'),clientHint:hint(val('ANGELONE_CLIENT_ID'))},
    fyers:{configured:has('FYERS_APP_ID','FYERS_ACCESS_TOKEN'),clientHint:hint(val('FYERS_CLIENT_ID'))},
    aliceblue:{configured:has('ALICEBLUE_API_KEY','ALICEBLUE_ACCESS_TOKEN'),clientHint:hint(val('ALICEBLUE_CLIENT_ID'))},
    icicidirect:{configured:has('ICICIDIRECT_API_KEY','ICICIDIRECT_ACCESS_TOKEN'),clientHint:hint(val('ICICIDIRECT_CLIENT_ID'))},
    kotakneo:{configured:has('KOTAKNEO_API_KEY','KOTAKNEO_ACCESS_TOKEN'),clientHint:hint(val('KOTAKNEO_CLIENT_ID'))},
    custom1:{configured:has('BROKER_1_NAME','BROKER_1_BASE_URL','BROKER_1_ACCESS_TOKEN'),name:val('BROKER_1_NAME')},
    custom2:{configured:has('BROKER_2_NAME','BROKER_2_BASE_URL','BROKER_2_ACCESS_TOKEN'),name:val('BROKER_2_NAME')}
  }
};}
function readEnvValues(){
  const values={};if(!fs.existsSync(envFile))return values;
  for(const line of fs.readFileSync(envFile,'utf8').replace(/^\uFEFF/,'').split(/\r?\n/)){
    const s=line.trim();if(!s||s.startsWith('#'))continue;
    const m=s.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);if(!m)continue;
    let value=m[2].trim();if(value.startsWith('"')&&value.endsWith('"')){try{value=JSON.parse(value);}catch(e){value=value.slice(1,-1);}}
    else if(value.startsWith("'")&&value.endsWith("'"))value=value.slice(1,-1);else value=value.replace(/\s+#.*$/,'').trim();
    values[m[1]]=value;
  }
  return values;
}
function writeEnvValues(input){
  if(!input||typeof input!=='object'||Array.isArray(input))throw new Error('Expected an object of environment variables.');
  const entries=Object.entries(input);if(entries.length>200)throw new Error('The environment editor supports at most 200 entries.');
  const values={};let size=0;
  for(const [rawKey,rawValue] of entries){
    const key=String(rawKey).trim();if(!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key))throw new Error('Invalid environment variable name: '+key.slice(0,80));
    const value=String(rawValue==null?'':rawValue);if(value.length>12000)throw new Error(key+' is too long.');size+=key.length+value.length+4;values[key]=value;
  }
  if(size>256*1024)throw new Error('Environment file exceeds 256 KB.');
  const text=Object.entries(values).map(([key,value])=>key+'='+JSON.stringify(value)).join('\n')+'\n';
  const temp=envFile+'.tmp';fs.writeFileSync(temp,text,{encoding:'utf8',mode:0o600});fs.renameSync(temp,envFile);
  for(const [key,oldValue] of loadedEnvValues)if(!(key in values)&&process.env[key]===oldValue)delete process.env[key];
  loadedEnvValues.clear();for(const [key,value] of Object.entries(values)){process.env[key]=value;loadedEnvValues.set(key,value);}
  return Object.keys(values).length;
}
function send(res,status,body,headers={}){res.writeHead(status,{...headers,'Cache-Control':'no-store'});res.end(body);}
function json(res,status,obj){send(res,status,JSON.stringify(obj),{'Content-Type':'application/json; charset=utf-8'});}
function allowed(req){
  const host=String(req.headers.host||'').toLowerCase();
  if(host!==`${HOST}:${PORT}`&&host!==`localhost:${PORT}`)return false;
  const origin=req.headers.origin;
  return !origin||allowedOrigins.has(origin);
}
function readBody(req){return new Promise((resolve,reject)=>{const chunks=[];let size=0;req.on('data',c=>{size+=c.length;if(size>2*1024*1024){reject(new Error('Request body too large'));req.destroy();return;}chunks.push(c);});req.on('end',()=>resolve(Buffer.concat(chunks)));req.on('error',reject);});}
function upstream(req,res,target,headers={},bodyOverride=null){
  readBody(req).then(original=>{
    let body=bodyOverride===null?original:bodyOverride;
    const h={accept:req.headers.accept||'application/json',...headers};
    if(req.headers['content-type'])h['content-type']=req.headers['content-type'];
    if(body.length)h['content-length']=body.length;
    const u=new URL(target),opts={hostname:u.hostname,port:u.port||443,path:u.pathname+u.search,method:req.method,headers:h};
    const up=https.request(opts,r=>{res.writeHead(r.statusCode||502,{'Content-Type':r.headers['content-type']||'application/json','Cache-Control':'no-store'});r.pipe(res);});
    up.on('error',e=>json(res,502,{errorMessage:'proxy: '+e.message}));
    if(body.length)up.write(body);up.end();
  }).catch(e=>json(res,413,{errorMessage:e.message||'Invalid request body'}));
}
function pathAfter(url,prefix){return url.pathname.slice(prefix.length-1)+url.search;}
function brokerRoute(name,req,res,url){
  let host='',prefix='',headers={};
  if(name==='zerodha'){
    if(!has('ZERODHA_API_KEY','ZERODHA_ACCESS_TOKEN'))return json(res,503,{errorMessage:'Zerodha credentials are missing from .env'});
    host='api.kite.trade';headers={'X-Kite-Version':'3','Authorization':'token '+val('ZERODHA_API_KEY')+':'+val('ZERODHA_ACCESS_TOKEN')};
  }else if(name==='groww'){
    if(!has('GROWW_ACCESS_TOKEN'))return json(res,503,{errorMessage:'Groww credentials are missing from .env'});
    host='growwbroking.com';prefix='/brokerage/v1';headers={'Authorization':'Bearer '+val('GROWW_ACCESS_TOKEN')};if(val('GROWW_API_KEY'))headers['X-API-Key']=val('GROWW_API_KEY');
  }else if(name==='upstox'){
    if(!has('UPSTOX_ACCESS_TOKEN'))return json(res,503,{errorMessage:'Upstox order access token is missing from .env'});
    host='api.upstox.com';headers={'Authorization':'Bearer '+val('UPSTOX_ACCESS_TOKEN'),'Accept':'application/json'};
  }else if(name==='angel'){
    if(!has('ANGELONE_API_KEY','ANGELONE_ACCESS_TOKEN'))return json(res,503,{errorMessage:'Angel One credentials are missing from .env'});
    host='apiconnect.angelbroking.com';headers={'Authorization':'Bearer '+val('ANGELONE_ACCESS_TOKEN'),'X-UserType':'USER','X-SourceID':'WEB','X-ClientLocalIP':'127.0.0.1','X-ClientPublicIP':'127.0.0.1','X-MACAddress':'00:00:00:00:00:00','X-PrivateKey':val('ANGELONE_API_KEY')};
  }else return json(res,404,{errorMessage:'Unknown broker'});
  const rest=url.pathname.slice(`/broker/${name}`.length)||'/';
  const target='https://'+host+prefix+rest+url.search;
  upstream(req,res,target,headers);
}
async function aiRoute(provider,req,res){
  if(req.method!=='POST')return json(res,405,{errorMessage:'Use POST'});
  const body=await readBody(req).catch(e=>null);if(!body)return json(res,400,{errorMessage:'Invalid request body'});
  let input;try{input=JSON.parse(body.toString('utf8'));}catch(e){return json(res,400,{errorMessage:'Expected JSON'});}
  const system=String(input.system||''),user=String(input.user||'');if(!user)return json(res,400,{errorMessage:'A user message is required'});
  let target,headers,bodyOut;
  if(provider==='claude'){
    if(!has('ANTHROPIC_API_KEY'))return json(res,503,{errorMessage:'ANTHROPIC_API_KEY is not configured in .env'});
    target='https://api.anthropic.com/v1/messages';headers={'x-api-key':val('ANTHROPIC_API_KEY'),'anthropic-version':'2023-06-01','content-type':'application/json'};
    bodyOut=JSON.stringify({model:val('ANTHROPIC_MODEL')||'claude-sonnet-4-20250514',max_tokens:800,system,messages:[{role:'user',content:user}]});
  }else if(provider==='gemini'){
    if(!has('GEMINI_API_KEY'))return json(res,503,{errorMessage:'GEMINI_API_KEY is not configured in .env'});
    target='https://generativelanguage.googleapis.com/v1beta/models/'+encodeURIComponent(val('GEMINI_MODEL')||'gemini-2.5-flash')+':generateContent';headers={'x-goog-api-key':val('GEMINI_API_KEY'),'content-type':'application/json'};
    bodyOut=JSON.stringify({contents:[{parts:[{text:system+'\n\n'+user}]}],generationConfig:{maxOutputTokens:800}});
  }else if(provider==='openai'){
    if(!has('OPENAI_API_KEY'))return json(res,503,{errorMessage:'OPENAI_API_KEY is not configured in .env'});
    target='https://api.openai.com/v1/chat/completions';headers={'Authorization':'Bearer '+val('OPENAI_API_KEY'),'content-type':'application/json'};
    bodyOut=JSON.stringify({model:val('OPENAI_MODEL')||'gpt-4o-mini',max_tokens:800,messages:[{role:'system',content:system},{role:'user',content:user}]});
  }else return json(res,404,{errorMessage:'Unknown AI provider'});
  // Forward a generated provider request without re-reading the already consumed browser body.
  const u=new URL(target),h={...headers,'content-length':Buffer.byteLength(bodyOut)};
  const up=https.request({hostname:u.hostname,port:443,path:u.pathname+u.search,method:'POST',headers:h},r=>{const chunks=[];r.on('data',c=>chunks.push(c));r.on('end',()=>json(res,r.statusCode||502,(()=>{let x=Buffer.concat(chunks).toString('utf8');try{return JSON.parse(x);}catch(e){return{errorMessage:x.slice(0,500)};}})()));});
  up.on('error',e=>json(res,502,{errorMessage:'AI proxy: '+e.message}));up.end(bodyOut);
}

const srv=http.createServer(async(req,res)=>{
  if(!allowed(req))return json(res,403,{errorMessage:'Only the local Reimagined Winner page may use this backend.'});
  const origin=req.headers.origin;if(origin&&allowedOrigins.has(origin))res.setHeader('Access-Control-Allow-Origin',origin);
  if(req.method==='OPTIONS')return send(res,204,'',CORS);
  const url=new URL(req.url,'http://127.0.0.1');
  if(req.method==='GET'&&(url.pathname==='/'||url.pathname==='/app'||url.pathname==='/reimagined-winner.html')){
    try{return send(res,200,fs.readFileSync(path.join(__dirname,'reimagined-winner.html')),{...CORS,'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':"default-src 'self' https: data: blob:; script-src 'self' 'unsafe-inline' https:; style-src 'self' 'unsafe-inline' https:; img-src 'self' https: data:; connect-src 'self' https: wss:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'"});}catch(e){return json(res,500,{errorMessage:'Could not read reimagined-winner.html'});}
  }
  if(req.method==='GET'&&url.pathname==='/reimagined-winner-icon.svg'){
    try{return send(res,200,fs.readFileSync(path.join(__dirname,'reimagined-winner-icon.svg')),{...CORS,'Content-Type':'image/svg+xml; charset=utf-8'});}catch(e){return json(res,404,{errorMessage:'App icon not found'});}
  }
  if(req.method==='GET'&&url.pathname==='/ping')return json(res,200,{app:'reimagined-winner-local-backend',ok:true,port:PORT,features:['env','env-editor','ai','brokers','dhan','upstox','yf','portfolio-research','turtle-soup']});
  if(req.method==='GET'&&url.pathname==='/config')return json(res,200,configStatus());
  if(req.method==='GET'&&url.pathname==='/config/values')return json(res,200,{ok:true,values:readEnvValues()});
  if(req.method==='POST'&&url.pathname==='/config/values'){
    const raw=await readBody(req).catch(e=>null);if(raw===null)return json(res,400,{errorMessage:'Could not read configuration request.'});
    try{const body=JSON.parse(raw.toString('utf8')),count=writeEnvValues(body.values);return json(res,200,{ok:true,count,restartRequired:Number(process.env.PORT||8765)!==PORT,config:configStatus()});}
    catch(e){return json(res,400,{errorMessage:'Configuration was not saved: '+String(e.message||'invalid request').slice(0,180)});}
  }
  const ai=url.pathname.match(/^\/ai\/(claude|gemini|openai)$/);if(ai)return aiRoute(ai[1],req,res);
  const broker=url.pathname.match(/^\/broker\/(zerodha|groww|upstox|angel)(\/.*)?$/);if(broker)return brokerRoute(broker[1],req,res,url);
  const dk='/dhan/',uk='/upstox/',yk='/yf/';
  if(url.pathname.startsWith(dk)){
    if(!has('DHAN_CLIENT_ID','DHAN_ACCESS_TOKEN'))return json(res,503,{errorMessage:'Dhan credentials are missing from .env'});
    let body=null;if(['POST','PUT'].includes(req.method)){
      const raw=await readBody(req).catch(e=>null);if(raw===null)return json(res,400,{errorMessage:'Could not read request body'});
      if(raw.length&&String(req.headers['content-type']||'').includes('application/json')){try{const j=JSON.parse(raw.toString('utf8'));if(j&&typeof j==='object'&&!j.dhanClientId)j.dhanClientId=val('DHAN_CLIENT_ID');body=Buffer.from(JSON.stringify(j));}catch(e){body=raw;}}else body=raw;
    }
    const target='https://api.dhan.co'+pathAfter(url,dk);
    const u=new URL(target),h={'access-token':val('DHAN_ACCESS_TOKEN'),'client-id':val('DHAN_CLIENT_ID'),'content-type':req.headers['content-type']||'application/json'};
    if(req.method==='GET'||req.method==='HEAD')body=Buffer.alloc(0);if(body===null)body=Buffer.alloc(0);if(body.length)h['content-length']=body.length;
    const up=https.request({hostname:u.hostname,port:443,path:u.pathname+u.search,method:req.method,headers:h},r=>{res.writeHead(r.statusCode||502,{'Content-Type':r.headers['content-type']||'application/json','Cache-Control':'no-store'});r.pipe(res);});
    up.on('error',e=>json(res,502,{errorMessage:'proxy: '+e.message}));if(body.length)up.write(body);up.end();return;
  }
  if(url.pathname.startsWith(uk)){
    const streamAuthorize=url.pathname.includes('/v3/feed/market-data-feed/authorize');
    const token=streamAuthorize?(val('UPSTOX_ACCESS_TOKEN')||val('UPSTOX_ANALYTICS_TOKEN')):(val('UPSTOX_ANALYTICS_TOKEN')||val('UPSTOX_ACCESS_TOKEN'));if(!token)return json(res,503,{errorMessage:'Upstox token is missing from .env'});
    return upstream(req,res,'https://api.upstox.com'+pathAfter(url,uk),{'Authorization':'Bearer '+token,'Accept':'application/json'});
  }
  if(url.pathname.startsWith(yk))return upstream(req,res,'https://query1.finance.yahoo.com'+pathAfter(url,yk),{'user-agent':UA,'accept':'application/json'});
  json(res,404,{errorMessage:'Route not found'});
});
srv.on('error',e=>{console.error('Proxy error: '+e.message);process.exitCode=1;});
srv.listen(PORT,HOST,()=>console.log(`Reimagined Winner backend at http://${HOST}:${PORT}/ · loaded ${envCount} .env entries · Ctrl+C to stop`));
