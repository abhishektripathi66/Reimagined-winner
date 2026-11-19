WyckoffPro + Upstox option-chain setup

Files
- wyckoffpro-upstox.html: app with Upstox live option-chain support.
- dhan-proxy.js: local connector for Upstox market data, Dhan, and Yahoo Finance.

Setup
1. Open Upstox Developer Apps: https://account.upstox.com/developer/apps
2. Select the Analytics tab, generate a free Analytics Token, and copy the full token.
3. Keep both files in the same folder. Install Node.js if it is not already installed.
4. Open PowerShell in that folder and run:
   node .\dhan-proxy.js
   Leave that window running.
5. Open wyckoffpro-upstox.html in Chrome. Go to Options Intel → Upstox Data, paste the Analytics Token, then click Verify & connect. The token is saved before API verification, so a permission error does not erase it; a rejected request still means that particular API cannot provide data.

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
