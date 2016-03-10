package com.hello.suripu.research.algtest;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.algorithm.hmm.BetaPdf;
import com.hello.suripu.algorithm.hmm.GaussianPdf;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelFactory;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;
import com.hello.suripu.algorithm.hmm.HmmDecodedResult;
import com.hello.suripu.algorithm.hmm.HmmPdfInterface;
import com.hello.suripu.algorithm.hmm.PdfComposite;
import com.hello.suripu.core.algorithmintegration.OneDaysSensorData;
import com.hello.suripu.core.algorithmintegration.TimelineAlgorithmResult;
import com.hello.suripu.core.models.Event;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.models.SleepSegment;
import com.hello.suripu.core.models.TrackerMotion;
import com.hello.suripu.core.translations.English;
/*
import com.xeiam.xchart.Chart;
import com.xeiam.xchart.QuickChart;
import com.xeiam.xchart.SeriesLineStyle;
import com.xeiam.xchart.SeriesMarker;
import com.xeiam.xchart.SwingWrapper;
import org.apache.commons.lang3.ArrayUtils;
*/
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Created by benjo on 3/3/16.
 */
public class HardCodedNeuralNet {

    final static String NET_BUCKET = "hello-data/neuralnet";
    final static String NET_BASE_KEY = "2016-02-24T16:00:57.283Z";
    private static final Logger LOGGER = LoggerFactory.getLogger(HardCodedNeuralNet.class);

    final private NeuralNetAndInfo data;

    static public Optional<HardCodedNeuralNet> create() {
        final Optional<NeuralNetAndInfo> netAndInfo = S3NeuralNet.getNet(NET_BUCKET, NET_BASE_KEY);

        if (!netAndInfo.isPresent()) {
            return Optional.absent();
        }

        return Optional.of(new HardCodedNeuralNet(netAndInfo.get()));

    }

    private HardCodedNeuralNet(final NeuralNetAndInfo data) {
        this.data = data;
    }

    private static int getIndex(final long t0, final long t) {
        return (int) ((t - t0) / (DateTimeConstants.MILLIS_PER_MINUTE));
    }

    // [0] ambient_light
// [1] diff light,
// [2] wave_count,
// [3] audio_num_disturbances,
// [4] audio_peak_disturbances_db,
// [5] my pill durations,
// [6] partner pill duration

    private static double [][] getSensorData(final OneDaysSensorData oneDaysSensorData) throws  Exception {

        final List<Sample> light = oneDaysSensorData.allSensorSampleList.get(Sensor.LIGHT);
        final List<Sample> soundcount = oneDaysSensorData.allSensorSampleList.get(Sensor.SOUND_NUM_DISTURBANCES);
        final List<Sample> soundvol = oneDaysSensorData.allSensorSampleList.get(Sensor.SOUND_PEAK_DISTURBANCE);
        final List<Sample> waves = oneDaysSensorData.allSensorSampleList.get(Sensor.WAVE_COUNT);

        if (light.isEmpty()) {
            throw new Exception("no data!");
        }
        final long t0 = light.get(0).dateTime;

        final long tf = light.get(light.size()-1).dateTime;

        final int T = (int) ((tf - t0) / (long) DateTimeConstants.MILLIS_PER_MINUTE) + 1;
        final int N = 7;

        final double [][] x = new double[N][T];

        for (final Sample s : light) {
            double value = Math.log(s.value + 1.0) / Math.log(2);

            final DateTime time = new DateTime(s.dateTime, DateTimeZone.UTC);
            if (time.getHourOfDay() >= 5 && time.getHourOfDay() < 20) {
                continue;
            }

            if (Double.isNaN(value) || value < 0.0) {
                value = 0.0;
            }

            x[0][getIndex(t0,s.dateTime)] = value;
        }

        //diff light
        for (int t = 1; t < x[0].length; t++) {
            x[1][t] = x[0][t] - x[0][t-1];
        }

        //waves
        for (final Sample s : waves) {
            x[2][getIndex(t0,s.dateTime)] = s.value;
        }

        //sound disturbance counts
        for (final Sample s : soundcount) {
            double value = Math.log(s.value + 1.0) / Math.log(2);

            if (Double.isNaN(value) || value < 0.0) {
                value = 0.0;
            }

            x[3][getIndex(t0,s.dateTime)] = value;
        }

        //sould volume
        for (final Sample s : soundvol) {
            double value = 0.1 * s.value - 4.0;

            if (value < 0.0) {
                value = 0.0;
            }
            x[4][getIndex(t0,s.dateTime)] = value;
        }

        for (final TrackerMotion m : oneDaysSensorData.originalTrackerMotions) {
            x[5][getIndex(t0,m.timestamp)] = m.onDurationInSeconds;
        }

        for (final TrackerMotion m : oneDaysSensorData.originalPartnerTrackerMotions) {
            x[6][getIndex(t0,m.timestamp)] = m.onDurationInSeconds;
        }


        return x;
    }

    protected static class PdfCompositeBuilder {
        final private PdfComposite pdf;

        public static PdfCompositeBuilder newBuilder() {
            return new PdfCompositeBuilder();
        }

        private PdfCompositeBuilder() {
            pdf = new PdfComposite();
        }

        private PdfCompositeBuilder(final PdfComposite pdf) {
            this.pdf = pdf;
        }

        public PdfCompositeBuilder withPdf(final HmmPdfInterface pdf) {
            this.pdf.addPdf(pdf);

            return new PdfCompositeBuilder(this.pdf);
        }

        public HmmPdfInterface build() {
            return this.pdf;
        }


    }

    public Optional<TimelineAlgorithmResult> evaluate(final OneDaysSensorData oneDaysSensorData) throws Exception {

        final List<Sample> light = oneDaysSensorData.allSensorSampleList.get(Sensor.LIGHT);

        if (light.isEmpty()) {
            return Optional.absent();
        }

        final long t0 = light.get(0).dateTime; //utc local


        final NeuralNetEvaluator evaluator = NeuralNetEvaluator.createFromNet(data.net);

        final double [][] x = getSensorData(oneDaysSensorData);

        final double [][] output = evaluator.evaluate(x);
        final double [] sleep = output[1].clone();
        final double [][] sleepMeas = {output[1]};

        final double [] dsleep = new double[sleep.length];

        for (int i = 1; i < dsleep.length; i++) {
            dsleep[i] = sleep[i] - sleep[i-1];
        }


        final HmmPdfInterface[] obsModelsMain = {new BetaPdf(1.0,1.0,0),new BetaPdf(50.0,5.0,0),new BetaPdf(1.0,1.0,0)};
        final HmmPdfInterface[] obsModelsDiff = {new GaussianPdf(-0.02,0.02,1),new GaussianPdf(0.00,0.004,1),new GaussianPdf(0.02,0.02,1)};

        /*
        //iterate through all possible combinations
        final HmmPdfInterface s0 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[0]).withPdf(obsModelsDiff[0]).build();
        final HmmPdfInterface s1 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[0]).withPdf(obsModelsDiff[1]).build();
        final HmmPdfInterface s2 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[0]).withPdf(obsModelsDiff[2]).build();
        final HmmPdfInterface s3 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).withPdf(obsModelsDiff[0]).build();
        final HmmPdfInterface s4 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).withPdf(obsModelsDiff[1]).build();
        final HmmPdfInterface s5 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).withPdf(obsModelsDiff[2]).build();
        final HmmPdfInterface s6 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[2]).withPdf(obsModelsDiff[0]).build();
        final HmmPdfInterface s7 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[2]).withPdf(obsModelsDiff[1]).build();
        final HmmPdfInterface s8 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[2]).withPdf(obsModelsDiff[2]).build();

        final HmmPdfInterface[] obsModel = {s0,s1,s2,s3,s4,s5,s6,s7,s8};

        final double[][] Aall = {
                {0.99, 0.01,0.00},
                {0.00, 0.9999,0.0001},
                {0.00,0.00,1.0}};
        */
        //segment this shit
        final double[][] A = {
                {0.99, 0.01,0.00},
                {0.00, 0.99,0.01},
                {0.00,0.00,1.0}};
        final double[] pi = {1.0, 0.000,0.000};

        final HiddenMarkovModelInterface hmm = HiddenMarkovModelFactory.create(HiddenMarkovModelFactory.HmmType.LOGMATH, 3, A, pi, obsModelsMain, 0);

        final HmmDecodedResult res = hmm.decode(sleepMeas,new Integer[]{2},1e-320);


        /*
        final double [][] A2 = {{0.99,0.01,0.00},{0.01,0.99,0.01},{0.00,0.01,0.99}};
        final double[] pi2 = {0.00, 1.000,0.000};
        final HiddenMarkovModelInterface hmm2 = HiddenMarkovModelFactory.create(HiddenMarkovModelFactory.HmmType.LOGMATH, 3, A2, pi2, obsModels2, 0);
        final HmmDecodedResult res2 = hmm2.decode(new double[][] {dsleep},new Integer[]{0,1,2},1e-320);


        double [] p2 = new double[res2.bestPath.size()];
        for (int i = 0; i < p2.length; i++) {
            p2[i] = (double)res2.bestPath.get(i);
        }

*/
        /*
        final double [][] arr = new double[8][0];

        for (int i = 0; i < 7; i++) {
            arr[i + 1] = x[i];
        }

        arr[0] = output[1].clone();

        for (int t = 0; t < arr[0].length; t++) {
            arr[0][t] *= 10.0;
        }


        // Create Chart
        final String[] series = new String[arr.length];
        for (int i = 0; i < series.length; i++) {
            series[i] = String.valueOf(i);
        }

        final double [] t = new double[x[0].length];

        for (int i = 0; i < t.length; i++) {
            t[i] = i;
        }


        final Chart chart = QuickChart.getChart("data", "index", "", series, t, arr);
        final SwingWrapper sw = new SwingWrapper(chart);
        sw.displayChart("foo");
*/

        /*
        final Chart chart2 = QuickChart.getChart("probs", "index", "", new String []{"p", "dp"}, t, new double [][]{sleep,dsleep});
        final SwingWrapper sw2 = new SwingWrapper(chart2);
        sw2.displayChart("probs");
*/
        /*
        final Chart chart2 = QuickChart.getChart("probs", "index", "", "pdp", dsleep, sleep);
        final SwingWrapper sw2 = new SwingWrapper(chart2);
        chart2.getSeriesMap().get("pdp").setMarker(SeriesMarker.TRIANGLE_UP);
        chart2.getSeriesMap().get("pdp").setLineStyle(SeriesLineStyle.NONE);
        sw2.displayChart("probs");
*/


        if (res.bestPath.size() <= 1) {
            LOGGER.error("path size <= 1");
            return Optional.absent();
        }

        final List<Event> events = Lists.newArrayList();

        Integer prevState = res.bestPath.get(0);
        for (int i = 1; i < res.bestPath.size(); i++) {
            final Integer state = res.bestPath.get(i);

            final long eventTime = i * DateTimeConstants.MILLIS_PER_MINUTE + t0;
            if (state.equals(1) && prevState.equals(0)) {
                LOGGER.info("SLEEP at idx={}, p={}",i,sleepMeas[0][i]);

                events.add(Event.createFromType(Event.Type.SLEEP,
                        eventTime,
                        eventTime+DateTimeConstants.MILLIS_PER_MINUTE,
                        light.get(i).offsetMillis,
                        Optional.of(English.FALL_ASLEEP_MESSAGE),
                        Optional.<SleepSegment.SoundInfo>absent(),
                        Optional.<Integer>absent()));

            }
            else if (state.equals(2) && prevState.equals(1)) {
                LOGGER.info("WAKE at idx={}, p={}",i,sleepMeas[0][i]);

                events.add(Event.createFromType(Event.Type.WAKE_UP,
                        eventTime,
                        eventTime+DateTimeConstants.MILLIS_PER_MINUTE,
                        light.get(i).offsetMillis,
                        Optional.of(English.WAKE_UP_MESSAGE),
                        Optional.<SleepSegment.SoundInfo>absent(),
                        Optional.<Integer>absent()));
            }

            prevState = state;

        }

        return Optional.of(new TimelineAlgorithmResult(events, Collections.<Event>emptyList()));

    }

}
