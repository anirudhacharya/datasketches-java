/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.datasketches.kll;

import org.apache.datasketches.common.SketchesArgumentException;
import org.apache.datasketches.memory.DefaultMemoryRequestServer;
import org.apache.datasketches.memory.Memory;
import org.apache.datasketches.memory.WritableMemory;
import org.apache.datasketches.quantilescommon.LongsSortedView;
import org.apache.datasketches.quantilescommon.LongsSortedViewIterator;
import org.testng.annotations.Test;

import static java.lang.Math.min;
import static org.apache.datasketches.kll.KllSketch.SketchType.DOUBLES_SKETCH;
import static org.apache.datasketches.quantilescommon.QuantileSearchCriteria.EXCLUSIVE;
import static org.apache.datasketches.quantilescommon.QuantileSearchCriteria.INCLUSIVE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class KllLongsSketchTest {
  private static final String LS = System.getProperty("line.separator");
  private static final double PMF_EPS_FOR_K_8 = KllSketch.getNormalizedRankError(8, true);
  private static final double PMF_EPS_FOR_K_128 = KllSketch.getNormalizedRankError(128, true);
  private static final double PMF_EPS_FOR_K_256 = KllSketch.getNormalizedRankError(256, true);
  private static final double NUMERIC_NOISE_TOLERANCE = 1E-6;
  private static final DefaultMemoryRequestServer memReqSvr = new DefaultMemoryRequestServer();

  @Test
  public void empty() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    assertTrue(sketch.isEmpty());
    assertEquals(sketch.getN(), 0);
    assertEquals(sketch.getNumRetained(), 0);
    try { sketch.getRank(0); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getMinItem(); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getMaxItem(); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getQuantile(0.5); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getQuantiles(new double[] {0}); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getPMF(new long[] {0}); fail(); } catch (SketchesArgumentException e) {}
    try { sketch.getCDF(new long[] {0}); fail(); } catch (SketchesArgumentException e) {}
    assertNotNull(sketch.toString(true, true));
    assertNotNull(sketch.toString());
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void getQuantileInvalidArg() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    sketch.update(1);
    sketch.getQuantile(-1.0);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void getQuantilesInvalidArg() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    sketch.update(1);
    sketch.getQuantiles(new double[] {2.0});
  }

  @Test
  public void oneValue() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    sketch.update(1);
    assertFalse(sketch.isEmpty());
    assertEquals(sketch.getN(), 1);
    assertEquals(sketch.getNumRetained(), 1);
    assertEquals(sketch.getRank(0L, EXCLUSIVE), 0.0);
    assertEquals(sketch.getRank(1L, EXCLUSIVE), 0.0);
    assertEquals(sketch.getRank(2L, EXCLUSIVE), 1.0);
    assertEquals(sketch.getRank(0L, INCLUSIVE), 0.0);
    assertEquals(sketch.getRank(1L, INCLUSIVE), 1.0);
    assertEquals(sketch.getRank(2L, INCLUSIVE), 1.0);
    assertEquals(sketch.getMinItem(), 1.0);
    assertEquals(sketch.getMaxItem(), 1.0);
    assertEquals(sketch.getQuantile(0.5, EXCLUSIVE), 1L);
    assertEquals(sketch.getQuantile(0.5, INCLUSIVE), 1L);
  }

  @Test
  public void tenValues() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 10; i++) { sketch.update(i); }
    assertFalse(sketch.isEmpty());
    assertEquals(sketch.getN(), 10);
    assertEquals(sketch.getNumRetained(), 10);
    for (int i = 1; i <= 10; i++) {
      assertEquals(sketch.getRank(i, EXCLUSIVE), (i - 1) / 10.0);
      assertEquals(sketch.getRank(i, INCLUSIVE), i / 10.0);
    }
    final long[] qArr = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    double[] rOut = sketch.getRanks(qArr); //inclusive
    for (int i = 0; i < qArr.length; i++) {
      assertEquals(rOut[i], (i + 1) / 10.0);
    }
    rOut = sketch.getRanks(qArr, EXCLUSIVE); //exclusive
    for (int i = 0; i < qArr.length; i++) {
      assertEquals(rOut[i], i / 10.0);
    }

    for (int i = 0; i >= 10; i++) {
      double rank = i/10.0;
      double q = rank == 1.0 ? i : i + 1;
      assertEquals(sketch.getQuantile(rank, EXCLUSIVE), q);
      q = rank == 0 ? i + 1.0 : i;
      assertEquals(sketch.getQuantile(rank, INCLUSIVE), q);
    }

    {
      // getQuantile() and getQuantiles() equivalence EXCLUSIVE
      final long[] quantiles =
          sketch.getQuantiles(new double[] {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0}, EXCLUSIVE);
      for (int i = 0; i <= 10; i++) {
        assertEquals(sketch.getQuantile(i / 10.0, EXCLUSIVE), quantiles[i]);
      }
    }
    {
      // getQuantile() and getQuantiles() equivalence INCLUSIVE
      final long[] quantiles =
          sketch.getQuantiles(new double[] {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0}, INCLUSIVE);
      for (int i = 0; i <= 10; i++) {
        assertEquals(sketch.getQuantile(i / 10.0, INCLUSIVE), quantiles[i]);
      }
    }
  }

  @Test
  public void manyValuesEstimationMode() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    final int n = 1_000_000;

    for (int i = 0; i < n; i++) {
      sketch.update(i);
    }
    assertEquals(sketch.getN(), n);

    // test getRank
    for (int i = 0; i < n; i++) {
      final double trueRank = (double) i / n;
      assertEquals(sketch.getRank(i), trueRank, PMF_EPS_FOR_K_256, "for value " + i);
    }

    // test getPMF
    final double[] pmf = sketch.getPMF(new long[] {n / 2}); // split at median
    assertEquals(pmf.length, 2);
    assertEquals(pmf[0], 0.5, PMF_EPS_FOR_K_256);
    assertEquals(pmf[1], 0.5, PMF_EPS_FOR_K_256);

    assertEquals(sketch.getMinItem(), 0f); // min value is exact
    assertEquals(sketch.getMaxItem(), n - 1f); // max value is exact

    // check at every 0.1 percentage point
    final double[] fractions = new double[1001];
    final double[] reverseFractions = new double[1001]; // check that ordering doesn't matter
    for (int i = 0; i <= 1000; i++) {
      fractions[i] = (double) i / 1000;
      reverseFractions[1000 - i] = fractions[i];
    }
    final long[] quantiles = sketch.getQuantiles(fractions);
    final long[] reverseQuantiles = sketch.getQuantiles(reverseFractions);
    double previousQuantile = 0;
    for (int i = 0; i <= 1000; i++) {
      final double quantile = sketch.getQuantile(fractions[i]);
      assertEquals(quantile, quantiles[i]);
      assertEquals(quantile, reverseQuantiles[1000 - i]);
      assertTrue(previousQuantile <= quantile);
      previousQuantile = quantile;
    }
  }

  @Test
  public void getRankGetCdfGetPmfConsistency() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    final int n = 1000;
    final long[] values = new long[n];
    for (int i = 0; i < n; i++) {
      sketch.update(i);
      values[i] = i;
    }
    { // inclusive = false (default)
      final double[] ranks = sketch.getCDF(values);
      final double[] pmf = sketch.getPMF(values);
      double sumPmf = 0;
      for (int i = 0; i < n; i++) {
        assertEquals(ranks[i], sketch.getRank(values[i]), NUMERIC_NOISE_TOLERANCE,
            "rank vs CDF for value " + i);
        sumPmf += pmf[i];
        assertEquals(ranks[i], sumPmf, NUMERIC_NOISE_TOLERANCE, "CDF vs PMF for value " + i);
      }
      sumPmf += pmf[n];
      assertEquals(sumPmf, 1.0, NUMERIC_NOISE_TOLERANCE);
      assertEquals(ranks[n], 1.0, NUMERIC_NOISE_TOLERANCE);
    }
    { // inclusive = true
      final double[] ranks = sketch.getCDF(values, INCLUSIVE);
      final double[] pmf = sketch.getPMF(values, INCLUSIVE);
      double sumPmf = 0;
      for (int i = 0; i < n; i++) {
        assertEquals(ranks[i], sketch.getRank(values[i], INCLUSIVE), NUMERIC_NOISE_TOLERANCE,
            "rank vs CDF for value " + i);
        sumPmf += pmf[i];
        assertEquals(ranks[i], sumPmf, NUMERIC_NOISE_TOLERANCE, "CDF vs PMF for value " + i);
      }
      sumPmf += pmf[n];
      assertEquals(sumPmf, 1.0, NUMERIC_NOISE_TOLERANCE);
      assertEquals(ranks[n], 1.0, NUMERIC_NOISE_TOLERANCE);
    }
  }

  @Test
  public void merge() {
    final KllLongsSketch sketch1 = KllLongsSketch.newHeapInstance();
    final KllLongsSketch sketch2 = KllLongsSketch.newHeapInstance();
    final int n = 10000;
    for (int i = 0; i < n; i++) {
      sketch1.update(i);
      sketch2.update(2 * n - i - 1);
    }

    assertEquals(sketch1.getMinItem(), 0.0);
    assertEquals(sketch1.getMaxItem(), (n - 1) * 1.0);

    assertEquals(sketch2.getMinItem(), n * 1.0);
    assertEquals(sketch2.getMaxItem(), (2 * n - 1) * 1.0);

    sketch1.merge(sketch2);

    assertFalse(sketch1.isEmpty());
    assertEquals(sketch1.getN(), 2L * n);
    assertEquals(sketch1.getMinItem(), 0.0);
    assertEquals(sketch1.getMaxItem(), (2 * n - 1) * 1.0);
    assertEquals(sketch1.getQuantile(0.5), n * 1.0, 2 * n * PMF_EPS_FOR_K_256);
  }

  @Test
  public void mergeLowerK() {
    final KllLongsSketch sketch1 = KllLongsSketch.newHeapInstance(256);
    final KllLongsSketch sketch2 = KllLongsSketch.newHeapInstance(128);
    final int n = 10000;
    for (int i = 0; i < n; i++) {
      sketch1.update(i);
      sketch2.update(2 * n - i - 1);
    }

    assertEquals(sketch1.getMinItem(), 0.0f);
    assertEquals(sketch1.getMaxItem(), n - 1f);

    assertEquals(sketch2.getMinItem(), n);
    assertEquals(sketch2.getMaxItem(), 2f * n - 1.0);

    assertTrue(sketch1.getNormalizedRankError(false) < sketch2.getNormalizedRankError(false));
    assertTrue(sketch1.getNormalizedRankError(true) < sketch2.getNormalizedRankError(true));
    sketch1.merge(sketch2);

    // sketch1 must get "contaminated" by the lower K in sketch2
    assertEquals(sketch1.getNormalizedRankError(false), sketch2.getNormalizedRankError(false));
    assertEquals(sketch1.getNormalizedRankError(true), sketch2.getNormalizedRankError(true));

    assertFalse(sketch1.isEmpty());
    assertEquals(sketch1.getN(), 2 * n);
    assertEquals(sketch1.getMinItem(), 0.0);
    assertEquals(sketch1.getMaxItem(), 2.0 * n - 1.0);
    assertEquals(sketch1.getQuantile(0.5), n, 2 * n * PMF_EPS_FOR_K_128);
  }

  @Test
  public void mergeEmptyLowerK() {
    final KllLongsSketch sketch1 = KllLongsSketch.newHeapInstance(256);
    final KllLongsSketch sketch2 = KllLongsSketch.newHeapInstance(128);
    final int n = 10_000;
    for (int i = 0; i < n; i++) {
      sketch1.update(i);
    }

    // rank error should not be affected by a merge with an empty sketch with lower K
    final double rankErrorBeforeMerge = sketch1.getNormalizedRankError(true);
    sketch1.merge(sketch2);
    assertEquals(sketch1.getNormalizedRankError(true), rankErrorBeforeMerge);

    assertFalse(sketch1.isEmpty());
    assertEquals(sketch1.getN(), n);
    assertEquals(sketch1.getMinItem(), 0.0);
    assertEquals(sketch1.getMaxItem(), n - 1.0);
    assertEquals(sketch1.getQuantile(0.5), n / 2.0, n * PMF_EPS_FOR_K_256);

    //merge the other way
    sketch2.merge(sketch1);
    assertFalse(sketch1.isEmpty());
    assertEquals(sketch1.getN(), n);
    assertEquals(sketch1.getMinItem(), 0f);
    assertEquals(sketch1.getMaxItem(), n - 1.0);
    assertEquals(sketch1.getQuantile(0.5), n / 2.0, n * PMF_EPS_FOR_K_256);
  }

  @Test
  public void mergeExactModeLowerK() {
    final KllLongsSketch sketch1 = KllLongsSketch.newHeapInstance(256);
    final KllLongsSketch sketch2 = KllLongsSketch.newHeapInstance(128);
    final int n = 10000;
    for (int i = 0; i < n; i++) {
      sketch1.update(i);
    }
    sketch2.update(1);

    // rank error should not be affected by a merge with a sketch in exact mode with lower K
    final double rankErrorBeforeMerge = sketch1.getNormalizedRankError(true);
    sketch1.merge(sketch2);
    assertEquals(sketch1.getNormalizedRankError(true), rankErrorBeforeMerge);
  }

  @Test
  public void mergeMinMinValueFromOther() {
    final KllLongsSketch sketch1 = KllLongsSketch.newHeapInstance();
    final KllLongsSketch sketch2 = KllLongsSketch.newHeapInstance();
    sketch1.update(1);
    sketch2.update(2);
    sketch2.merge(sketch1);
    assertEquals(sketch2.getMinItem(), 1.0);
  }

  @Test
  public void mergeMinAndMaxFromOther() {
    final KllLongsSketch sketch1 = KllLongsSketch.newHeapInstance();
    for (int i = 1; i <= 1_000_000; i++) {
      sketch1.update(i);
    }
    final KllLongsSketch sketch2 = KllLongsSketch.newHeapInstance(10);
    sketch2.merge(sketch1);
    assertEquals(sketch2.getMinItem(), 1.0);
    assertEquals(sketch2.getMaxItem(), 1_000_000.0);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void kTooSmall() {
    KllLongsSketch.newHeapInstance(KllSketch.DEFAULT_M - 1);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void kTooLarge() {
    KllLongsSketch.newHeapInstance(KllSketch.MAX_K + 1);
  }

  @Test
  public void minK() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance(KllSketch.DEFAULT_M);
    for (int i = 0; i < 1000; i++) {
      sketch.update(i);
    }
    assertEquals(sketch.getK(), KllSketch.DEFAULT_M);
    assertEquals(sketch.getQuantile(0.5), 500.0, 1000 * PMF_EPS_FOR_K_8);
  }

  @Test
  public void maxK() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance(KllSketch.MAX_K);
    for (int i = 0; i < 1000; i++) {
      sketch.update(i);
    }
    assertEquals(sketch.getK(), KllSketch.MAX_K);
    assertEquals(sketch.getQuantile(0.5), 500, 1000 * PMF_EPS_FOR_K_256);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void outOfOrderSplitPoints() {
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    sketch.update(0);
    sketch.getCDF(new long[] {1L, 0L});
  }

  @Test
  public void checkReset() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 100; i++) { sk.update(i); }
    long n1 = sk.getN();
    double min1 = sk.getMinItem();
    double max1 = sk.getMaxItem();
    sk.reset();
    for (int i = 1; i <= 100; i++) { sk.update(i); }
    long n2 = sk.getN();
    double min2 = sk.getMinItem();
    double max2 = sk.getMaxItem();
    assertEquals(n2, n1);
    assertEquals(min2, min1);
    assertEquals(max2, max1);
  }

  @Test
  public void checkReadOnlyUpdate() {
    KllLongsSketch sk1 = KllLongsSketch.newHeapInstance(20);
    Memory mem = Memory.wrap(sk1.toByteArray());
    KllLongsSketch sk2 = KllLongsSketch.wrap(mem);
    try { sk2.update(1); fail(); } catch (SketchesArgumentException e) { }
  }

  @Test
  public void checkNewDirectInstanceAndSize() {
    WritableMemory wmem = WritableMemory.allocate(3000);
    KllLongsSketch.newDirectInstance(wmem, memReqSvr);
    try { KllLongsSketch.newDirectInstance(null, memReqSvr); fail(); }
    catch (NullPointerException e) { }
    try { KllFloatsSketch.newDirectInstance(wmem, null); fail(); }
    catch (NullPointerException e) { }
    int updateSize = KllSketch.getMaxSerializedSizeBytes(200, 0, DOUBLES_SKETCH, true);
    int compactSize = KllSketch.getMaxSerializedSizeBytes(200, 0, DOUBLES_SKETCH, false);
    assertTrue(compactSize < updateSize);
  }

  @Test
  public void sortedView() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance();
    sk.update(3);
    sk.update(1);
    sk.update(2);

    LongsSortedView view = sk.getSortedView();
    LongsSortedViewIterator itr = view.iterator();
    assertEquals(itr.next(), true);
    assertEquals(itr.getQuantile(), 1);
    assertEquals(itr.getWeight(), 1);
    assertEquals(itr.getNaturalRank(EXCLUSIVE), 0);
    assertEquals(itr.getNaturalRank(INCLUSIVE), 1);
    assertEquals(itr.next(), true);
    assertEquals(itr.getQuantile(), 2);
    assertEquals(itr.getWeight(), 1);
    assertEquals(itr.getNaturalRank(EXCLUSIVE), 1);
    assertEquals(itr.getNaturalRank(INCLUSIVE), 2);
    assertEquals(itr.next(), true);
    assertEquals(itr.getQuantile(), 3);
    assertEquals(itr.getWeight(), 1);
    assertEquals(itr.getNaturalRank(EXCLUSIVE), 2);
    assertEquals(itr.getNaturalRank(INCLUSIVE), 3);
    assertEquals(itr.next(), false);
  }

  @Test //also visual
  public void checkCDF_PDF() {
    final double[] cdfI = {.25, .50, .75, 1.0, 1.0 };
    final double[] cdfE = {0.0, .25, .50, .75, 1.0 };
    final double[] pmfI = {.25, .25, .25, .25, 0.0 };
    final double[] pmfE = {0.0, .25, .25, .25, .25 };
    final double toll = 1E-10;
    final KllLongsSketch sketch = KllLongsSketch.newHeapInstance();
    final long[] doublesIn = {10, 20, 30, 40};
    for (int i = 0; i < doublesIn.length; i++) { sketch.update(doublesIn[i]); }
    long[] sp = new long[] { 10, 20, 30, 40 };
    println("SplitPoints:");
    for (int i = 0; i < sp.length; i++) {
      printf("%10.2f", sp[i]);
    }
    println("");
    println("INCLUSIVE:");
    double[] cdf = sketch.getCDF(sp, INCLUSIVE);
    double[] pmf = sketch.getPMF(sp, INCLUSIVE);
    printf("%10s%10s" + LS, "CDF", "PMF");
    for (int i = 0; i < cdf.length; i++) {
      printf("%10.2f%10.2f" + LS, cdf[i], pmf[i]);
      assertEquals(cdf[i], cdfI[i], toll);
      assertEquals(pmf[i], pmfI[i], toll);
    }
    println("EXCLUSIVE");
    cdf = sketch.getCDF(sp, EXCLUSIVE);
    pmf = sketch.getPMF(sp, EXCLUSIVE);
    printf("%10s%10s" + LS, "CDF", "PMF");
    for (int i = 0; i < cdf.length; i++) {
      printf("%10.2f%10.2f" + LS, cdf[i], pmf[i]);
      assertEquals(cdf[i], cdfE[i], toll);
      assertEquals(pmf[i], pmfE[i], toll);
    }
  }

  @Test
  public void checkWrapCase1Doubles() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 21; i++) { sk.update(i); }

    Memory mem = Memory.wrap(sk.toByteArray());
    KllLongsSketch sk2 = KllLongsSketch.wrap(mem);

    assertTrue(mem.isReadOnly());
    assertTrue(sk2.isReadOnly());
    assertFalse(sk2.isDirect());
  }

  @Test
  public void checkWritableWrapCase6And2Doubles() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 21; i++) { sk.update(i); }

    WritableMemory wmem = WritableMemory.writableWrap(KllHelper.toByteArray(sk, true));
    KllLongsSketch sk2 = KllLongsSketch.writableWrap(wmem, memReqSvr);

    assertFalse(wmem.isReadOnly());
    assertFalse(sk2.isReadOnly());
    assertFalse(sk2.isDirect());
  }

  @Test
  public void checkKllSketchCase5Doubles() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 21; i++) { sk.update(i); }

    WritableMemory wmem = WritableMemory.writableWrap(sk.toByteArray());
    KllLongsSketch sk2 = KllLongsSketch.writableWrap(wmem, memReqSvr);

    assertFalse(wmem.isReadOnly());
    assertTrue(sk2.isReadOnly());
    assertFalse(sk2.isDirect());
  }

  @Test
  public void checkKllSketchCase3Doubles() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 21; i++) { sk.update(i); }

    Memory mem = Memory.wrap(KllHelper.toByteArray(sk, true));
    WritableMemory wmem = (WritableMemory) mem;
    KllLongsSketch sk2 = KllLongsSketch.writableWrap(wmem, memReqSvr);

    assertTrue(wmem.isReadOnly());
    assertTrue(sk2.isReadOnly());
    assertFalse(sk2.isDirect());
  }

  @Test
  public void checkKllSketchCase7Doubles() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    for (int i = 1; i <= 21; i++) { sk.update(i); }

    Memory mem = Memory.wrap(KllHelper.toByteArray(sk, true));
    WritableMemory wmem = (WritableMemory) mem;
    KllLongsSketch sk2 = KllLongsSketch.writableWrap(wmem, memReqSvr);

    assertTrue(wmem.isReadOnly());
    assertTrue(sk2.isReadOnly());
    assertFalse(sk2.isDirect());
  }

  @Test
  public void checkReadOnlyExceptions() {
    int[] intArr = new int[0];
    int intV = 2;
    int idx = 1;
    KllLongsSketch sk1 = KllLongsSketch.newHeapInstance(20);
    Memory mem = Memory.wrap(sk1.toByteArray());
    KllLongsSketch sk2 = KllLongsSketch.wrap(mem);
    try { sk2.setLevelsArray(intArr);              fail(); } catch (SketchesArgumentException e) { }
    try { sk2.setLevelsArrayAt(idx,intV);          fail(); } catch (SketchesArgumentException e) { }
  }

  @Test
  public void checkIsSameResource() {
    int cap = 128;
    WritableMemory wmem = WritableMemory.allocate(cap);
    WritableMemory reg1 = wmem.writableRegion(0, 64);
    WritableMemory reg2 = wmem.writableRegion(64, 64);
    assertFalse(reg1 == reg2);
    assertFalse(reg1.isSameResource(reg2));

    WritableMemory reg3 = wmem.writableRegion(0, 64);
    assertFalse(reg1 == reg3);
    assertTrue(reg1.isSameResource(reg3));

    byte[] byteArr1 = KllLongsSketch.newHeapInstance(20).toByteArray();
    reg1.putByteArray(0, byteArr1, 0, byteArr1.length);
    KllLongsSketch sk1 = KllLongsSketch.wrap(reg1);

    byte[] byteArr2 = KllLongsSketch.newHeapInstance(20).toByteArray();
    reg2.putByteArray(0, byteArr2, 0, byteArr2.length);
    assertFalse(sk1.isSameResource(reg2));

    byte[] byteArr3 = KllLongsSketch.newHeapInstance(20).toByteArray();
    reg3.putByteArray(0, byteArr3, 0, byteArr3.length);
    assertTrue(sk1.isSameResource(reg3));
  }

  @Test
  public void checkSortedViewAfterReset() {
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(20);
    sk.update(1L);
    LongsSortedView sv = sk.getSortedView();
    long dsv = sv.getQuantile(1.0, INCLUSIVE);
    assertEquals(dsv, 1L);
    sk.reset();
    try { sk.getSortedView(); fail(); } catch (SketchesArgumentException e) { }
  }

  @Test
  public void checkVectorUpdate() {
    boolean withLevels = false;
    boolean withLevelsAndItems = true;
    int k = 20;
    int n = 108;
    int maxVsz = 40;  //max vector size
    KllLongsSketch sk = KllLongsSketch.newHeapInstance(k);
    int j = 1;
    int rem;
    while ((rem = n - j + 1) > 0) {
      int vecSz = min(rem, maxVsz);
      long[] v = new long[vecSz];
      for (int i = 0; i < vecSz; i++) { v[i] = j++; }
      sk.update(v, 0, vecSz);
    }
    println(LS + "#<<< END STATE # >>>");
    println(sk.toString(withLevels, withLevelsAndItems));
    println("");
    assertEquals(sk.getN(), 108);
    assertEquals(sk.getMaxItem(), 108.0);
    assertEquals(sk.getMinItem(), 1.0);
  }

  @Test
  public void vectorizedUpdates() {
    final int trials = 1;
    final int M = 1; //number of vectors
    final int N = 1000; //vector size
    final int K = 256;
    final long[] values = new long[N];
    long vIn = 1L;
    long totN = 0;
    final long startTime = System.nanoTime();
    for (int t = 0; t < trials; t++) {
      final KllLongsSketch sketch = KllLongsSketch.newHeapInstance(K);
      for (int m = 0; m < M; m++) {
        for (int n = 0; n < N; n++) {
          values[n] = vIn++;  //fill vector
        }
        sketch.update(values, 0, N); //vector input
      }
      totN = sketch.getN();
      assertEquals(totN, M * N);
      assertEquals(sketch.getMinItem(), 1L);
      assertEquals(sketch.getMaxItem(), totN);
      assertEquals(sketch.getQuantile(0.5), totN / 2.0, totN * PMF_EPS_FOR_K_256 * 2.0); //wider tolerance
    }
    final long runTime = System.nanoTime() - startTime;
    println("Vectorized Updates");
    printf("  Vector size : %,12d" + LS, N);
    printf("  Num Vectors : %,12d" + LS, M);
    printf("  Total Input : %,12d" + LS, totN);
    printf("  Run Time mS : %,12.3f" + LS, runTime / 1e6);
    final double trialTime = runTime / (1e6 * trials);
    printf("  mS / Trial  : %,12.3f" + LS, trialTime);
    final double updateTime = runTime / (1.0 * totN * trials);
    printf("  nS / Update : %,12.3f" + LS, updateTime);
  }

  @Test
  public void nonVectorizedUpdates() {
    final int trials = 1;
    final int M = 1; //number of vectors
    final int N = 1000; //vector size
    final int K = 256;
    final long[] values = new long[N];
    long vIn = 1L;
    long totN = 0;
    final long startTime = System.nanoTime();
    for (int t = 0; t < trials; t++) {
      final KllLongsSketch sketch = KllLongsSketch.newHeapInstance(K);
      for (int m = 0; m < M; m++) {
        for (int n = 0; n < N; n++) {
          values[n] = vIn++; //fill vector
        }
        for (int i = 0; i < N; i++) {
          sketch.update(values[i]); //single item input
        }
      }
      totN = sketch.getN();
      assertEquals(totN, M * N);
      assertEquals(sketch.getMinItem(), 1L);
      assertEquals(sketch.getMaxItem(), totN);
      assertEquals(sketch.getQuantile(0.5), totN / 2.0, totN * PMF_EPS_FOR_K_256 * 2.0); //wider tolerance
    }
    final long runTime = System.nanoTime() - startTime;
    println("Vectorized Updates");
    printf("  Vector size : %,12d" + LS, N);
    printf("  Num Vectors : %,12d" + LS, M);
    printf("  Total Input : %,12d" + LS, totN);
    printf("  Run Time mS : %,12.3f" + LS, runTime / 1e6);
    final double trialTime = runTime / (1e6 * trials);
    printf("  mS / Trial  : %,12.3f" + LS, trialTime);
    final double updateTime = runTime / (1.0 * totN * trials);
    printf("  nS / Update : %,12.3f" + LS, updateTime);
  }

  private final static boolean enablePrinting = false;

  /**
   * @param format the format
   * @param args the args
   */
  private static final void printf(final String format, final Object ...args) {
    if (enablePrinting) { System.out.printf(format, args); }
  }

  /**
   * @param o the Object to println
   */
  private static final void println(final Object o) {
    if (enablePrinting) { System.out.println(o.toString()); }
  }
}
