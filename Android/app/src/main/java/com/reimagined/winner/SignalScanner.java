package com.reimagined.winner;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Conservative native scanner: only completed candles, alignment, weighted constituents, and option liquidity. */
public final class SignalScanner {
    private static final ZoneId IST=ZoneId.of("Asia/Kolkata");
    private final NativeApi api;
    private final ExecutorService componentPool=Executors.newFixedThreadPool(4);
    private final Map<String,CachedComponent> componentCache=new HashMap<>();
    public SignalScanner(NativeApi api){this.api=api;}

    public Result scan(int index) throws Exception {
        Result result=new Result();result.index=index;result.symbol=NativeApi.SYMBOLS[index];
        if(!api.hasUpstox())throw new IllegalStateException("Add UPSTOX_ACCESS_TOKEN or UPSTOX_ANALYTICS_TOKEN to your imported environment file to run the native scanner.");
        if(!marketOpen())throw new IllegalStateException("The Indian market is closed. Live recommendations pause outside 09:15–15:30 IST on trading days.");
        List<NativeApi.Bar> bars=api.intraday(NativeApi.INDEX_KEYS[index]);
        long thisMinute=Instant.now().getEpochSecond()/60*60;
        List<NativeApi.Bar> closed=new ArrayList<>();for(NativeApi.Bar b:bars)if(b.time<thisMinute)closed.add(b);
        if(closed.size()<24)throw new IllegalStateException("Waiting for at least 24 completed one-minute candles from Upstox. Available: "+closed.size()+".");
        NativeApi.Bar last=closed.get(closed.size()-1),prior=closed.get(closed.size()-2);
        Signal a=signal(closed,closed.size()-2),b=signal(closed,closed.size()-1);
        result.closedAt=last.time;result.spot=last.close;result.pattern=a.label+" → "+b.label+" · "+b.turtleSoupLabel;result.bias=b.bias;
        if(a.direction==0||b.direction==0||a.direction!=b.direction){result.message="Waiting for two consecutive aligned, closed candles.";result.pending=true;return result;}
        Major major=major(index);
        result.majorScore=major.score;result.majorCoverage=major.coverage;result.majorAsOf=major.asOf;result.majorSummary=major.summary;
        if(b.direction*major.score < -0.18 && major.coverage>=0.60){result.message="Major constituent pattern opposes the index signal; candidate filtered.";result.filtered=true;return result;}
        int dir=b.direction;
        double alignment=Math.max(0,Math.min(1,Math.abs(b.bias)));
        double component=major.coverage>=0.50?Math.max(0,Math.min(1,(1+dir*major.score)/2)):0.5,tsSupport=b.turtleSoupDirection==dir?1:b.turtleSoupDirection==0?.5:0;
        result.strength=(int)Math.round(100*(0.60*alignment+0.30*component+0.10*tsSupport));
        if(result.strength<62){result.message="Confirmed direction, but signal strength is below the 62/100 threshold.";result.filtered=true;return result;}
        result.candidate=buildCandidate(index,dir,last.close,result.strength,major,last.time,b.turtleSoupLabel);
        result.message=result.candidate==null?"The index signal is confirmed, but no liquid option passed the spread, OI and quote checks.":"Confirmed on two closed one-minute candles.";
        return result;
    }

    private Signal signal(List<NativeApi.Bar> bars,int idx){
        Signal s=new Signal();NativeApi.Bar bar=bars.get(idx);int lo=Math.max(0,idx-12),refLo=Math.max(0,idx-22);
        if(idx-lo<8){s.label="Insufficient history";return s;}
        double high=-Double.MAX_VALUE,low=Double.MAX_VALUE,vsum=0;int vn=0;
        for(int i=refLo;i<idx;i++){NativeApi.Bar x=bars.get(i);if(x.volume>0){vsum+=x.volume;vn++;}}
        for(int i=lo;i<idx;i++){NativeApi.Bar x=bars.get(i);high=Math.max(high,x.high);low=Math.min(low,x.low);}
        double vma=vn>0?vsum/vn:0,vr=vma>0?bar.volume/vma:0;
        boolean spring=bar.low<low&&bar.close>low,upthrust=bar.high>high&&bar.close<high;
        boolean sos=bar.close>high&&vr>=1.15,sow=bar.close<low&&vr>=1.15;
        int wy=spring||sos?1:upthrust||sow?-1:0;
        boolean sweepUp=spring,sweepDown=upthrust,bosUp=bar.close>high,bosDown=bar.close<low;
        int smc=sweepUp||bosUp?1:sweepDown||bosDown?-1:0;
        int from=Math.max(0,idx-15);double sumSq=0;int n=0;
        for(int i=from+1;i<=idx;i++){double r=Math.log(bars.get(i).close/bars.get(i-1).close);sumSq+=r*r;n++;}
        double sd=n>0?Math.sqrt(sumSq/n):0;int k=Math.min(15,idx-from);double z=sd>0?Math.log(bar.close/bars.get(idx-k).close)/(sd*Math.sqrt(k)):0;
        int vmaDir=vr>=1.15?(bar.close>bar.open?1:bar.close<bar.open?-1:0):0;
        double bias=clamp(.25*clamp(z/2,-1,1)+.30*wy+.30*smc+.15*vmaDir,-1,1);
        int dir=bias>=.30?1:bias<=-.30?-1:0;
        if(dir*wy<0||dir*smc<0)dir=0;
        int refStart=Math.max(0,idx-20);double high20=-Double.MAX_VALUE,low20=Double.MAX_VALUE;for(int i=refStart;i<idx;i++){high20=Math.max(high20,bars.get(i).high);low20=Math.min(low20,bars.get(i).low);}
        int turtleDirection=idx-refStart>=20?(bar.low<low20&&bar.close>low20?1:bar.high>high20&&bar.close<high20?-1:0):0;
        String turtleLabel=idx-refStart<20?"Turtle Soup unavailable · needs 20 bars":turtleDirection>0?"Bullish 20-bar failed-low sweep":turtleDirection<0?"Bearish 20-bar failed-high sweep":"No 20-bar Turtle Soup proxy";
        if(turtleDirection!=0&&dir*turtleDirection<0)dir=0;
        String event=spring?"Spring":upthrust?"Upthrust":sos?"SOS breakout":sow?"SOW breakdown":bosUp?"Bullish BOS":bosDown?"Bearish BOS":"No structure break";
        s.direction=dir;s.bias=bias;s.label=event;s.time=bar.time;s.turtleSoupDirection=turtleDirection;s.turtleSoupLabel=turtleLabel;return s;
    }

    private Candidate buildCandidate(int index,int dir,double spot,int strength,Major major,long closedBarTime,String turtleSoup)throws Exception{
        JSONArray contracts=api.optionContracts(NativeApi.INDEX_KEYS[index]);String expiry=null;LocalDate today=LocalDate.now(IST);
        for(int i=0;i<contracts.length();i++){JSONObject c=contracts.optJSONObject(i);if(c==null)continue;String e=c.optString("expiry","");if(e.length()>=10)e=e.substring(0,10);try{LocalDate d=LocalDate.parse(e);if(d.isAfter(today)&& (expiry==null||d.isBefore(LocalDate.parse(expiry))))expiry=e;}catch(Exception ignored){}}
        if(expiry==null)throw new IllegalStateException("Upstox returned no unexpired options for this index.");
        JSONArray rows=api.optionChain(NativeApi.INDEX_KEYS[index],expiry);JSONObject chosen=null,leg=null;double best=Double.MAX_VALUE;String type=dir>0?"CE":"PE";
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;double strike=row.optDouble("strike_price",0);if(strike<=0)continue;
            JSONObject options=row.optJSONObject(type.equals("CE")?"call_options":"put_options");if(options==null)continue;
            JSONObject md=options.optJSONObject("market_data"),greeks=options.optJSONObject("option_greeks");if(md==null)continue;
            double ask=md.optDouble("ask_price",0),bid=md.optDouble("bid_price",0),oi=md.optDouble("oi",0),volume=md.optDouble("volume",0),delta=greeks==null?0:Math.abs(greeks.optDouble("delta",0));
            if(ask<=0||bid<=0||oi<1000||volume<100||delta<.20||delta>.75)continue;
            double spread=(ask-bid)/ask;if(spread>.12)continue;
            double dist=Math.abs(strike-spot)/spot;
            if(dist>.035)continue;
            double score=dist+spread*.8+(delta<.25||delta>.70?.006:0);
            if(score<best){best=score;chosen=row;leg=options;}
        }
        if(chosen==null)return null;
        JSONObject md=leg.optJSONObject("market_data"),g=leg.optJSONObject("option_greeks");double entry=md.optDouble("ask_price",0);if(entry<=0)return null;
        Candidate c=new Candidate();c.index=index;c.symbol=NativeApi.SYMBOLS[index];c.label=NativeApi.LABELS[index];c.side="BUY "+type;c.expiry=expiry;c.strike=chosen.optDouble("strike_price");c.entry=entry;c.bid=md.optDouble("bid_price");c.delta=g==null?0:Math.abs(g.optDouble("delta",0));c.oi=md.optDouble("oi");c.volume=md.optDouble("volume");c.strength=strength;c.spot=spot;c.majorScore=major.score;c.majorCoverage=major.coverage;c.majorAsOf=major.asOf;c.majorSummary=major.summary;c.turtleSoup=turtleSoup;
        c.stop=Math.max(.05,Math.round(entry*.85*20)/20.0);c.target=Math.max(.05,Math.round(entry*1.20*20)/20.0);
        ZonedDateTime now=ZonedDateTime.now(IST),close=now.withHour(15).withMinute(30).withSecond(0).withNano(0);long twoHours=now.plusHours(2).toInstant().toEpochMilli();long closeMs=close.toInstant().toEpochMilli();
        long expiryMs=LocalDate.parse(expiry).atTime(15,30).atZone(IST).toInstant().toEpochMilli();c.validUntil=Math.min(twoHours,Math.min(closeMs,expiryMs));c.createdAt=System.currentTimeMillis();c.closedBarTime=closedBarTime*1000;return c;
    }

    private Major major(int index){
        // Index factsheet weights are dated; their influence fades after 30 days. Unmapped weights do not count as covered.
        String[] names;double[] weights;String asOf;
        if(index==0){names=new String[]{"HDFCBANK","ICICIBANK","RELIANCE","BHARTIARTL","LT","INFY","SBIN"};weights=new double[]{9.95,9.39,7.30,5.63,4.25,3.92,3.73};asOf="2026-10-30";}
        else if(index==1){names=new String[]{"HDFCBANK","ICICIBANK","AXISBANK","SBIN","KOTAKBANK","FEDERALBNK","INDUSINDBK","AUBANK","BANKBARODA","IDFCFIRSTB"};weights=new double[]{18.37,13.55,10.02,9.93,9.67,6.27,5.35,4.97,4.34,4.12};asOf="2026-04-30";}
        else {names=new String[]{"HDFCBANK","ICICIBANK","RELIANCE","BHARTIARTL","LT","INFY","SBIN","ITC"};weights=new double[]{9.97,9.44,7.28,5.80,4.26,3.94,3.77,2.42};asOf="2026-10-30";}
        Map<String,String> keys=new HashMap<>();keys.put("RELIANCE","NSE_EQ|INE002A01018");keys.put("HDFCBANK","NSE_EQ|INE040A01034");keys.put("ICICIBANK","NSE_EQ|INE090A01021");keys.put("SBIN","NSE_EQ|INE062A01020");keys.put("INFY","NSE_EQ|INE009A01021");keys.put("LT","NSE_EQ|INE018A01030");keys.put("BHARTIARTL","NSE_EQ|INE397D01024");keys.put("AXISBANK","NSE_EQ|INE238A01034");keys.put("KOTAKBANK","NSE_EQ|INE237A01028");keys.put("FEDERALBNK","NSE_EQ|INE171A01029");keys.put("INDUSINDBK","NSE_EQ|INE095A01012");keys.put("AUBANK","NSE_EQ|INE949L01017");keys.put("BANKBARODA","NSE_EQ|INE028A01039");keys.put("IDFCFIRSTB","NSE_EQ|INE092T01019");keys.put("ITC","NSE_EQ|INE154A01025");
        double total=0,covered=0,weighted=0;int count=0;List<String> tags=new ArrayList<>();List<Future<CachedComponent>> futures=new ArrayList<>();List<String> symbols=new ArrayList<>();
        for(int i=0;i<names.length;i++){total+=weights[i];String key=keys.get(names[i]);if(key==null)continue;symbols.add(names[i]);final String symbol=names[i],instrument=key;futures.add(componentPool.submit(()->componentPattern(symbol,instrument)));}
        for(int i=0;i<futures.size();i++){
            try{CachedComponent c=futures.get(i).get(20,TimeUnit.SECONDS);if(c==null)continue;double weight=weights[Arrays.asList(names).indexOf(symbols.get(i))];covered+=weight;weighted+=weight*c.score;count++;tags.add(symbols.get(i)+(c.score>=0?" +":" ")+String.format(Locale.US,"%.1f",c.score));}
            catch(Exception ignored){futures.get(i).cancel(true);}
        }
        double coverage=total>0?covered/total:0,raw=covered>0?weighted/covered:0;long age=Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(asOf),LocalDate.now(IST)));double freshness=Math.exp(-Math.max(0,age-30)/120.0);
        Major m=new Major();m.score=raw*freshness;m.coverage=coverage;m.asOf=asOf;m.summary=count+"/"+names.length+" mapped constituents · "+Math.round(coverage*100)+"% weight coverage · "+String.join(", ",tags.subList(0,Math.min(4,tags.size())));return m;
    }
    private CachedComponent componentPattern(String symbol,String key)throws Exception{
        synchronized(componentCache){CachedComponent cached=componentCache.get(symbol);if(cached!=null&&System.currentTimeMillis()-cached.at<300000)return cached;}
        List<NativeApi.Bar>b=api.daily(key);if(b.size()>0&&Instant.ofEpochSecond(b.get(b.size()-1).time).atZone(IST).toLocalDate().equals(LocalDate.now(IST)))b.remove(b.size()-1);if(b.size()<25)return null;
        NativeApi.Bar last=b.get(b.size()-1);List<NativeApi.Bar> prev=b.subList(Math.max(0,b.size()-21),b.size()-1);if(prev.size()<15)return null;
        double hi=-1e99,lo=1e99,sma=0,retSum=0,sq=0;for(NativeApi.Bar x:prev){hi=Math.max(hi,x.high);lo=Math.min(lo,x.low);sma+=x.close;}sma/=prev.size();
        int base=Math.max(0,b.size()-21);double ret20=last.close/b.get(base).close-1;for(int j=Math.max(1,b.size()-20);j<b.size();j++){double r=Math.log(b.get(j).close/b.get(j-1).close);retSum+=r;sq+=r*r;}int n=Math.max(1,Math.min(20,b.size()-1));double vol=Math.sqrt(Math.max(0,sq/n-(retSum/n)*(retSum/n)));
        double trend=clamp(ret20/Math.max(.012,vol*Math.sqrt(20)*1.5),-1,1),position=clamp((last.close/sma-1)/.025,-1,1);int structure=last.close>hi?1:last.close<lo?-1:last.low<lo&&last.close>lo?1:last.high>hi&&last.close<hi?-1:0;double score=clamp(.58*trend+.27*structure+.15*position,-1,1);
        CachedComponent result=new CachedComponent();result.at=System.currentTimeMillis();result.score=score;synchronized(componentCache){componentCache.put(symbol,result);}return result;
    }
    private boolean marketOpen(){ZonedDateTime n=ZonedDateTime.now(IST);int day=n.getDayOfWeek().getValue();int minute=n.getHour()*60+n.getMinute();return day<=5&&minute>=555&&minute<930;}
    private static double clamp(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}

    private static final class Signal {int direction,turtleSoupDirection;double bias;String label,turtleSoupLabel;long time;}
    private static final class Major {double score,coverage;String asOf,summary;}
    private static final class CachedComponent {long at;double score;}
    public static final class Result {public int index,strength;public String symbol,pattern,message,majorSummary,majorAsOf;public boolean pending,filtered;public double spot,bias,majorScore,majorCoverage;public long closedAt;public Candidate candidate;}
    public static final class Candidate {public int index,strength;public String symbol,label,side,expiry,majorAsOf,majorSummary,turtleSoup;public double strike,entry,bid,delta,oi,volume,stop,target,spot,majorScore,majorCoverage;public long validUntil,createdAt,closedBarTime;public String id(){return symbol+"|"+side+"|"+strike+"|"+expiry;}}
}
