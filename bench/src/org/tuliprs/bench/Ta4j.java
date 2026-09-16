package org.tuliprs.bench;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Shared ta4j plumbing: one BaseBarSeries per stock (built once, cached —
 * series construction is setup, not measured work) and the full-series pass
 * every ta4j closure times.
 */
public final class Ta4j {

    private static final Map<String, BarSeries> CACHE = new ConcurrentHashMap<>();
    private static final Instant START = Instant.parse("2000-01-03T00:00:00Z");
    private static final Duration DAY = Duration.ofDays(1);

    private Ta4j() {}

    /** Cached calendar-daily bar series for a stock (DoubleNum fast path). */
    public static BarSeries series(Stock s) {
        return CACHE.computeIfAbsent(s.symbol, k -> build(s));
    }

    private static BarSeries build(Stock s) {
        var series = new BaseBarSeriesBuilder()
                .withName(s.symbol)
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        for (int i = 0; i < s.bars(); i++) {
            Instant b = START.plusMillis(i * DAY.toMillis());
            series.addBar(new BaseBar(DAY, b, b.plus(DAY),
                    DoubleNum.valueOf(s.open[i]), DoubleNum.valueOf(s.high[i]),
                    DoubleNum.valueOf(s.low[i]), DoubleNum.valueOf(s.close[i]),
                    DoubleNum.valueOf(s.volume[i]),
                    DoubleNum.valueOf(s.close[i] * s.volume[i]), 0L));
        }
        return series;
    }

    /**
     * Timed body of every ta4j reference: compute the indicator over the
     * FULL series (one getValue per bar) and fold the values together so the
     * JIT cannot elide the computation.
     */
    public static double runFull(Indicator<Num> ind) {
        BarSeries s = ind.getBarSeries();
        double sum = 0;
        for (int i = s.getBeginIndex(); i <= s.getEndIndex(); i++) {
            Num v = ind.getValue(i);
            sum += v.doubleValue();
        }
        return sum;
    }

    /** runFull over a list of indicators (multi-row indicators). */
    public static double runFull(List<Indicator<Num>> inds) {
        double sum = 0;
        for (Indicator<Num> ind : inds) {
            sum += runFull(ind);
        }
        return sum;
    }
}
