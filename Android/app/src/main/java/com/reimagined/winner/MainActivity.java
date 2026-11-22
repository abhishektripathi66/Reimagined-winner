package com.reimagined.winner;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private static final int PICK_ENV=41, REQ_NOTIFICATIONS=42, PICK_PORTFOLIO=43;
    private static final String CHANNEL="rw_mobile_trades";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private EnvStore envStore; private Map<String,String> env; private NativeApi api; private SignalScanner scanner;
    private LinearLayout root,toolbar,nav,body; private ScrollView scroll; private TextView topStatus,validityText;
    private String tab="Home"; private boolean light=false,foreground=false,scanBusy=false,priceBusy=false;
    private boolean editingConfig=false,portfolioBusy=false;private EditText configEditor;private final List<Holding> holdings=new ArrayList<>();private final Map<String,PortfolioResearch.Result> portfolioResults=new HashMap<>();private final Map<String,String> portfolioErrors=new HashMap<>();private final List<String> growthCandidates=new ArrayList<>();
    private int selectedIndex=0; private SignalScanner.Candidate current; private long lastScannedClosedBar=0; private int invalidBars=0; private String lastAlertId="";
    private String lastPortfolioImportSummary="No statement imported yet.";
    private String marketMessage="Prices have not been refreshed yet.";
    private final double[] price={0,0,0},change={0,0,0}; private final boolean[] priceAvailable={false,false,false};
    private Runnable refreshLoop,clockLoop;

    @Override public void onCreate(Bundle state){super.onCreate(state);envStore=new EnvStore(this);env=envStore.load();api=new NativeApi(env);scanner=new SignalScanner(api);light=getPreferences(0).getBoolean("light",false);loadHoldings();createNotificationChannel();requestNotificationPermission();getWindow().setStatusBarColor(colorBg());getWindow().setNavigationBarColor(colorBg());render();}
    @Override protected void onResume(){super.onResume();foreground=true;scheduleRefresh(200);scheduleClock();}
    @Override protected void onPause(){foreground=false;handler.removeCallbacks(refreshLoop);handler.removeCallbacks(clockLoop);super.onPause();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private void render(){
        validityText=null;
        configureSystemBars();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(background(new int[]{colorBg(),colorBgEnd()},0));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            }else{
                view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(16),dp(10),dp(12),dp(10));toolbar.setBackground(background(new int[]{Color.rgb(12,38,62),Color.rgb(17,35,67)},0));toolbar.setElevation(dp(5));
        ImageView brandIcon=new ImageView(this);brandIcon.setImageResource(R.mipmap.ic_launcher);toolbar.addView(brandIcon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setOrientation(LinearLayout.VERTICAL);brand.setPadding(dp(10),0,0,0);TextView brandName=text("REIMAGINED WINNER",15,Color.WHITE);brandName.setTypeface(null,Typeface.BOLD);TextView brandSub=text("NSE / BSE  ·  MARKET RESEARCH",9,Color.rgb(105,239,207));brandSub.setTypeface(null,Typeface.BOLD);brand.addView(brandName);brand.addView(brandSub);toolbar.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
        Button mode=button(light?"☾ Dark":"☼ Light",false);mode.setOnClickListener(v->{light=!light;getPreferences(0).edit().putBoolean("light",light).apply();render();});toolbar.addView(mode);root.addView(toolbar);
        topStatus=text("",11,colorMuted());topStatus.setPadding(dp(16),dp(8),dp(16),dp(8));topStatus.setBackground(background(new int[]{colorPanelTop(),colorPanel()},dp(12)));root.addView(topStatus);
        scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(8),dp(14),dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(5),dp(6),dp(5),dp(6));nav.setBackground(background(new int[]{Color.rgb(12,25,46),Color.rgb(17,31,53)},0));nav.setElevation(dp(7));String[] items={"Home","Signals","Portfolio","AI","Settings"};
        for(String item:items){Button b=button(item.equals(tab)?"● "+item:item,item.equals(tab));b.setTextSize(10);b.setPadding(dp(1),0,dp(1),0);b.setOnClickListener(v->{tab=item;editingConfig=false;render();});nav.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));}root.addView(nav);
        setContentView(root);updateTopStatus();
        if("Home".equals(tab))renderHome();else if("Signals".equals(tab))renderSignals();else if("Portfolio".equals(tab))renderPortfolio();else if("AI".equals(tab))renderAi();else if(editingConfig)renderConfigEditor();else renderSettings();
    }

    private void renderHome(){
        title("Market overview","Native Android client · credentials stay in encrypted app storage");
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.VERTICAL);body.addView(cards);
        for(int base=0;base<3;base+=2){LinearLayout row=new LinearLayout(this);for(int i=base;i<Math.min(3,base+2);i++){LinearLayout c=card();c.setPadding(dp(12),dp(12),dp(10),dp(12));TextView name=text("●  "+NativeApi.LABELS[i],10,i==0?Color.rgb(52,207,255):i==1?Color.rgb(255,190,88):Color.rgb(188,139,255));name.setTypeface(null,Typeface.BOLD);TextView value=text(priceAvailable[i]?rupee(price[i]):"—",21,priceAvailable[i]?priceColor(change[i]):colorText());value.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);TextView pct=text(priceAvailable[i]?signed(change[i])+"%":"Add an Upstox token in Settings",10,priceAvailable[i]?priceColor(change[i]):colorMuted());c.addView(name);c.addView(value);c.addView(pct);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>base)lp.leftMargin=dp(8);row.addView(c,lp);}cards.addView(row,spaceBottom());}
        LinearLayout summary=card();summary.addView(text("SIGNAL STATUS",11,colorMuted()));String signal=current==null?"No active confirmed setup":""+current.side+" · "+current.label+" "+format(current.strike);summary.addView(text(signal,17,colorText()));summary.addView(text(current==null?"A setup appears only after two aligned closed candles and liquidity checks.":"Valid until "+when(current.validUntil),12,colorMuted()));Button scan=button("Refresh market and scan",true);scan.setOnClickListener(v->{refreshPrices();runScan(true);});summary.addView(scan);body.addView(summary,spaceBottom());
        LinearLayout row=new LinearLayout(this);Button signals=button("Open Signals",true);signals.setOnClickListener(v->{tab="Signals";render();});row.addView(signals,new LinearLayout.LayoutParams(0,dp(48),1));Button settings=button("Configuration",false);settings.setOnClickListener(v->{tab="Settings";render();});row.addView(settings,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(row);
    }

    private void renderSignals(){
        title("Confirmed option setups","BUY CE / BUY PE only · no orders are sent from the Android app");
        LinearLayout controls=card();controls.addView(text("INDEX",11,colorMuted()));Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,NativeApi.LABELS);spinner.setAdapter(adapter);spinner.setSelection(selectedIndex);spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){if(selectedIndex!=position){selectedIndex=position;render();}}public void onNothingSelected(android.widget.AdapterView<?> p){}});controls.addView(spinner);Button scan=button(scanBusy?"Scanning…":"Scan now",true);scan.setEnabled(!scanBusy);scan.setOnClickListener(v->runScan(true));controls.addView(scan);body.addView(controls,spaceBottom());
        LinearLayout status=card();status.addView(text("SCANNER",11,colorMuted()));status.addView(text(scanBusy?"Checking completed candles and major constituents…":marketMessage,14,colorText()));status.addView(text(api.hasUpstox()?"Market-data source: Upstox direct HTTPS":"Waiting for Upstox token in imported environment",12,colorMuted()));body.addView(status,spaceBottom());
        if(current!=null){if(current.validUntil<=System.currentTimeMillis()){expireCurrent("Setup validity ended");}else renderCandidate(current);}
        else {LinearLayout empty=card();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(18),dp(28),dp(18),dp(28));empty.addView(text("No active setup",18,colorText()));empty.addView(text("Filtered or pending scans stay hidden until confirmation. Expired setups disappear automatically.",13,colorMuted()));body.addView(empty);}
        LinearLayout note=card();note.addView(text("Confirmation & risk",13,colorText()));note.addView(text("Requires two consecutive completed 1-minute bars with aligned Wyckoff / structure direction. Major-weight patterns can block an opposing setup. Options are filtered by live bid/ask, volume, open interest and delta. Strength is a rule score, not a win probability or backtest result. Verify the live contract and manage risk yourself.",12,colorMuted()));body.addView(note,spaceBottom());
    }
    private void renderCandidate(SignalScanner.Candidate c){
        LinearLayout card=card();card.addView(text(c.label+" · "+c.side,19,colorAccent()));card.addView(text("Strike "+format(c.strike)+" · expiry "+c.expiry,14,colorText()));card.addView(text("Ask reference "+rupee(c.entry)+" · bid "+rupee(c.bid),14,colorText()));card.addView(text("Stop "+rupee(c.stop)+" · target "+rupee(c.target)+" · strength "+c.strength+"/100",13,colorText()));validityText=text("Validity remaining: "+remaining(c.validUntil),13,colorAccent());card.addView(validityText);card.addView(text("Index "+rupee(c.spot)+" · major-weight coverage "+Math.round(c.majorCoverage*100)+"% · weight snapshot "+c.majorAsOf,12,colorMuted()));card.addView(text(c.majorSummary==null?"Major-weight pattern unavailable.":c.majorSummary,11,colorMuted()));card.addView(text("Turtle Soup: "+(c.turtleSoup==null?"Unavailable":c.turtleSoup)+" · 20-bar intraday failed-breakout proxy",11,colorMuted()));card.addView(text("Not a backtested win rate. No trade/order was placed.",11,colorMuted()));Button dismiss=button("Dismiss setup",false);dismiss.setOnClickListener(v->{current=null;invalidBars=0;render();});card.addView(dismiss);body.addView(card,spaceBottom());
    }

    private void renderAi(){
        title("AI options analyst","Uses provider keys already loaded from the encrypted environment file");
        Spinner providers=new Spinner(this);String[] names={"Gemini","Claude","ChatGPT"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);providers.setAdapter(adapter);body.addView(providers,spaceBottom());
        TextView response=text("Ask about a setup or the market. The assistant only receives the market context shown in this app.",14,colorText());response.setPadding(dp(14),dp(14),dp(14),dp(14));response.setBackground(round(colorPanel(),dp(12)));body.addView(response,spaceBottom());
        EditText input=new EditText(this);input.setSingleLine(false);input.setMinLines(2);input.setHint("Ask for analysis…");input.setTextColor(colorText());input.setHintTextColor(colorMuted());input.setBackground(round(colorPanel(),dp(10)));input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setImeOptions(EditorInfo.IME_ACTION_SEND);body.addView(input,spaceBottom());
        Button send=button("Ask",true);
        send.setOnClickListener(v -> {
            String q=input.getText().toString().trim();
            if(q.isEmpty()) return;
            input.setEnabled(false); send.setEnabled(false); response.setText("Thinking…");
            String provider=String.valueOf(providers.getSelectedItem());
            String context;
            if(current==null) context="No confirmed option setup is active. Index quotes: "+(priceAvailable[0]?rupee(price[0]):"unavailable")+".";
            else context=current.label+" "+current.side+" strike "+current.strike+" ask "+current.entry+" stop "+current.stop+" target "+current.target+" strength "+current.strength+"/100, expires "+when(current.validUntil)+". This is a model setup, not a backtested win rate.";
            io.execute(() -> {
                try { String answer=api.ask(provider,q,context); handler.post(() -> response.setText(answer)); }
                catch(Exception e) { handler.post(() -> response.setText("AI error: "+safe(e.getMessage()))); }
                finally { handler.post(() -> { input.setEnabled(true); send.setEnabled(true); }); }
            });
        });
        body.addView(send);
        LinearLayout keys=card();keys.addView(text("Provider status",13,colorText()));keys.addView(text("Gemini: "+(configured("GEMINI_API_KEY")?"ready":"not configured")+" · Claude: "+(configured("ANTHROPIC_API_KEY")?"ready":"not configured")+" · ChatGPT: "+(configured("OPENAI_API_KEY")?"ready":"not configured"),12,colorMuted()));body.addView(keys,spaceBottom());
    }

    private void renderSettings(){
        title("Configuration","Import once or read/edit the saved values inside this app.");
        LinearLayout file=card();file.addView(text("Encrypted app-private config",15,colorText()));file.addView(text(env.isEmpty()?"No configuration loaded.":"Configuration loaded · "+env.size()+" entries · encrypted with Android Keystore",13,colorMuted()));Button pick=button("Import .env / local.env",true);pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_ENV);});file.addView(pick);Button edit=button("Read / edit configuration",false);edit.setOnClickListener(v->{editingConfig=true;render();});file.addView(edit);body.addView(file,spaceBottom());
        section("AI providers");providerRow("Gemini","GEMINI_API_KEY");providerRow("Claude","ANTHROPIC_API_KEY");providerRow("ChatGPT / OpenAI","OPENAI_API_KEY");providerRow("Google sign-in","GOOGLE_CLIENT_ID");
        section("Broker credentials");providerRow("Upstox market data","UPSTOX_ANALYTICS_TOKEN","UPSTOX_ACCESS_TOKEN");providerRow("Dhan","DHAN_CLIENT_ID","DHAN_ACCESS_TOKEN");providerRow("Groww","GROWW_ACCESS_TOKEN");providerRow("Zerodha","ZERODHA_API_KEY","ZERODHA_ACCESS_TOKEN");providerRow("Angel One","ANGELONE_API_KEY","ANGELONE_ACCESS_TOKEN");providerRow("Fyers","FYERS_APP_ID","FYERS_ACCESS_TOKEN");providerRow("Alice Blue","ALICEBLUE_API_KEY","ALICEBLUE_ACCESS_TOKEN");providerRow("ICICI Direct","ICICIDIRECT_API_KEY","ICICIDIRECT_ACCESS_TOKEN");providerRow("Kotak Neo","KOTAKNEO_API_KEY","KOTAKNEO_ACCESS_TOKEN");providerRow("Client ID hint","UPSTOX_CLIENT_ID","DHAN_CLIENT_ID");
        LinearLayout privacy=card();privacy.addView(text("Security & scope",13,colorText()));privacy.addView(text("The imported file is encrypted with an Android Keystore key and stored in this app’s private directory. The Android build does not include your .env file, Node.js, or the Windows HTML. Broker order placement is not enabled in this native build.",12,colorMuted()));body.addView(privacy,spaceBottom());
    }

    private void renderConfigEditor(){
        title("Read / edit config","This is the app’s saved configuration. Save encrypts it in app-private storage.");
        LinearLayout c=card();configEditor=new EditText(this);configEditor.setGravity(Gravity.TOP|Gravity.START);configEditor.setMinLines(14);configEditor.setTextSize(12);configEditor.setTypeface(Typeface.MONOSPACE);configEditor.setTextColor(colorText());configEditor.setHintTextColor(colorMuted());configEditor.setHint("GEMINI_API_KEY=...\nUPSTOX_ACCESS_TOKEN=...");configEditor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);configEditor.setPadding(dp(10),dp(10),dp(10),dp(10));configEditor.setBackground(round(colorBg(),dp(8)));configEditor.setText(envAsText());c.addView(configEditor,new LinearLayout.LayoutParams(-1,dp(330)));
        TextView warning=text("Secret keys are visible while this editor is open. Do not share a screenshot of this screen.",11,Color.rgb(220,156,49));warning.setPadding(0,dp(8),0,dp(6));c.addView(warning);Button save=button("Save encrypted configuration",true);save.setOnClickListener(v->saveConfigText());c.addView(save);Button cancel=button("Back to settings",false);cancel.setOnClickListener(v->{editingConfig=false;render();});c.addView(cancel);body.addView(c,spaceBottom());
    }
    private String envAsText(){StringBuilder s=new StringBuilder();for(Map.Entry<String,String> e:env.entrySet())s.append(e.getKey()).append('=').append(quoteEnv(e.getValue())).append('\n');return s.toString();}
    private String quoteEnv(String value){if(value==null)return"\"\"";return"\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    private void saveConfigText(){String value=configEditor==null?"":configEditor.getText().toString();try{envStore.saveText(value);env=envStore.load();api=new NativeApi(env);scanner=new SignalScanner(api);editingConfig=false;Toast.makeText(this,"Configuration saved encrypted.",Toast.LENGTH_LONG).show();render();if(api.hasUpstox())refreshPrices();}catch(Exception e){Toast.makeText(this,"Could not save config: "+safe(e.getMessage()),Toast.LENGTH_LONG).show();}}

    private void renderPortfolio(){
        title("Portfolio research","Real completed daily candles · trend, Wyckoff, volume and Turtle Soup checks");
        LinearLayout actions=card();Button importFile=button("📥 Import Groww Excel / CSV",true);importFile.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_PORTFOLIO);});actions.addView(importFile);Button analyze=button(portfolioBusy?"⏳ Analyzing…":"✨ Analyze holdings + growth candidates",false);analyze.setEnabled(!portfolioBusy);analyze.setOnClickListener(v->runPortfolioResearch());actions.addView(analyze);body.addView(actions,spaceBottom());
        LinearLayout importInfo=card();importInfo.addView(text("STATEMENT IMPORT",10,colorAccent()));importInfo.addView(text(lastPortfolioImportSummary,12,colorMuted()));importInfo.addView(text("Groww buy value, closing price/value, and statement unrealised P&L are shown separately from live research.",11,colorMuted()));body.addView(importInfo,spaceBottom());
        LinearLayout add=card();add.addView(text("Add a holding",14,colorText()));LinearLayout row=new LinearLayout(this);EditText sym=smallInput("Symbol e.g. RELIANCE"),qty=smallInput("Qty"),avg=smallInput("Avg cost ₹");qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);avg.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);row.addView(sym,new LinearLayout.LayoutParams(0,dp(52),1.3f));row.addView(qty,new LinearLayout.LayoutParams(0,dp(52),.8f));row.addView(avg,new LinearLayout.LayoutParams(0,dp(52),1));add.addView(row);Button addButton=button("Add holding",false);addButton.setOnClickListener(v->{try{String symbol=normalizeSymbol(sym.getText().toString());double q=Double.parseDouble(qty.getText().toString()),cost=Double.parseDouble(avg.getText().toString());if(symbol.isEmpty()||q<=0||cost<=0)throw new IllegalArgumentException("Enter symbol, quantity and average cost.");if(!NativeApi.STOCK_KEYS.containsKey(symbol))throw new IllegalArgumentException("Daily-history mapping is not available for "+symbol+" yet.");holdings.add(new Holding(symbol,q,cost));saveHoldings();render();}catch(Exception e){Toast.makeText(this,safe(e.getMessage()),Toast.LENGTH_LONG).show();}});add.addView(addButton);body.addView(add,spaceBottom());
        if(holdings.isEmpty()){LinearLayout empty=card();empty.addView(text("No holdings added",15,colorText()));empty.addView(text("Import a broker CSV or add positions above. Nothing is prefilled with demo trades.",12,colorMuted()));body.addView(empty,spaceBottom());}
        else for(Holding h:holdings){LinearLayout c=card();PortfolioResearch.Result r=portfolioResults.get(h.symbol);c.addView(text(h.displayName+" · "+format(h.quantity)+" shares",16,colorText()));if(!h.displayName.equals(h.symbol))c.addView(text(h.symbol+" · "+(h.instrumentKey.isEmpty()?"no mapped instrument":"ISIN history key ready"),10,colorAccent()));c.addView(text(h.averageCost>0?"Average buy price "+rupee(h.averageCost):"Average buy price not present in file",12,colorMuted()));if(h.statementBuyValue>0)c.addView(text("Groww buy value "+rupee(h.statementBuyValue),12,colorMuted()));if(h.statementClosePrice>0)c.addView(text("Groww closing price "+rupee(h.statementClosePrice),12,colorMuted()));if(h.statementCloseValue>0)c.addView(text("Groww closing value "+rupee(h.statementCloseValue),12,colorMuted()));if(h.statementUnrealizedPL!=0)c.addView(text("Groww statement unrealised P&L "+rupee(h.statementUnrealizedPL),12,priceColor(h.statementUnrealizedPL)));if(r==null){String err=portfolioErrors.get(h.symbol);c.addView(text(portfolioBusy?"Loading real daily history…":err==null?"Run analysis to evaluate.":"No recommendation · "+err,12,colorMuted()));}else{double pnl=h.averageCost>0?(r.lastClose-h.averageCost)*h.quantity:0,pct=h.averageCost>0?(r.lastClose/h.averageCost-1)*100:0;c.addView(text("Last completed close "+rupee(r.lastClose)+" · "+r.asOf,13,colorText()));if(h.averageCost>0)c.addView(text("Research unrealized "+rupee(pnl)+" ("+signed(pct)+"%)",12,priceColor(pnl)));c.addView(text(r.action+" · research score "+r.score+"/100 · "+r.trend+" · "+r.phase,13,colorAccent()));c.addView(text("Daily SMC: "+r.smc+" · Turtle Soup: "+r.turtleSoup,11,colorMuted()));c.addView(text("VMA "+String.format(Locale.US,"%.2f×",r.volumeRatio)+" · completed daily candles only",11,colorMuted()));}Button remove=button("Remove holding",false);remove.setOnClickListener(v->{holdings.remove(h);portfolioResults.remove(h.symbol);saveHoldings();render();});c.addView(remove);body.addView(c,spaceBottom());}
        LinearLayout candidates=card();candidates.addView(text("Growth research candidates",15,colorText()));if(portfolioBusy)candidates.addView(text("Scanning available mapped constituents…",12,colorMuted()));else if(growthCandidates.isEmpty())candidates.addView(text("No candidates currently passed the trend and signal rules. Score is not a growth probability.",12,colorMuted()));else for(String candidate:growthCandidates){PortfolioResearch.Result r=portfolioResults.get(candidate);candidates.addView(text(candidate+" · score "+r.score+"/100 · "+r.trend+" · "+r.phase,13,colorText()));candidates.addView(text("SMC "+r.smc+" · Turtle Soup: "+r.turtleSoup+" · candles through "+r.asOf,11,colorMuted()));}body.addView(candidates,spaceBottom());
        LinearLayout note=card();note.addView(text("How the research works",13,colorText()));note.addView(text("The research combines trend, Wyckoff-style phase, daily SMC structure, volume versus median and relative momentum. Investment Turtle Soup is a daily failed breakout: price crosses a prior 20-session extreme that is at least four sessions old, then closes back inside the prior range. This is a simplified research score, not a calibrated probability or a promise of returns. Missing or stale history produces no recommendation.",12,colorMuted()));body.addView(note);
    }
    private EditText smallInput(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(11);e.setHint(hint);e.setTextColor(colorText());e.setHintTextColor(colorMuted());e.setPadding(dp(7),0,dp(7),0);e.setBackground(round(colorBg(),dp(7)));return e;}

    private void runPortfolioResearch(){
        if(portfolioBusy)return;if(!api.hasUpstox()){Toast.makeText(this,"Add an Upstox market-data token in Settings to fetch real daily candles.",Toast.LENGTH_LONG).show();return;}
        portfolioBusy=true;portfolioErrors.clear();render();Set<String> symbols=new HashSet<>();for(Holding h:holdings)symbols.add(h.symbol);for(String s:NativeApi.RESEARCH_SYMBOLS)symbols.add(s);
        io.execute(()->{Map<String,PortfolioResearch.Result> results=new HashMap<>();Map<String,String> errors=new HashMap<>();ExecutorService pool=Executors.newFixedThreadPool(4);List<Future<SymbolResult>> futures=new ArrayList<>();Map<Future<SymbolResult>,String> owners=new HashMap<>();
            for(String symbol:symbols){Future<SymbolResult> f=pool.submit(()->{String key=NativeApi.instrumentForSymbol(symbol);for(Holding h:holdings)if(h.symbol.equals(symbol)&&!h.instrumentKey.isEmpty()){key=h.instrumentKey;break;}if(key==null)throw new IllegalArgumentException("No ISIN or instrument mapping for daily history");return new SymbolResult(symbol,PortfolioResearch.analyze(api.daily(key)));});futures.add(f);owners.put(f,symbol);}
            for(Future<SymbolResult> f:futures){try{SymbolResult r=f.get();results.put(r.symbol,r.result);}catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();errors.put(owners.get(f),safe(cause.getMessage()));}}
            pool.shutdownNow();handler.post(()->{portfolioBusy=false;portfolioResults.clear();portfolioResults.putAll(results);portfolioErrors.clear();portfolioErrors.putAll(errors);growthCandidates.clear();for(Map.Entry<String,PortfolioResearch.Result> e:results.entrySet())if(!containsHolding(e.getKey())&&PortfolioResearch.isGrowthCandidate(e.getValue()))growthCandidates.add(e.getKey());growthCandidates.sort((a,b)->Integer.compare(portfolioResults.get(b).score,portfolioResults.get(a).score));render();Toast.makeText(this,"Daily-candle research complete · "+results.size()+" symbols scored",Toast.LENGTH_LONG).show();});
        });
    }
    private boolean containsHolding(String symbol){for(Holding h:holdings)if(h.symbol.equals(symbol))return true;return false;}
    private void loadHoldings(){try{lastPortfolioImportSummary=getPreferences(0).getString("portfolio_import_summary",lastPortfolioImportSummary);JSONArray a=new JSONArray(getPreferences(0).getString("portfolio_holdings","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)holdings.add(new Holding(o.optString("symbol"),o.optDouble("quantity"),o.optDouble("averageCost"),o.optString("displayName",o.optString("symbol")),o.optString("instrumentKey"),o.optDouble("statementBuyValue"),o.optDouble("statementClosePrice"),o.optDouble("statementCloseValue"),o.optDouble("statementUnrealizedPL")));}}catch(Exception ignored){}}
    private void saveHoldings(){try{JSONArray a=new JSONArray();for(Holding h:holdings)a.put(new JSONObject().put("symbol",h.symbol).put("quantity",h.quantity).put("averageCost",h.averageCost).put("displayName",h.displayName).put("instrumentKey",h.instrumentKey).put("statementBuyValue",h.statementBuyValue).put("statementClosePrice",h.statementClosePrice).put("statementCloseValue",h.statementCloseValue).put("statementUnrealizedPL",h.statementUnrealizedPL));getPreferences(0).edit().putString("portfolio_holdings",a.toString()).putString("portfolio_import_summary",lastPortfolioImportSummary).apply();}catch(Exception ignored){}}
    private String normalizeSymbol(String value){return value==null?"":value.trim().toUpperCase(Locale.US).replace("NSE:","").replace(".NS","").replace(".BO","").replace("-EQ","").replace(" ","");}
    private String normalizeHeader(String value){return value==null?"":value.toLowerCase(Locale.US).replace("&","and").replaceAll("[^a-z0-9]","");}
    private int findColumn(List<String> headers,String... names){for(int i=0;i<headers.size();i++){String h=normalizeHeader(headers.get(i));for(String n:names)if(h.equals(normalizeHeader(n)))return i;}return-1;}
    private int findHeaderRow(List<List<String>> rows,int[] cols){for(int i=0;i<Math.min(rows.size(),100);i++){List<String> row=rows.get(i);int sym=findColumn(row,"stock name","symbol","ticker","scrip","trading symbol","tradingsymbol","company name","security name","instrument name","name"),qty=findColumn(row,"quantity","qty","net qty","net quantity","quantity held","holding quantity","shares","units"),avg=findColumn(row,"average buy price","average price","avg cost","avg price","average cost","buy avg","buy price","cost price","purchase price"),buy=findColumn(row,"buy value","invested value","cost value");if(sym>=0&&qty>=0&&(avg>=0||buy>=0)){cols[0]=sym;cols[1]=qty;cols[2]=avg;cols[3]=findColumn(row,"isin","security isin");cols[4]=buy;cols[5]=findColumn(row,"closing price","close price","last traded price","last price","ltp","market price");cols[6]=findColumn(row,"closing value","close value","market value","current value");cols[7]=findColumn(row,"unrealised p&l","unrealized p&l","unrealised pnl","unrealized pnl","profit/loss","profit and loss");return i;}}throw new IllegalArgumentException("Could not find the Groww holdings table. Select an Excel/CSV file with Stock Name, Quantity, and Average buy price (or Buy value) columns.");}
    private List<Holding> parsePortfolioRows(List<List<String>> rows){if(rows==null||rows.isEmpty())throw new IllegalArgumentException("The selected workbook or CSV is empty.");int[] c=new int[8];int head=findHeaderRow(rows,c);Map<String,Holding> combined=new java.util.LinkedHashMap<>();int sourceRows=0,skipped=0;
        for(int i=head+1;i<rows.size();i++){List<String> row=rows.get(i);String name=cell(row,c[0]),isin=c[3]>=0?cell(row,c[3]).toUpperCase(Locale.US):"";if(name.isEmpty())continue;sourceRows++;double qty=parseOptionalNumber(cell(row,c[1]));if(!(qty>0)){skipped++;continue;}double buyValue=c[4]>=0?parseOptionalNumber(cell(row,c[4])):0,avg=c[2]>=0?parseOptionalNumber(cell(row,c[2])):0;if(!(avg>0)&&buyValue>0)avg=buyValue/qty;double closePrice=c[5]>=0?parseOptionalNumber(cell(row,c[5])):0,closeValue=c[6]>=0?parseOptionalNumber(cell(row,c[6])):0,statementPnl=c[7]>=0?parseOptionalNumber(cell(row,c[7])):0;
            String symbol=growwSymbol(name);if(symbol.isEmpty())symbol=isin.matches("[A-Z]{2}[A-Z0-9]{10}")?"ISIN_"+isin:normalizeSymbol(name);if(symbol.isEmpty()){skipped++;continue;}String key=isin.matches("[A-Z]{2}[A-Z0-9]{10}")?"NSE_EQ|"+isin:NativeApi.instrumentForSymbol(symbol);Holding h=new Holding(symbol,qty,avg,name,key,buyValue,closePrice,closeValue,statementPnl);Holding old=combined.get(symbol);if(old==null)combined.put(symbol,h);else{double total=old.quantity+qty,cost=old.averageCost>0&&avg>0?(old.averageCost*old.quantity+avg*qty)/total:Math.max(old.averageCost,avg),weightedClose=(old.statementClosePrice*old.quantity+closePrice*qty)/total;combined.put(symbol,new Holding(symbol,total,cost,old.displayName,key==null?old.instrumentKey:key,old.statementBuyValue+buyValue,weightedClose,old.statementCloseValue+closeValue,old.statementUnrealizedPL+statementPnl));}}
        List<Holding> out=new ArrayList<>(combined.values());if(out.isEmpty())throw new IllegalArgumentException("The holdings table was found, but no rows had a usable stock name and positive quantity.");int collapsed=Math.max(0,sourceRows-skipped-out.size());lastPortfolioImportSummary=sourceRows+" statement row(s) found · "+out.size()+" position(s) loaded"+(skipped>0?" · "+skipped+" row(s) skipped":"")+(collapsed>0?" · "+collapsed+" duplicate-symbol row(s) combined":"");return out;}
    private String growwSymbol(String value){String s=normalizeHeader(value);if(s.contains("bharatelectronics"))return"BEL";if(s.equals("bselimited")||s.equals("bse"))return"BSE";if(s.contains("divislaborator"))return"DIVISLAB";if(s.contains("gardenreach"))return"GRSE";if(s.contains("hblengineering"))return"HBLENGINE";if(s.contains("hindustanaeronautics"))return"HAL";if(s.contains("hindustanzinc"))return"HINDZINC";if(s.contains("mazagondock"))return"MAZDOCK";if(s.contains("multicommodityexchange"))return"MCX";if(s.contains("goldbees"))return"GOLDBEES";if(s.contains("netfsilver")||s.contains("silverb ees"))return"SILVERBEES";if(s.contains("oraclefinserv"))return"OFSS";String normalized=normalizeSymbol(value);return NativeApi.STOCK_KEYS.containsKey(normalized)?normalized:"";}
    private List<String> splitCsv(String line){List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quoted=false;char delimiter=detectDelimiter(line);for(int i=0;i<line.length();i++){char ch=line.charAt(i);if(ch=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else quoted=!quoted;}else if(ch==delimiter&&!quoted){out.add(b.toString().trim());b.setLength(0);}else b.append(ch);}out.add(b.toString().trim());return out;}
    private char detectDelimiter(String line){int commas=0,tabs=0,semis=0;boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"')quoted=!quoted;else if(!quoted){if(c==',')commas++;else if(c=='\t')tabs++;else if(c==';')semis++;}}return tabs>commas&&tabs>=semis?'\t':semis>commas?';':',';}
    private String cell(List<String> row,int index){return index<0||index>=row.size()?"":String.valueOf(row.get(index)).trim();}
    private double parseOptionalNumber(String value){if(value==null||value.trim().isEmpty()||value.equals("-"))return 0;String s=value.trim().replace(",","").replace("₹","").replace("%","").replaceAll("^[^0-9(+-]+","");boolean negative=s.startsWith("(")&&s.endsWith(")");s=s.replace("(","").replace(")","");try{double n=Double.parseDouble(s);return negative?-n:n;}catch(Exception e){return 0;}}
    private List<Holding> parsePortfolioFile(byte[] bytes){if(bytes==null||bytes.length==0)throw new IllegalArgumentException("The selected file is empty.");if(bytes.length>5*1024*1024)throw new IllegalArgumentException("Select an .xlsx or CSV file smaller than 5 MB.");if(bytes.length>3&&bytes[0]==0x50&&bytes[1]==0x4b)return parsePortfolioRows(readXlsxRows(bytes));String csv=new String(bytes,StandardCharsets.UTF_8).replace("\uFEFF","");String[] lines=csv.split("\\r?\\n");List<List<String>> rows=new ArrayList<>();for(String line:lines)if(!line.trim().isEmpty())rows.add(splitCsv(line));return parsePortfolioRows(rows);}
    private List<List<String>> readXlsxRows(byte[] bytes){
        try{
            Map<String,byte[]> entries=new HashMap<>();
            Map<String,byte[]> sheets=new java.util.TreeMap<>();
            int unpacked=0;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
                ZipEntry e;byte[] buf=new byte[8192];
                while((e=zip.getNextEntry())!=null){
                    String name=e.getName();
                    if(e.isDirectory()||!(name.equals("xl/sharedStrings.xml")||name.matches("xl/worksheets/sheet\\d+\\.xml")))continue;
                    ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
                    while((n=zip.read(buf))!=-1){unpacked+=n;if(unpacked>12*1024*1024)throw new IllegalArgumentException("Workbook contents exceed the safe 12 MB limit.");out.write(buf,0,n);}
                    if(name.equals("xl/sharedStrings.xml"))entries.put(name,out.toByteArray());else sheets.put(name,out.toByteArray());
                }
            }
            if(sheets.isEmpty())throw new IllegalArgumentException("No worksheets were found in this Excel workbook.");
            List<String> shared=new ArrayList<>();byte[] sharedBytes=entries.get("xl/sharedStrings.xml");
            if(sharedBytes!=null){org.w3c.dom.Document strings=parseXml(sharedBytes);NodeList sis=strings.getElementsByTagName("si");for(int i=0;i<sis.getLength();i++)shared.add(sis.item(i).getTextContent());}
            List<List<String>> bestRows=null;int bestCount=-1;
            for(byte[] sheet:sheets.values()){
                org.w3c.dom.Document doc=parseXml(sheet);NodeList rowNodes=doc.getElementsByTagName("row");List<List<String>> rows=new ArrayList<>();
                for(int i=0;i<rowNodes.getLength();i++){
                    Element row=(Element)rowNodes.item(i);NodeList cells=row.getElementsByTagName("c");Map<Integer,String> values=new java.util.TreeMap<>();int max=-1;
                    for(int j=0;j<cells.getLength();j++){
                        Element c=(Element)cells.item(j);int col=excelColumn(c.getAttribute("r"));if(col<0)continue;
                        String type=c.getAttribute("t"),value="";NodeList v=c.getElementsByTagName("v");
                        if(v.getLength()>0)value=v.item(0).getTextContent();else if("inlineStr".equals(type))value=c.getTextContent();
                        if("s".equals(type)&&!value.isEmpty())try{value=shared.get(Integer.parseInt(value));}catch(Exception ignored){}
                        values.put(col,value);max=Math.max(max,col);
                    }
                    List<String> vals=new ArrayList<>();for(int col=0;col<=max;col++)vals.add(values.getOrDefault(col,""));rows.add(vals);
                }
                int[] cols=new int[8];int head;
                try{head=findHeaderRow(rows,cols);}catch(IllegalArgumentException noTable){continue;}
                int count=0;for(int i=head+1;i<rows.size();i++)if(!cell(rows.get(i),cols[0]).isEmpty()&&parseOptionalNumber(cell(rows.get(i),cols[1]))>0)count++;
                if(count>bestCount){bestRows=rows;bestCount=count;}
            }
            if(bestRows==null)throw new IllegalArgumentException("No worksheet contains a Groww holdings table with Stock Name, Quantity, and Average buy price (or Buy value).");
            return bestRows;
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){String detail=e.getMessage()==null?e.getClass().getSimpleName():e.getClass().getSimpleName()+": "+safe(e.getMessage());throw new IllegalArgumentException("Could not read the Groww .xlsx workbook ("+detail+"). Save it again as Excel Workbook (.xlsx) and retry.",e);}
    }
    private org.w3c.dom.Document parseXml(byte[] bytes)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignored){}try{f.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(Exception ignored){}try{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Exception ignored){}try{f.setXIncludeAware(false);}catch(Exception ignored){}try{f.setExpandEntityReferences(false);}catch(Exception ignored){}return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}
    private int excelColumn(String ref){if(ref==null)return-1;int value=0,n=0;for(int i=0;i<ref.length();i++){char ch=ref.charAt(i);if(ch>='A'&&ch<='Z'){value=value*26+(ch-'A'+1);n++;}else if(ch>='a'&&ch<='z'){value=value*26+(ch-'a'+1);n++;}else break;}return n==0?-1:value-1;}
    private static final class Holding{final String symbol;final double quantity,averageCost,statementBuyValue,statementClosePrice,statementCloseValue,statementUnrealizedPL;final String displayName,instrumentKey;Holding(String s,double q,double c){this(s,q,c,s,NativeApi.instrumentForSymbol(s),0,0,0,0);}Holding(String s,double q,double c,String name,String key){this(s,q,c,name,key,0,0,0,0);}Holding(String s,double q,double c,String name,String key,double buyValue,double closePrice,double closeValue,double statementPnl){symbol=s;quantity=q;averageCost=c;displayName=name==null||name.isEmpty()?s:name;instrumentKey=key==null?"":key;statementBuyValue=buyValue;statementClosePrice=closePrice;statementCloseValue=closeValue;statementUnrealizedPL=statementPnl;}}
    private static final class SymbolResult{final String symbol;final PortfolioResearch.Result result;SymbolResult(String s,PortfolioResearch.Result r){symbol=s;result=r;}}

    private void providerRow(String label,String... keys){boolean yes=false;String hint="";for(String key:keys){String value=env.getOrDefault(key,"").trim();if(!value.isEmpty()){yes=true;if(key.endsWith("CLIENT_ID"))hint=" · "+mask(value);}}LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);TextView l=text(label,13,colorText());c.addView(l,new LinearLayout.LayoutParams(0,dp(38),1));c.addView(text((yes?"● Ready":"○ Not set")+hint,12,yes?colorAccent():colorMuted()));body.addView(c,spaceBottom());}
    private void section(String name){TextView s=text(name,12,colorAccent());s.setPadding(dp(3),dp(8),dp(3),dp(3));body.addView(s);}

    private void refreshPrices(){if(priceBusy||!api.hasUpstox())return;priceBusy=true;io.execute(()->{try{JSONObject quotes=api.indexQuotes();for(int i=0;i<3;i++){JSONObject q=findQuote(quotes,NativeApi.INDEX_KEYS[i],NativeApi.LABELS[i]);if(q==null)continue;double p=q.optDouble("last_price",0),close=q.optDouble("close_price",0),n=close>0?(p/close-1)*100:0;if(p>0){price[i]=p;change[i]=n;priceAvailable[i]=true;}}marketMessage="Prices updated from Upstox.";}catch(Exception e){marketMessage=safe(e.getMessage());}finally{priceBusy=false;handler.post(()->{if("Home".equals(tab))render();else updateTopStatus();});}});}
    private JSONObject findQuote(JSONObject data,String key,String label){if(data.has(key))return data.optJSONObject(key);java.util.Iterator<String> it=data.keys();while(it.hasNext()){String k=it.next();JSONObject q=data.optJSONObject(k);if(q==null)continue;String ik=q.optString("instrument_key","");String sym=q.optString("symbol","");if(ik.equals(key)||k.contains(label.replace(" ",""))||sym.equalsIgnoreCase(label)||k.toLowerCase(Locale.US).contains(key.substring(key.indexOf('|')+1).toLowerCase(Locale.US)))return q;}return null;}
    private void runScan(boolean user){if(scanBusy)return;scanBusy=true;if("Signals".equals(tab))render();io.execute(()->{SignalScanner.Result r=null;Exception err=null;try{r=scanner.scan(selectedIndex);}catch(Exception e){err=e;}final SignalScanner.Result result=r;final Exception error=err;handler.post(()->{scanBusy=false;if(error!=null){marketMessage=safe(error.getMessage());if(error.getMessage()!=null&&error.getMessage().contains("market is closed"))expireIfPastClose();}else{marketMessage=result.message+" · "+result.pattern;applyResult(result);}if("Signals".equals(tab))render();else if(user)Toast.makeText(this,marketMessage,Toast.LENGTH_LONG).show();});});}
    private void applyResult(SignalScanner.Result r){if(r.closedAt<=0)return;if(current!=null&&current.index==r.index&&current.validUntil>System.currentTimeMillis()){
            if(r.closedAt>lastScannedClosedBar){lastScannedClosedBar=r.closedAt;if(r.candidate!=null){invalidBars=0;if(current.id().equals(r.candidate.id())){r.candidate.validUntil=current.validUntil;r.candidate.createdAt=current.createdAt;r.candidate.entry=current.entry;r.candidate.stop=current.stop;r.candidate.target=current.target;current=r.candidate;}else{expireCurrent("Setup changed after a new confirmed signal");current=r.candidate;lastAlertId=current.id();notifyTrade("New confirmed setup · "+current.label,current.side+" "+format(current.strike)+" · strength "+current.strength+"/100 · valid until "+when(current.validUntil),current.id());}}else{invalidBars++;if(invalidBars>=2)expireCurrent("Setup invalidated after two closed candles failed confirmation");}}
        }else if(r.candidate!=null){current=r.candidate;lastScannedClosedBar=r.closedAt;invalidBars=0;String id=current.id();if(!id.equals(lastAlertId)){lastAlertId=id;notifyTrade("Confirmed setup · "+current.label,current.side+" "+format(current.strike)+" · strength "+current.strength+"/100 · valid until "+when(current.validUntil),id);}}
    }
    private void scheduleRefresh(long delay){if(refreshLoop!=null)handler.removeCallbacks(refreshLoop);refreshLoop=()->{if(!foreground)return;refreshPrices();if("Signals".equals(tab)||current!=null)runScan(false);handler.postDelayed(refreshLoop,"Signals".equals(tab)?60000:30000);};handler.postDelayed(refreshLoop,delay);}
    private void scheduleClock(){if(clockLoop!=null)handler.removeCallbacks(clockLoop);clockLoop=()->{if(!foreground)return;if(current!=null&&current.validUntil<=System.currentTimeMillis())expireCurrent("Setup validity ended");else if(validityText!=null&&current!=null)validityText.setText("Validity remaining: "+remaining(current.validUntil));handler.postDelayed(clockLoop,1000);};handler.postDelayed(clockLoop,1000);}
    private void expireIfPastClose(){if(current!=null&&current.validUntil<=System.currentTimeMillis())expireCurrent("Setup validity ended");}
    private void expireCurrent(String reason){SignalScanner.Candidate old=current;if(old==null)return;current=null;invalidBars=0;lastAlertId="";notifyTrade(reason+" · "+old.label,old.side+" "+format(old.strike)+" is no longer active. If you entered, review your exit plan. No order was placed.","exit-"+old.id()+System.currentTimeMillis());if("Signals".equals(tab))render();}

    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager m=getSystemService(NotificationManager.class);m.createNotificationChannel(new NotificationChannel(CHANNEL,"Trade setup alerts",NotificationManager.IMPORTANCE_HIGH));}}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);}
    private void notifyTrade(String title,String message,String id){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pi).setAutoCancel(true);m.notify(id.hashCode(),b.build());}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PICK_ENV){io.execute(()->{String message;try{envStore.save(()->getContentResolver().openInputStream(uri));env=envStore.load();api=new NativeApi(env);scanner=new SignalScanner(api);message="Configuration imported and encrypted. Android settings are ready.";}catch(Exception e){message="Could not import file: "+safe(e.getMessage());}String finalMessage=message;handler.post(()->{Toast.makeText(this,finalMessage,Toast.LENGTH_LONG).show();render();if(api.hasUpstox())refreshPrices();});});}
        else if(request==PICK_PORTFOLIO){io.execute(()->{List<Holding> parsed=null;String message;try(ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IllegalArgumentException("Could not open selected file.");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>5*1024*1024)throw new IllegalArgumentException("Select an .xlsx or CSV file smaller than 5 MB.");out.write(b,0,n);}parsed=parsePortfolioFile(out.toByteArray());message=lastPortfolioImportSummary;}catch(Exception e){message="Portfolio import failed: "+safe(e.getMessage());}List<Holding> finalParsed=parsed;String finalMessage=message;handler.post(()->{if(finalParsed!=null){holdings.clear();holdings.addAll(finalParsed);portfolioResults.clear();portfolioErrors.clear();growthCandidates.clear();saveHoldings();}Toast.makeText(this,finalMessage,Toast.LENGTH_LONG).show();render();});});}
    }

    private void title(String a,String b){TextView eyebrow=text("◈  MARKET RESEARCH",10,colorAccent());eyebrow.setTypeface(null,Typeface.BOLD);eyebrow.setLetterSpacing(.12f);body.addView(eyebrow);TextView t=text(a,25,colorText());t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(3),0,0);body.addView(t);TextView sub=text(b,12,colorMuted());sub.setPadding(0,dp(3),0,dp(13));body.addView(sub);}
    private void configureSystemBars(){
        getWindow().setStatusBarColor(colorBg());getWindow().setNavigationBarColor(colorBg());
        int flags=light?(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(background(new int[]{colorPanelTop(),colorPanel()},dp(17)));c.setElevation(dp(3));return c;}
    private LinearLayout.LayoutParams spaceBottom(){return new LinearLayout.LayoutParams(-1,-2){{bottomMargin=dp(10);}};}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(true);return t;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:colorText());b.setPadding(dp(12),dp(4),dp(12),dp(4));b.setBackground(background(primary?new int[]{Color.rgb(0,205,164),Color.rgb(34,137,242)}:new int[]{colorPanelTop(),colorPanel()},dp(13)));if(primary)b.setTypeface(null,Typeface.BOLD);b.setElevation(dp(primary?3:1));return b;}
    private android.graphics.drawable.GradientDrawable background(int[] colors,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,colors);d.setCornerRadius(dp(radius));d.setStroke(dp(1),light?Color.rgb(219,229,244):Color.rgb(47,70,99));return d;}
    private android.graphics.drawable.GradientDrawable round(int color,int radius){return background(new int[]{color,color},radius);}
    private int colorBg(){return light?Color.rgb(242,247,255):Color.rgb(8,15,30);}private int colorBgEnd(){return light?Color.rgb(229,241,255):Color.rgb(13,24,44);}private int colorPanel(){return light?Color.rgb(255,255,255):Color.rgb(17,28,49);}private int colorPanelTop(){return light?Color.rgb(255,255,255):Color.rgb(25,40,66);}private int colorText(){return light?Color.rgb(21,38,65):Color.rgb(239,246,255);}private int colorMuted(){return light?Color.rgb(82,105,137):Color.rgb(155,177,210);}private int colorAccent(){return Color.rgb(0,204,163);}private int priceColor(double n){return n>=0?Color.rgb(0,204,145):Color.rgb(246,84,103);}private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private boolean configured(String key){return env!=null&&!env.getOrDefault(key,"").trim().isEmpty();}private String mask(String s){return s.length()<5?"••••":s.substring(0,2)+"•••"+s.substring(s.length()-2);}private String safe(String s){if(s==null||s.trim().isEmpty())return"Unexpected error";return s.length()>240?s.substring(0,240):s;}private String format(double n){return NumberFormat.getNumberInstance(new Locale("en","IN")).format(n);}private String rupee(double n){return"₹"+String.format(Locale.US,"%,.2f",n);}private String signed(double n){return(n>=0?"+":"")+String.format(Locale.US,"%.2f",n);}private String when(long t){return java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm 'IST'"));}private String remaining(long t){long s=Math.max(0,(t-System.currentTimeMillis())/1000);return String.format(Locale.US,"%02d:%02d",s/60,s%60);}
    private void updateTopStatus(){if(topStatus==null)return;String source=api.hasUpstox()?"Upstox market data configured":"Import .env to enable live market data";topStatus.setText(source+(priceAvailable[0]?" · NIFTY "+rupee(price[0])+" "+signed(change[0])+"%":"")+(current!=null?" · Setup valid until "+when(current.validUntil):""));topStatus.setTextColor(current!=null?colorAccent():colorMuted());}
}
