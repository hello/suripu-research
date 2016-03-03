package com.hello.suripu.research.algtest;

import com.google.common.base.Optional;
import com.hello.suripu.algorithm.hmm.BetaPdf;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelFactory;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;
import com.hello.suripu.algorithm.hmm.HmmDecodedResult;
import com.hello.suripu.algorithm.hmm.HmmPdfInterface;
import com.hello.suripu.algorithm.hmm.PoissonPdf;
import com.hello.suripu.core.algorithmintegration.OneDaysSensorData;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.models.TrackerMotion;
import org.joda.time.DateTimeConstants;

import java.util.List;

/**
 * Created by benjo on 3/3/16.
 */
public class HardCodedNeuralNet {

    final static String NET_BUCKET = "hello-data/neuralnet";
    final static String NET_BASE_KEY = "2016-02-24T16:00:57.283Z";
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

    private static double [][] getSensorData(final OneDaysSensorData oneDaysSensorData) {

        final List<Sample> light = oneDaysSensorData.allSensorSampleList.get(Sensor.LIGHT);
        final List<Sample> soundcount = oneDaysSensorData.allSensorSampleList.get(Sensor.SOUND_NUM_DISTURBANCES);
        final List<Sample> soundvol = oneDaysSensorData.allSensorSampleList.get(Sensor.SOUND_PEAK_DISTURBANCE);
        final List<Sample> waves = oneDaysSensorData.allSensorSampleList.get(Sensor.WAVE_COUNT);

        final long t0 = light.get(0).dateTime;

        final long tf = light.get(light.size()-1).dateTime;

        final int T = (int) ((tf - t0) / (long) DateTimeConstants.MILLIS_PER_MINUTE) + 1;
        final int N = 7;

        final double [][] x = new double[N][T];

        for (final Sample s : light) {
            double value = Math.log(s.value + 1.0) / Math.log(2);

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
            x[4][getIndex(t0,s.dateTime)] = 0.1 * s.value - 4.0;
        }

        for (final TrackerMotion m : oneDaysSensorData.originalTrackerMotions) {
            x[5][getIndex(t0,m.timestamp)] = m.onDurationInSeconds;
        }

        for (final TrackerMotion m : oneDaysSensorData.originalPartnerTrackerMotions) {
            x[6][getIndex(t0,m.timestamp)] = m.onDurationInSeconds;
        }


        return x;
    }

    public void evaluate(final OneDaysSensorData oneDaysSensorData) {
        final NeuralNetEvaluator evaluator = NeuralNetEvaluator.createFromNet(data.net);

        final double [][] x = getSensorData(oneDaysSensorData);

        final double [][] output = evaluator.evaluate(x);
        final double [][] sleep = {output[1]};


        //segment this shit
        final double[][] A = {{0.99, 0.01}, {0.01, 0.99}};
        final double[] pi = {0.999, 0.001};
        final HmmPdfInterface[] obsModels = {new BetaPdf(9.0,1.0,0),new BetaPdf(1.0,9.0,0)};

        final HiddenMarkovModelInterface hmm = HiddenMarkovModelFactory.create(HiddenMarkovModelFactory.HmmType.LOGMATH, 2, A, pi, obsModels, 0);

        final HmmDecodedResult res = hmm.decode(sleep,new Integer[]{1},1e-320);

        int foo = 3;
        foo++;


    }

}
