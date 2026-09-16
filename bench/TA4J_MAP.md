# ta4j 0.19 equivalence map

Authoritative for the Java bench suite. Derived from `jar tf ta4j-core-0.19.jar`
(class list) — NOT from the wiki, which lags releases. A `Ta4jFn` is registered
ONLY when ta4j computes the same thing parameterized by the same swept option
(`(int) opts[0]` etc.). Absence here means the bench file gets `ta4j(...)`
omitted entirely with a one-line comment.

All classes live under `org.ta4j.core.indicators.` unless noted. `series` =
`Ta4j.series(stock)`, price inputs = `ClosePriceIndicator`/`HighPriceIndicator`...

## Param-compatible (register Ta4jFn)

| tulip | ta4j construction |
|---|---|
| sma | `averages.SMAIndicator(series, (int)opts[0])` |
| ema | `averages.EMAIndicator(close, (int)opts[0])` |
| wma | `averages.WMAIndicator(close, (int)opts[0])` |
| dema | `averages.DoubleEMAIndicator(close, (int)opts[0])` |
| tema | `averages.TripleEMAIndicator(close, (int)opts[0])` |
| hma | `averages.HMAIndicator(close, (int)opts[0])` |
| zlema | `averages.ZLEMAIndicator(close, (int)opts[0])` |
| kama | `averages.KAMAIndicator(close, (int)opts[0], 2, 30, true)` (fast=2/slow=30 fixed — sweep opts[0]=er period; NOTE: tulip kama opts are {er_period, fast, slow}: use `(int)opts[0], (int)opts[1], (int)opts[2], true`) |
| vidya | `averages.VIDYAIndicator(close, (int)opts[0], ...)` — CHECK ctor args vs tulip's {long_period, alpha/...}; if not compatible, nil |
| vwma | `averages.VWMAIndicator(close→volume-aware ctor)`, period opts[0] |
| wilders | `averages.WildersMAIndicator` — nil unless ctor matches period-only sweep |
| trima | `averages.TMAIndicator(close, (int)opts[0])` |
| rsi | `RSIIndicator(close, (int)opts[0])` |
| macd | `MACDIndicator(close, (int)opts[0], (int)opts[1])` (signal row separately if needed) |
| atr | `ATRIndicator(series, (int)opts[0], ...)` — default moving type; acceptable twin |
| tr | `helpers.TRIndicator(series)` (tulip tr has 0 options) |
| dx | `adx.DXIndicator(series, (int)opts[0])` |
| adx | `adx.ADXIndicator(series, (int)opts[0])` |
| di | `adx.PlusDIIndicator(series, p)` + `adx.MinusDIIndicator(series, p)` summed |
| dm | `adx.PlusDMIndicator(series)` + `MinusDMIndicator` (0-option fixed window — nil if tulip sweeps options) |
| aroon | `aroon.AroonUpIndicator(series, (int)opts[0])` (+ Down) |
| aroonosc | `aroon.AroonOscillatorIndicator(series, (int)opts[0])` |
| psar | `ParabolicSarIndicator(series)` default .02/.2 — ONLY for the fixed sweep; register with a comment if tulip options {0.02, 0.2} match defaults |
| willr | `WilliamsRIndicator(series, (int)opts[0])` |
| stoch | `StochasticOscillatorKIndicator(series, (int)opts[0])` (+ `StochasticOscillatorDIndicator`) |
| stochrsi | `StochasticRSIIndicator(series, (int)opts[0])` |
| cci | `CCIIndicator(series, (int)opts[0])` |
| cmo | `CMOIndicator(close, (int)opts[0])` |
| roc | `ROCIndicator(close, (int)opts[0])` |
| mom | `NetMomentumIndicator` NOT equivalent — nil |
| ao | `AwesomeOscillatorIndicator(series)` (fixed 5/34 == tulip defaults; 0-option) |
| bbands | `bollinger.BollingerBandsLowerIndicator`/`Middle`/`Upper` over `SMAIndicator` + `statistics.SigmaIndicator` with `(int)opts[0]` and `DoubleNum.valueOf(opts[1])` |
| stddev | `statistics.StandardDeviationIndicator(close, (int)opts[0])` |
| md | `statistics.MeanDeviationIndicator(close, (int)opts[0])` |
| max | `helpers.HighestValueIndicator(high, (int)opts[0])` |
| min | `helpers.LowestValueIndicator(low, (int)opts[0])` |
| medprice | `helpers.MedianPriceIndicator(series, (int)opts[0])` — CHECK ctor (ta4j's is (series, timeFrame)) |
| typprice | `helpers.TypicalPriceIndicator(series)` (0-option) |
| obv | `volume.OnBalanceVolumeIndicator(series)` (0-option) |
| ad | `volume.AccumulationDistributionIndicator(series)` (0-option) |
| adosc | `volume.ChaikinOscillatorIndicator(series)` — fixed 3/10 windows: nil if swept |
| mfi | `volume.MoneyFlowIndexIndicator(series, (int)opts[0])` |
| nvi | `volume.NVIIndicator(series)` (0-option — register) |
| pvi | `volume.PVIIndicator(series)` (0-option — register) |
| vwap | `volume.VWAPIndicator(series, (int)opts[0], true)` |
| chaikinmf | `volume.ChaikinMoneyFlowIndicator(series, (int)opts[0])` |
| donchianchannel | `donchian.DonchianChannelUpper/Lower/MiddleIndicator(series, (int)opts[0])` |
| keltnerchannel | `keltner.KeltnerChannelUpper/Lower/MiddleIndicator` — ctor (series, multiplier?, ...) check; map if tulip opts {ema_period, atr_period, multiplier} align |
| ichimoku | `ichimoku.IchimokuTenkanSen/KijunSen/SenkouSpanA/SenkouSpanBIndicator(series, t, k, sb)` |
| supertrend | `supertrend.SuperTrendIndicator(series, (int)opts[0], multiplier)` |
| mass | `MassIndexIndicator(series)` fixed 25/9 — nil if swept |
| dpo | `DPOIndicator(series, (int)opts[0])` |
| linreg | `statistics.SimpleLinearRegressionIndicator(close, (int)opts[0], Type.CLOSURE)` |
| pivotpoint | `pivotpoints.PivotPointIndicator(series, Method.STANDARD)` |
| chandelierexit | `ChandelierExitLongIndicator/ShortIndicator(series, (int)opts[0])` |
| ppo | `PPOIndicator(close, (int)opts[0], (int)opts[1])` |
| vosc | volume oscillator — `volume` pkg has no VolumeOscillator → nil |
| fisher | `FisherIndicator(series)` fixed 9 window: only if tulip option sweep is 9 — else nil with comment |

## No ta4j counterpart (Ta4jFn omitted)

candlestick, wad, cvc/cvi, vhf, volatility, ultosc, qstick, tsf, rocr, marketfi,
emv, apo, ef, highpass, roofingfilter, hilberttransform, homodynediscriminator,
instantaneoustrendline, trendmode, msw, adaptivemsw, cybercycle, supersmoother,
mama, natr, trix (no TRIX in 0.19 list), stochrsi is above (has one),
chaikinOsc? see table.

If a class you expected is absent from `ta4j_classes.txt`, the entry is nil.
When in doubt, grep the class list first: it is the ground truth.
