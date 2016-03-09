package com.hello.suripu.research.algtest;

import com.google.common.base.Optional;
import com.hello.suripu.algorithm.hmm.BetaPdf;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelFactory;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;
import com.hello.suripu.algorithm.hmm.HmmDecodedResult;
import com.hello.suripu.algorithm.hmm.HmmPdfInterface;
import com.hello.suripu.core.algorithmintegration.OneDaysSensorData;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.models.TrackerMotion;
import com.xeiam.xchart.Chart;
import com.xeiam.xchart.QuickChart;
import com.xeiam.xchart.SwingWrapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public void evaluate(final OneDaysSensorData oneDaysSensorData) throws Exception {
        final NeuralNetEvaluator evaluator = NeuralNetEvaluator.createFromNet(data.net);

        final double [][] x = getSensorData(oneDaysSensorData);

        final double [][] output = evaluator.evaluate(x);
        final double [][] sleep = {output[1]};

        final double [][] arr = new double[8][0];

        for (int i = 0; i < 7; i++) {
            arr[i + 1] = x[i];
        }

        arr[0] = output[1];

        for (int t = 0; t < arr[0].length; t++) {
            arr[0][t] *= 10.0;
        }


        //segment this shit
        final double[][] A = {{0.99, 0.01}, {0.01, 0.99}};
        final double[] pi = {0.999, 0.001};
        final HmmPdfInterface[] obsModels = {new BetaPdf(9.0,1.0,0),new BetaPdf(1.0,9.0,0)};

        final HiddenMarkovModelInterface hmm = HiddenMarkovModelFactory.create(HiddenMarkovModelFactory.HmmType.LOGMATH, 2, A, pi, obsModels, 0);

        final HmmDecodedResult res = hmm.decode(sleep,new Integer[]{1},1e-320);


        // Create Chart
        final String[] series = new String[8];
        for (int i = 0; i < series.length; i++) {
            series[i] = String.valueOf(i);
        }

        final double [] t = new double[x[0].length];

        for (int i = 0; i < t.length; i++) {
            t[i] = i;
        }

        /*
        final Chart chart = QuickChart.getChart("data", "index", "", series, t, arr);
        final SwingWrapper sw = new SwingWrapper(chart);
        sw.displayChart("foo");
        */


        if (res.bestPath.size() <= 1) {
            LOGGER.error("path size <= 1");
            return;
        }

        Integer prevState = res.bestPath.get(0);
        for (int i = 1; i < res.bestPath.size(); i++) {
            final Integer state = res.bestPath.get(i);

            if (!state.equals(prevState)) {
                LOGGER.info("{} ---> {} at idx={}",prevState,state,i);
            }

            prevState = state;

        }

    }

}
