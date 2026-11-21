package com.reimagined.winner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rule-based portfolio research using actual completed daily candles. Scores are not probabilities. */
public final class PortfolioResearch {
    private static final ZoneId IST=ZoneId.of("Asia/Kolkata");
    private PortfolioResearch() {}

    public static Result analyze(List<NativeApi.Bar> input) {
        List<NativeApi.Bar> bars=new ArrayList<>(input==null?Collections.emptyList():input);
        bars.sort((a,b)->Long.compare(a.time,b.time));
        LocalDate today=LocalDate.now(IST);int minute=ZonedDateTime.now(IST).getHour()*60+ZonedDateTime.now(IST).getMinute();
        if(!bars.isEmpty()&&LocalDate.ofInstant(Instant.ofEpochSecond(bars.get(bars.size()-1).time),IST).equals(today)&&minute<930)bars.remove(bars.size()-1);
        if(bars.size()<25)throw new IllegalArgumentException("Need at least 25 completed daily candles; received "+bars.size()+".");

        int n=bars.size();NativeApi.Bar last=bars.get(n-1);double hi=-Double.MAX_VALUE,lo=Double.MAX_VALUE;double[] volumes=new double[20];
        int start=n-21;
        for(int i=start;i<n-1;i++){NativeApi.Bar b=bars.get(i);hi=Math.max(hi,b.high);lo=Math.min(lo,b.low);volumes[i-start]=b.volume;}
        int lowAt=-1,highAt=-1;for(int i=start;i<n-1;i++){NativeApi.Bar b=bars.get(i);if(b.low==lo)lowAt=i;if(b.high==hi)highAt=i;}
        boolean oldLow=(n-2-lowAt)>=4,oldHigh=(n-2-highAt)>=4;
        int turtle=oldLow&&last.low<lo&&last.close>lo?1:oldHigh&&last.high>hi&&last.close<hi?-1:0;
        String turtleLabel=turtle>0?"Bullish failed 20-day low breakout":turtle<0?"Bearish failed 20-day high breakout":"No confirmed daily Turtle Soup";

        double c10=bars.get(Math.max(0,n-10)).close,c20=bars.get(Math.max(0,n-20)).close;
        String trend=last.close>c10*1.02&&c10>c20*1.01?"Bullish":last.close<c10*.98&&c10<c20*.99?"Bearish":"Neutral";
        double rangeHi=-Double.MAX_VALUE,rangeLo=Double.MAX_VALUE;for(int i=Math.max(0,n-30);i<n;i++){rangeHi=Math.max(rangeHi,bars.get(i).high);rangeLo=Math.min(rangeLo,bars.get(i).low);}
        String phase=last.close>rangeHi?"D · SOS":trend.equals("Bullish")?"E · Markup":last.close<rangeLo?"A · Markdown":(rangeHi-rangeLo)/last.close<.06?"C · Base":"B · Range";
        double[] sorted=volumes.clone();java.util.Arrays.sort(sorted);double median=(sorted[9]+sorted[10])/2;
        double volumeRatio=median>0?last.volume/median:0;int volumeDirection=volumeRatio>=1.15?(last.close>last.open?1:last.close<last.open?-1:0):0;
        int trendDirection=trend.equals("Bullish")?1:trend.equals("Bearish")?-1:0;
        int wyckoff=phase.startsWith("D")||phase.startsWith("E")?1:phase.startsWith("A")?-1:0;
        // Daily Turtle Soup is the investment validation. This score also incorporates trend, phase and volume.
        double ret20=last.close/bars.get(Math.max(0,n-21)).close-1,ret60=last.close/bars.get(Math.max(0,n-61)).close-1;
        int momentum=Double.compare(ret20+ret60,0);
        int score=(int)Math.round(100*(.28*trendDirection+.24*wyckoff+.26*turtle+.12*volumeDirection+.10*momentum));
        String action=turtle<0||score<=-35?"REVIEW":score>=45?"HOLD / WATCH ADD":score>=10?"HOLD":"REVIEW";
        Result r=new Result();r.score=score;r.trend=trend;r.phase=phase;r.turtleSoup=turtleLabel;r.turtleDirection=turtle;r.action=action;r.volumeRatio=volumeRatio;r.lastClose=last.close;r.asOf=LocalDate.ofInstant(Instant.ofEpochSecond(last.time),IST).toString();return r;
    }

    public static boolean isGrowthCandidate(Result r){return r!=null&&r.score>=35&&r.trend.equals("Bullish")&&r.turtleDirection>=0;}
    public static final class Result {public int score,turtleDirection;public String trend,phase,turtleSoup,action,asOf;public double volumeRatio,lastClose;}
}
