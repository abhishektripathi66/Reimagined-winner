package com.reimagined.winner;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/** Direct HTTPS adapters used by the Android client; no local Node process or browser proxy. */
public final class NativeApi {
    public static final String[] SYMBOLS = {"NIFTY", "BANKNIFTY", "SENSEX"};
    public static final String[] LABELS = {"NIFTY 50", "BANK NIFTY", "SENSEX"};
    public static final String[] INDEX_KEYS = {"NSE_INDEX|Nifty 50", "NSE_INDEX|Nifty Bank", "BSE_INDEX|SENSEX"};
    public static final Map<String,String> STOCK_KEYS = stockKeys();
    public static final String[] RESEARCH_SYMBOLS = STOCK_KEYS.keySet().toArray(new String[0]);
    private final Map<String,String> env;
    public NativeApi(Map<String,String> env) { this.env = env; }

    public boolean hasUpstox() { return !v("UPSTOX_ACCESS_TOKEN").isEmpty() || !v("UPSTOX_ANALYTICS_TOKEN").isEmpty(); }
    public String upstoxToken() { String t = v("UPSTOX_ANALYTICS_TOKEN"); return t.isEmpty() ? v("UPSTOX_ACCESS_TOKEN") : t; }
    public String value(String k) { return v(k); }

    public JSONObject upstox(String path) throws Exception { return request("https://api.upstox.com" + path, "GET", null, upstoxToken(), "upstox"); }
    public JSONObject indexQuotes() throws Exception {
        if (!hasUpstox()) throw new IllegalStateException("UPSTOX_ACCESS_TOKEN or UPSTOX_ANALYTICS_TOKEN is missing in the imported configuration.");
        StringBuilder keys = new StringBuilder();
        for (String key : INDEX_KEYS) { if (keys.length() > 0) keys.append(','); keys.append(URLEncoder.encode(key, "UTF-8")); }
        JSONObject response = upstox("/v2/market-quote/quotes?instrument_key=" + keys);
        JSONObject data = response.optJSONObject("data"); if (data == null) throw new IllegalStateException("Upstox returned no quote data.");
        return data;
    }

    public List<Bar> intraday(String key) throws Exception {
        JSONObject response = upstox("/v3/historical-candle/intraday/" + pathEncode(key) + "/minutes/1");
        JSONObject data = response.optJSONObject("data"); JSONArray arr = data == null ? null : data.optJSONArray("candles");
        List<Bar> bars = parseBars(arr); bars.sort((a,b) -> Long.compare(a.time,b.time)); return bars;
    }
    public List<Bar> daily(String key) throws Exception {
        java.time.LocalDate end = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        String path = "/v3/historical-candle/" + pathEncode(key) + "/days/1/" + end + "/" + end.minusDays(220);
        JSONObject response = upstox(path); JSONObject data = response.optJSONObject("data"); JSONArray arr = data == null ? null : data.optJSONArray("candles");
        List<Bar> bars = parseBars(arr); bars.sort((a,b) -> Long.compare(a.time,b.time)); return bars;
    }
    private List<Bar> parseBars(JSONArray rows) {
        List<Bar> result = new ArrayList<>(); if (rows == null) return result;
        for (int i=0;i<rows.length();i++) {
            JSONArray r = rows.optJSONArray(i); if (r == null || r.length() < 5) continue;
            Object time = r.opt(0); long epoch = 0;
            try { if (time instanceof Number) { epoch=((Number)time).longValue(); if(epoch>1000000000000L)epoch/=1000; } else epoch=Instant.parse(String.valueOf(time)).getEpochSecond(); } catch(Exception ignored) { try { epoch=java.time.OffsetDateTime.parse(String.valueOf(time)).toEpochSecond(); } catch(Exception ignored2) {} }
            if (epoch <= 0) continue;
            Bar b = new Bar(); b.time=epoch; b.open=r.optDouble(1); b.high=r.optDouble(2); b.low=r.optDouble(3); b.close=r.optDouble(4); b.volume=r.optDouble(5); if(r.length()>6)b.oi=r.optDouble(6);
            if(b.close>0) result.add(b);
        }
        return result;
    }
    public JSONArray optionContracts(String indexKey) throws Exception {
        JSONObject r = upstox("/v2/option/contract?instrument_key=" + URLEncoder.encode(indexKey, "UTF-8"));
        JSONArray data = r.optJSONArray("data"); if(data==null)throw new IllegalStateException("No active option contracts returned by Upstox."); return data;
    }
    public JSONArray optionChain(String indexKey, String expiry) throws Exception {
        JSONObject r = upstox("/v2/option/chain?instrument_key=" + URLEncoder.encode(indexKey, "UTF-8") + "&expiry_date=" + URLEncoder.encode(expiry,"UTF-8"));
        JSONArray data = r.optJSONArray("data"); if(data==null)throw new IllegalStateException("No option chain returned for expiry " + expiry + "."); return data;
    }

    public String ask(String provider, String question, String context) throws Exception {
        String system = "You are a concise Indian market options research assistant. Discuss only the supplied data, state uncertainty, never promise returns, and do not place orders. The output is educational information, not financial advice.";
        if (provider.equals("Gemini")) {
            String key=v("GEMINI_API_KEY"); if(key.isEmpty())throw new IllegalStateException("GEMINI_API_KEY is not configured in the imported environment file.");
            String model=v("GEMINI_MODEL");if(model.isEmpty())model="gemini-2.5-flash";
            JSONObject part=new JSONObject().put("text",system+"\n\nApp context:\n"+context+"\n\nUser question:\n"+question);
            JSONObject body=new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(part))));
            JSONObject r=request("https://generativelanguage.googleapis.com/v1beta/models/"+URLEncoder.encode(model,"UTF-8")+":generateContent","POST",body.toString(),key,"gemini");
            JSONArray c=r.optJSONArray("candidates");return c!=null&&c.length()>0?c.getJSONObject(0).optJSONObject("content").optJSONArray("parts").optJSONObject(0).optString("text","No response text."):"No response text.";
        }
        if (provider.equals("Claude")) {
            String key=v("ANTHROPIC_API_KEY");if(key.isEmpty())throw new IllegalStateException("ANTHROPIC_API_KEY is not configured in the imported environment file.");
            String model=v("ANTHROPIC_MODEL");if(model.isEmpty())model="claude-sonnet-4-20250514";
            JSONObject body=new JSONObject().put("model",model).put("max_tokens",800).put("system",system+"\nApp context:\n"+context).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",question)));
            JSONObject r=request("https://api.anthropic.com/v1/messages","POST",body.toString(),key,"anthropic");JSONArray c=r.optJSONArray("content");return c!=null&&c.length()>0?c.getJSONObject(0).optString("text","No response text."):"No response text.";
        }
        String key=v("OPENAI_API_KEY");if(key.isEmpty())throw new IllegalStateException("OPENAI_API_KEY is not configured in the imported environment file.");
        String model=v("OPENAI_MODEL");if(model.isEmpty())model="gpt-4o-mini";
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content",system+"\nApp context:\n"+context)).put(new JSONObject().put("role","user").put("content",question));
        JSONObject r=request("https://api.openai.com/v1/chat/completions","POST",new JSONObject().put("model",model).put("messages",messages).toString(),key,"openai");
        return r.optJSONArray("choices")!=null&&r.getJSONArray("choices").length()>0?r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","No response text."):"No response text.";
    }

    private JSONObject request(String url, String method, String body, String credential, String kind) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(16000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");
        if(kind.equals("upstox")){c.setRequestProperty("Authorization","Bearer "+credential);}
        else if(kind.equals("anthropic")){c.setRequestProperty("x-api-key",credential);c.setRequestProperty("anthropic-version","2023-06-01");}
        else if(kind.equals("gemini")){c.setRequestProperty("x-goog-api-key",credential);}
        else if(kind.equals("openai")){c.setRequestProperty("Authorization","Bearer "+credential);}
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
        int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();String text="";
        if(stream!=null){try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)break;out.write(b,0,n);}text=new String(out.toByteArray(),StandardCharsets.UTF_8);}}
        c.disconnect();if(status<200||status>=300){String detail=text;try{JSONObject error=new JSONObject(text);if(error.optJSONObject("error")!=null)detail=error.getJSONObject("error").optString("message",text);else detail=error.optString("message",text);}catch(Exception ignored){}
            throw new IllegalStateException(kind+" HTTP "+status+(detail.isEmpty()?"":" — "+detail.substring(0,Math.min(220,detail.length()))));}
        return text.isEmpty()?new JSONObject():new JSONObject(text);
    }
    private String v(String k){return env.getOrDefault(k,"").trim();}
    public static String instrumentForSymbol(String symbol){return STOCK_KEYS.get(symbol==null?"":symbol.trim().toUpperCase(Locale.US));}
    private static Map<String,String> stockKeys(){Map<String,String> m=new LinkedHashMap<>();m.put("RELIANCE","NSE_EQ|INE002A01018");m.put("HDFCBANK","NSE_EQ|INE040A01034");m.put("ICICIBANK","NSE_EQ|INE090A01021");m.put("SBIN","NSE_EQ|INE062A01020");m.put("INFY","NSE_EQ|INE009A01021");m.put("TCS","NSE_EQ|INE467B01029");m.put("LT","NSE_EQ|INE018A01030");m.put("BHARTIARTL","NSE_EQ|INE397D01024");m.put("AXISBANK","NSE_EQ|INE238A01034");m.put("KOTAKBANK","NSE_EQ|INE237A01028");m.put("FEDERALBNK","NSE_EQ|INE171A01029");m.put("INDUSINDBK","NSE_EQ|INE095A01012");m.put("AUBANK","NSE_EQ|INE949L01017");m.put("BANKBARODA","NSE_EQ|INE028A01039");m.put("IDFCFIRSTB","NSE_EQ|INE092T01019");m.put("ITC","NSE_EQ|INE154A01025");m.put("BAJFINANCE","NSE_EQ|INE296A01024");return java.util.Collections.unmodifiableMap(m);}
    private String pathEncode(String value)throws Exception{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}

    public static final class Bar { public long time; public double open,high,low,close,volume,oi; }
}
