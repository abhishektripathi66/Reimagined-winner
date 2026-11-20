// dhan-proxy.js — local proxy for Dhan (/dhan/*), Upstox (/upstox/*), and Yahoo Finance (/yf/*).
// Local default: node dhan-proxy.js. Optional phone-on-LAN mode:
// WYCKOFF_PROXY_KEY="long-random-secret" node dhan-proxy.js --host 0.0.0.0
// Keep LAN mode on trusted Wi-Fi and never expose this HTTP proxy to the public internet.
const http=require('http'),https=require('https'),args=process.argv.slice(2);
let PORT=+(process.env.PORT||8765),HOST=process.env.WYCKOFF_PROXY_HOST||'127.0.0.1',PROXY_KEY=process.env.WYCKOFF_PROXY_KEY||'';
for(let i=0;i<args.length;i++){
  const a=args[i];
  if(a==='--host'&&args[i+1])HOST=args[++i];else if(a.startsWith('--host='))HOST=a.slice(7);
  else if(a==='--key'&&args[i+1])PROXY_KEY=args[++i];else if(a.startsWith('--key='))PROXY_KEY=a.slice(6);
  else if(/^\d+$/.test(a))PORT=+a;
}
if(!['127.0.0.1','localhost','::1'].includes(HOST)&&!PROXY_KEY){console.error('Refusing non-local bind without a key. Set WYCKOFF_PROXY_KEY or pass --key.');process.exit(2);}
const CORS={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'access-token,client-id,dhanclientid,authorization,accept,content-type,x-wyckoff-proxy-key','Access-Control-Allow-Private-Network':'true','Access-Control-Max-Age':'600'};
const UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36';
const ROUTES={'/dhan/':{base:'https://api.dhan.co',keep:['access-token','client-id','dhanclientid','content-type','content-length'],extra:{}},
              '/upstox/':{base:'https://api.upstox.com',keep:['authorization','accept','content-type','content-length'],extra:{}},
              '/yf/':{base:'https://query1.finance.yahoo.com',keep:[],extra:{'user-agent':UA,accept:'application/json'}}};
const srv=http.createServer((req,res)=>{
  if(req.method==='OPTIONS'){res.writeHead(204,CORS);return res.end();}
  if(PROXY_KEY&&req.headers['x-wyckoff-proxy-key']!==PROXY_KEY){res.writeHead(401,{...CORS,'Content-Type':'application/json'});return res.end(JSON.stringify({error:'Invalid or missing proxy access key'}));}
  if(req.url==='/ping'){res.writeHead(200,{...CORS,'Content-Type':'application/json'});return res.end(JSON.stringify({app:'wyckoff-proxy',ok:true,port:PORT,features:['dhan','upstox','yf']}));}
  if(req.method==='GET'&&req.url.split('?')[0]==='/upstox/callback'){
    const page='<!doctype html><html><meta charset="utf-8"><title>Upstox login</title><body style="font:16px system-ui;padding:32px;background:#0b1020;color:#fff"><h2 id="m">Returning to Reimagined Winner…</h2><script>(function(){var p=new URLSearchParams(location.search),m={type:"upstox-auth-callback",code:p.get("code"),state:p.get("state"),error:p.get("error_description")||p.get("error")};if(window.opener){window.opener.postMessage(m,"*");document.getElementById("m").textContent=m.code?"Login complete. You can close this window.":"Upstox login did not complete.";setTimeout(function(){window.close()},700)}else{document.getElementById("m").textContent="Return to Reimagined Winner and reconnect."}})();</script></body></html>';
    res.writeHead(200,{...CORS,'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});return res.end(page);
  }
  const k=Object.keys(ROUTES).find(p=>req.url.startsWith(p));
  if(!k){res.writeHead(404,CORS);return res.end('use /dhan/..., /upstox/... or /yf/...');}
  const R=ROUTES[k],h={...R.extra},log=k==='/dhan/'?(s=>console.log(req.method,req.url.split('?')[0],s)):(()=>{});
  R.keep.forEach(n=>{if(req.headers[n])h[n]=req.headers[n];});
  const up=https.request(R.base+'/'+req.url.slice(k.length),{method:req.method,headers:h},r=>{log('->'+r.statusCode);res.writeHead(r.statusCode,{...CORS,'Content-Type':r.headers['content-type']||'application/json'});r.pipe(res);});
  up.on('error',e=>{log('-> failed: '+e.message);res.writeHead(502,CORS);res.end(JSON.stringify({errorMessage:'proxy: '+e.message}));});
  req.pipe(up);
});
srv.on('error',e=>{console.log(e.code==='EADDRINUSE'?'Port '+PORT+' is already used by another program. Close it, or start this on another port.':'Proxy error: '+e.message);process.exit(1);});
srv.listen(PORT,HOST,()=>console.log('Wyckoff proxy on http://'+(HOST==='0.0.0.0'?'<PC-LAN-IP>':HOST)+':'+PORT+' (Dhan / Upstox / Yahoo). '+(PROXY_KEY?'Access key required. ':'')+'Ctrl+C to stop'));
