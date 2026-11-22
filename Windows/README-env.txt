Reimagined Winner — Windows local backend and credentials
========================================================

Start the Windows app
---------------------
1. Copy .env.example to .env in this folder, then replace the values you use.
2. In PowerShell in this folder run: node .\dhan-proxy.js
3. Open http://127.0.0.1:8765/ in your browser. Do not open the HTML file
   directly; it needs the local backend for credentials and broker requests.
4. The backend reads .env once at startup. The HTML Algo Config page can also
   load, show, add, remove and save the local .env values. Secret fields are
   masked until revealed. A changed PORT requires restarting the backend.

Keep credentials private
------------------------
Do not commit or share .env. The backend binds to 127.0.0.1 and checks the
local Host/Origin before serving config and proxy routes. GET /config returns
provider status and masked IDs; the explicit GET /config/values editor route
returns values only to that same local UI so the user can read/edit the file.
Treat the local editor like a password manager: anyone who can use the local
machine and browser can read the settings there.

AI and broker keys
------------------
The template includes Gemini, Anthropic Claude, OpenAI, Google client ID,
Upstox, Dhan, Groww, Zerodha, Angel One, Fyers, Alice Blue, ICICI Direct,
Kotak Neo, and two custom credential slots. A credential slot is not itself a
working integration: each broker still requires a backend adapter and that
provider's API access. Currently wired adapters are Dhan, Upstox, Groww,
Zerodha and Angel One, plus Claude, Gemini and OpenAI.

Portfolio research and Turtle Soup
----------------------------------
Portfolio suggestions use actual daily OHLC candles, Wyckoff-style phase and
trend, volume, and a simplified close-confirmed Turtle Soup failed 20-session
breakout. Unsupported symbols or missing history show insufficient data.
New-stock candidates are selected from mapped liquid index constituents only.
Scores are rule-based research ranks, not calibrated growth probabilities or
financial advice.

The options scanner uses a separately labeled 20-bar intraday Turtle Soup
proxy. Opposing failed-breakout direction filters candidates; aligned patterns
add to the displayed rule score. Backtest replay applies the same filter to
historical index signals. It is not a substitute for validating execution,
spread, charges, and out-of-sample performance.

Index constituent overlay
-------------------------
For NIFTY, BANKNIFTY, and SENSEX options, the scanner analyzes OHLCV patterns
in mapped high-weight constituents, then combines weighted trend, structure,
and volume context with index signals. It adds fetch latency. Weight snapshots
are dated and displayed in the UI; constituent patterns are heuristic context,
not independently validated predictive evidence.
