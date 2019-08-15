package com.vachel.chartview.util;

import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.vachel.chartview.data.entity.SuitEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jianglixuan on 2019/7/30
 *
 * 各种指标算法
 *
 */
public class Arithmetic {
    /**
     * 布林带BOLL（n， k） 一般n默认取20，k取2, mb为计算好的中轨线
     * 中轨线MB: n日移动平均线 MA(n)
     * 上轨线：MB + 2*MD
     * 下轨线：MB - 2*MD
     * MD：n日方差
     *
     * @param entries
     * @param n
     * @param k
     * @return
     */
    public static List<Entry>[] getMB(List<SuitEntry> entries, int n, int k) {
        ArrayList<Entry> resultMB = new ArrayList<>();
        ArrayList<Entry> resultUp = new ArrayList<>();
        ArrayList<Entry> resultDn = new ArrayList<>();
        for (int i = 0, len = entries.size(); i < len; i++) {
            if (i < n - 1) {
                continue;
            }
            float sumMB = 0;
            float sumMD = 0;
            for (int j = n - 1; j >= 0; j--) {
                float thisClose = entries.get(i - j).getValue();
                sumMB += thisClose;
            }
            float mb = sumMB / n;
            float x = entries.get(i).getX();
            resultMB.add(new Entry(x, mb));
            for (int j = n - 1; j >= 0; j--) {
                float thisClose = entries.get(i - j).getValue();
                float cma = thisClose - mb; // C-MB
                sumMD += cma * cma;
            }

            float md = (float) Math.pow(sumMD / (n - 1), 1.0 / k); //MD=前n日C-MB的平方和来开根
            resultUp.add(new Entry(x, mb + 2 * md)); // UP=MB+2*MD
            resultDn.add(new Entry(x, mb - 2 * md)); // DN=MB+2*MD
        }
        return new ArrayList[]{resultMB, resultUp, resultDn};
    }

    // n日均线MA, 一般计算5，10，20，30
    public static List<Entry> getMA(List<SuitEntry> entries, int n) {
        List<Entry> result = new ArrayList<>();
        for (int i = 0, len = entries.size(); i < len; i++) {
            if (i < n - 1) {
                continue;
            }
            float sum = 0;
            for (int j = 0; j < n; j++) {
                sum += entries.get(i - j).getValue();
            }
            result.add(new Entry(entries.get(i).getX(), sum / n));
        }
        return result;
    }

    /**
     * EMA算法
     * EMA(N) = (2C + (N-1)EMA')/(N+1), EMA'为前一天的ema; 通常N取12和26
     *
     * @param entries
     * @param n
     * @return
     */
    public static List<Entry> getEMA(List<SuitEntry> entries, int n) {
        List<Entry> result = new ArrayList<>();
        float lastEma = entries.get(0).getValue();// 第一个EMA为当第一个数据的价格
        result.add(new Entry(0, lastEma));

        float[] emaFactor = getEMAFactor(n);
        for (int i = 1; i < entries.size(); i++) {
            float ema = emaFactor[0] * entries.get(i).getValue() + emaFactor[1] * lastEma;
            result.add(new Entry(entries.get(i).getX(), ema));
            lastEma = ema;
        }
        return result;
    }

    /**
     * MACD算法：
     * DIF：EMA(short) - EMA(long) 一般short取12，long取26
     * DEA: EMA(DIF, mid), mid一般取9
     * MACD:(DIF-DEA)*2
     *
     * @param entries
     * @param s
     * @return
     */
    public static List[] getMACD(List<SuitEntry> entries, int s, int l, int m) {
        ArrayList<Entry> listDIF = new ArrayList<>();
        ArrayList<Entry> listDEA = new ArrayList<>();
        ArrayList<BarEntry> listMACD = new ArrayList<>();

        float lastEmaS = entries.get(0).getValue();
        float lastEmaL = lastEmaS;
        float lastDIF = 0;
        listDIF.add(new Entry(0, 0));
        listDEA.add(new Entry(0, 0));
        listMACD.add(new BarEntry(0, 0));

        float[] factorShort = getEMAFactor(s);
        float[] factorLong = getEMAFactor(l);
        float[] factorMid = getEMAFactor(m);
        for (int i = 1; i < entries.size(); i++) {
            float x = entries.get(i).getX();
            // 短线EMA
            float valueS = factorShort[0] * entries.get(i).getValue() + factorShort[1] * lastEmaS;
            lastEmaS = valueS;
            // 长线EMA
            float valueL = factorLong[0] * entries.get(i).getValue() + factorLong[1] * lastEmaL;
            lastEmaL = valueL;
            // DIF：EMA(short) - EMA(long)
            float valueDIF = valueS - valueL;
            listDIF.add(new Entry(x, valueDIF));
            // EMA(DIF, mid)
            float valueDEA = factorMid[0] * valueDIF + factorMid[1] * lastDIF;
            listDEA.add(new Entry(x, valueDEA));
            lastDIF = valueDEA;
            // MACD:(DIF-DEA)*2
            listMACD.add(new BarEntry(x, (valueDIF - valueDEA) * 2));
        }
        return new ArrayList[]{listDIF, listDEA, listMACD};
    }

    /**
     * 获取EMA计算时的相关系数
     * @param n
     * @return
     */
    private static float[] getEMAFactor(int n) {
        return new float[]{2f / (n + 1), (n - 1) * 1.0f / (n + 1)};
    }

    /**
     * kdj 9,3,3
     * N:=9; P1:=3; P2:=3;
     * RSV:=(CLOSE-L(LOW,N))/(H(HIGH,N)-L(LOW,N))*100;
     * K:SMA(RSV,P1,1);
     * D:SMA(K,P2,1);
     * J:3*K-2*D;
     * @param entries 数据集合
     * @param n 指标周期 9
     * @param m 权重 1
     * @param P1 参数值为3
     * @param P2 参数值为3
     * @return
     */
    public static List[] getKDJ(List<CandleEntry> entries, int n, int P1, int P2, int m) {
        List<Entry> kValue = new ArrayList();
        List<Entry> dValue = new ArrayList();
        List<Entry> jValue = new ArrayList();

        List<Entry> maxs = getPeriodHighest(entries, n);
        List<Entry> mins = getPeriodLowest(entries, n);
        //确保和 传入的list size一致，
        int size = entries.size() - maxs.size();
        for (int i = 0; i < size; i++) {
            maxs.add(0, new Entry());
            mins.add(0, new Entry());
        }
        float rsv = 0;
        float lastK = 50;
        float lastD = 50;

        for (int i = n - 1; i < entries.size(); i++) {
            float x = entries.get(i).getX();
            if (i >= maxs.size())
                break;
            if (i >= mins.size())
                break;
            float div = maxs.get(i).getY() - mins.get(i).getY();
            if (div == 0) {
                //使用上一次的
            } else {
                rsv = ((entries.get(i).getClose() - mins.get(i).getY())
                        / (div)) * 100;
            }

            float k = countSMA(rsv, P1, m, lastK);
            float d = countSMA(k, P2, m, lastD);
            float j = 3 * k - 2 * d;
            lastK = k;
            lastD = d;
            kValue.add(new Entry(x, k));
            dValue.add(new Entry(x, d));
            jValue.add(new Entry(x, j));
        }

        return new List[]{kValue, dValue, jValue};
    }

    /**
     * SMA(C,N,M) = (M*C+(N-M)*Y')/N
     * C=今天收盘价－昨天收盘价    N＝就是周期比如 6或者12或者24， M＝权重，其实就是1
     *
     * @param c   今天收盘价－昨天收盘价
     * @param n   周期
     * @param m   1
     * @param sma 上一个周期的sma
     * @return
     */
    private static float countSMA(float c, float n, float m, float sma) {
        return (m * c + (n - m) * sma) / n;
    }

    /**
     * n周期内最低值
     * @param entries
     * @param n
     * @return
     */
    private static List<Entry> getPeriodLowest(List<CandleEntry> entries, int n) {
        List<Entry> result = new ArrayList<>();
        float minValue = 0;
        for (int i = n - 1; i < entries.size(); i++) {
            float x = entries.get(i).getX();
            for (int j = i - n + 1; j <= i; j++) {
                if (j == i - n + 1) {
                    minValue = entries.get(j).getLow();
                } else {
                    minValue = Math.min(minValue, entries.get(j).getLow());
                }
            }
            result.add(new Entry(x, minValue));
        }
        return result;
    }

    /**
     *  N周期内最高值
     * @param entries
     * @param n
     * @return
     */
    private static List<Entry> getPeriodHighest(List<CandleEntry> entries, int n) {
        List<Entry> result = new ArrayList<>();
        float maxValue = entries.get(0).getHigh();
        for (int i = n - 1; i < entries.size(); i++) {
            float x = entries.get(i).getX();
            for (int j = i - n + 1; j <= i; j++) {
                if (j == i - n + 1) {
                    maxValue = entries.get(j).getHigh();
                } else {
                    maxValue = Math.max(maxValue, entries.get(j).getHigh());
                }
            }
            result.add(new Entry(x, maxValue));
        }
        return result;
    }

    /**
     * RSI（n)
     * RSI(N):= SMA(MAX(Close-LastClose,0),N,1)/SMA(ABS(Close-LastClose),N,1)*100
     *
     * @param entries
     * @param n
     * @param m 加权 1
     * @return
     */
    public static List<Entry> getRSI(List<CandleEntry> entries, int n, int m) {
        List<Entry> result = new ArrayList();
        float preIn = 0;
        float preAll = 0;
        for (int i = 1; i < entries.size(); i++) {
            float diff = entries.get(i).getClose() - entries.get(i - 1).getClose();
            preIn = countSMA(Math.max(diff, 0), n, m, preIn);
            preAll = countSMA(Math.abs(diff), n, m, preAll);
            if (i >= n) {
                float x = entries.get(i).getX();
                result.add(new Entry(x, preIn / preAll * 100));
            }
        }
        return result;
    }
}
