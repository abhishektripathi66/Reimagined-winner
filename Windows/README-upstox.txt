Reimagined Winner + Upstox option-chain setup

Files
- reimagined-winner-upstox.html: app with Upstox live option-chain support.
- dhan-proxy.js: local connector for Upstox market data, Dhan, and Yahoo Finance.

Setup
1. Open Upstox Developer Apps: https://account.upstox.com/developer/apps
2. Select the Analytics tab, generate a free Analytics Token, and copy the full token.
3. Keep both files in the same folder. Install Node.js if it is not already installed.
4. Open PowerShell in that folder and run:
   node .\dhan-proxy.js
   Leave that window running.
5. Open reimagined-winner-upstox.html in Chrome. Go to Options Intel → Upstox Data, paste the Analytics Token, then click Verify & connect. The token is saved before API verification, so a permission error does not erase it; a rejected request still means that particular API cannot provide data.

No OAuth redirect URI or API secret is needed. The Analytics Token is read-only, supports Upstox market-data APIs, and is saved in the current browser profile until disconnected. Upstox lists its validity as one year. This connection does not connect Upstox order placement or change the configured order broker.

If port 8765 is already in use, stop the other program or start this proxy on another port, for example:
   node .\dhan-proxy.js 8790
Then set the app's Local proxy URL to http://127.0.0.1:8790/dhan.

If token verification returns an API permission error, confirm you generated an Analytics Token from the Developer Apps Analytics tab and that it has not been revoked.

Backtest note
The app's Backtest direction button replays up to one month of one-minute index candles using a 75% earlier-session / 25% held-out-session split. It reports index-direction hit rate only; it is not a backtest of option premiums, strike selection, stop/target fills, or fees. Upstox marks expired-option contract and candle APIs as Plus-only, so a reliable multi-expiry option P&L backtest is not available through the free Analytics Token. No backtest guarantees future results.

Candidate display filter
Options Intel shows only long BUY CE and BUY PE candidates with a modelled P(T1 before SL) from 60% through 90%, inclusive. Candidates outside that band are hidden. This percentage is a scenario-model output, not a historical options win rate or a guarantee; no qualifying setup means the app shows no trade.


Token storage
The app saves the Analytics Token in browser local storage so it survives closing and reopening the same HTML in the same Chrome profile. If local storage is blocked, it falls back to session storage. Use Disconnect Upstox data to delete the saved token. The browser profile is the security boundary; anyone with access to it may use the token.

Live index updates
The app subscribes to Upstox's V3 market-data WebSocket for NIFTY, BANK NIFTY, and SENSEX. The displayed index value updates on each tick received (event-driven; the market does not necessarily publish a tick every fraction of a second). Option-chain and signal scans remain on the selected 15/30/60-second schedule. This keeps the spot display responsive without repeatedly requesting the full option chain.

AI model
Gemini calls use the current gemini-3.8-flash generateContent endpoint. The previous gemini-1.5-flash model name has been retired.

Trade notifications
In Options Intel, click Enable alerts once and allow browser notifications. The app sends one desktop notification when a new qualifying BUY CE/BUY PE setup appears, and includes an in-app fallback. After alerts are enabled, keep the app open and the LIVE toggle on; scans continue when you switch to another page in the app. Alerts use the app's selected index and existing 60–90% model filter. Email, WhatsApp, and SMS delivery are not configured.

Backtest
Historical candle timestamps are normalized before replay so the backtest no longer reads an uninitialized timestamp variable.

Cross-market scan
Options Intel → Find best across can scan the selected instrument, all three indices, eight liquid NSE F&O stocks (RELIANCE, HDFCBANK, ICICIBANK, SBIN, INFY, TCS, LT, BHARTIARTL), or the combined 11-symbol set. Wide scans are throttled to at least 60 seconds. Stock-option ideas require Upstox option-contract and live-chain access; if the token does not have that access, the stock symbols are shown as unavailable rather than using guessed lot sizes or stale option prices. The instrument master keys are the NSE_EQ ISIN keys published by Upstox.

Reimagined Winner — local backend setup
======================================

1. Fill in the values you use in .env. Leave other providers blank.
2. Open PowerShell in this folder and run: node .\dhan-proxy.js
3. Open http://127.0.0.1:8765/ in your browser. Do not open the HTML file
   directly; the page needs the local backend to read provider settings.
4. Restart node after editing .env. The backend reads it once at startup.

Credential handling
-------------------
The server reads .env beside dhan-proxy.js. /config returns configured flags
and masked broker client IDs; it never returns API keys or tokens. The optional
Google OAuth client ID is public and is sent to the page for sign-in. AI and
supported broker requests are sent from Node with environment credentials.
Dhan access tokens commonly expire daily; update that value in .env and restart
the backend when it expires.

DEFAULT_BROKER may be set to dhan, zerodha, groww, upstox, or angel. When it is
blank, use Connect Broker and choose a provider already configured in .env.
Only provider credentials are loaded automatically. The app still requires
the user to confirm actual trading orders.

FnO constituent-pattern overlay
--------------------------------
For NIFTY, BANKNIFTY, and SENSEX options, the scanner analyzes daily OHLCV
patterns in up to ten high-weight constituents. It scores trend, breakouts,
springs/upthrusts, and volume confirmation, then blends the weighted result
into the index directional bias and setup quality. Daily series are cached
for five minutes; an incomplete current-day candle is ignored during market
hours. The first scan can take longer because it fetches component history.

Weights are dated snapshots rather than a live weight feed. NIFTY and SENSEX
use October 30, 2026 snapshots; BANKNIFTY uses the April 30, 2026 snapshot
available in the official factsheet. The SENSEX list is explicitly a BSE
SENSEX 50 top-weight proxy. The UI shows source/date and fades older weights.
Refresh the mapping in reimagined-winner.html when the official factsheets
change. The constituent overlay is a heuristic and has not been separately
backtested. It adds context to ranking; it does not establish that the
recommendations are more accurate.

Provider scope
--------------
The current app adapters are Dhan, Upstox, Zerodha, Groww, and Angel One, plus
Claude, Gemini, and OpenAI. BROKER_1_* and BROKER_2_* in .env are flexible
credential slots; a new broker still needs an API adapter before the app can
connect or trade through it. Check each broker's current API access and token
rules before using it.
