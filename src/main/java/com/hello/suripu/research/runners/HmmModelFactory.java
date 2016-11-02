package com.hello.suripu.research.runners;

import com.google.common.base.Optional;
import com.hello.suripu.algorithm.hmm.BetaPdf;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelFactory;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;
import com.hello.suripu.algorithm.hmm.HmmPdfInterface;
import com.hello.suripu.algorithm.hmm.PdfCompositeBuilder;

/**
 * Created by benjo on 11/2/16.
 */
public class HmmModelFactory {
    public static HmmModel getExisting() {

           /*
     *  This takes the output of a neural network that outputs p(sleep), and a motion signal (on duration seconds) from the pill
     *  and will output when it thinks you got in bed, fell asleep, woke up, and got out of bed.
     *
     *
     *
     *  There are two steps:
     *  Step 1) Segment sleep with a hidden Markov model.
     *
     *                /--->{med(2)}----\
     *               |                 v
     *{med(1)} <--> {low(0)}        {high(3)} <-----> {med(4)}
     *                ^                /
     *                \-----{med(5)}---
     *
     *  The general idea is when you go from low to high, you will have a transition period on state 2
     *  And when you go from high to low, you will have a transition period in state 5
     *
     *  When you're in state 2 you search for a sleep event
     *  When you're in state 5 you search for a wake event
     *
     *  sleep event is max d/dt[p(sleep)] weighted by how close you are to p(sleep) = 0.5
     *  so maybe weighed by p * (1 - p)
     *
     *  wake event is min d/dt[p(sleep)] * pill_svm_magnitude
     *
     *  Initial state is always state 0 or 1, and final state is always 0 or 1
     *

     */
        
        final  double MIN_HMM_PDF_EVAL = 1e-320;

        final HmmPdfInterface[] obsModelsMain = {new BetaPdf(1.0,10.0,0),new BetaPdf(2.0,2.0,0),new BetaPdf(10.0,1.0,0)};

        final HmmPdfInterface s0 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[0]).build();
        final HmmPdfInterface s1 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).build();
        final HmmPdfInterface s2 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).build();
        final HmmPdfInterface s3 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[2]).build();
        final HmmPdfInterface s4 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).build();
        final HmmPdfInterface s5 = PdfCompositeBuilder.newBuilder().withPdf(obsModelsMain[1]).build();


        final HmmPdfInterface[] obsModels = {s0, s1, s2, s3, s4, s5};

        final double[][] A = new double[obsModels.length][obsModels.length];
        A[0][0] = 0.98; A[0][1] = 0.01; A[0][2] = 0.01;
        A[1][0] = 0.02; A[1][1] = 0.98;
        A[2][2] = 0.98; A[2][3] = 0.02;
        A[3][3] = 0.98; A[3][4] = 0.01; A[3][5] = 0.01;
        A[4][3] = 0.05; A[4][4] = 0.95;
        A[5][0] = 0.02;                                                                 A[5][5] = 0.98;



        final double[] pi = new double[obsModels.length];
        pi[0] = 0.9;
        pi[1] = 0.1;

        //segment this shit
        final HiddenMarkovModelInterface hmm = HiddenMarkovModelFactory.create(HiddenMarkovModelFactory.HmmType.LOGMATH, obsModels.length, A, pi, obsModels, 0);


        return new HmmModel(hmm,new Integer[]{0,1},MIN_HMM_PDF_EVAL);

    }


    public static HmmModel getFoobars() {

        final double [][] A = {
                {0.9,0.1,0.0},
                {0.1,0.9,0.0},
                {0.0,0.1,0.9}
        };

        //initial state probababilities
        final double pi [] = {1.0,0.0,0.0};

        final double highAlpha = 2.0;
        final double highBeta = 10.0;

        final double medAlpha = 2.0;
        final double medBeta = 2.0;

        final double lowAlpha = 10.0;
        final double lowBeta = 2.0;

        final HmmPdfInterface[] obsModel = {
                PdfCompositeBuilder.newBuilder()
                        .withPdf(new BetaPdf(highAlpha, highBeta, 0))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 1))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 2))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 3))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 4))
                        .build(),
                PdfCompositeBuilder.newBuilder()
                        .withPdf(new BetaPdf(medAlpha, medBeta, 0))
                        .withPdf(new BetaPdf(medAlpha, medBeta, 1))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 2))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 3))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 4))
                        .build(),
                PdfCompositeBuilder.newBuilder()
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 0))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 1))
                        .withPdf(new BetaPdf(highAlpha,highBeta, 2))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 3))
                        .withPdf(new BetaPdf(lowAlpha, lowBeta, 4))
                        .build()

        };





        final HiddenMarkovModelInterface hmm = HiddenMarkovModelFactory.create(
                HiddenMarkovModelFactory.HmmType.LOGMATH,
                A.length,
                A,
                pi,
                obsModel,
                0);

        //only allow the model to end in state 0 or state 2
        return new HmmModel(hmm,new Integer[]{0,2},1e-320);
    }

    static Optional<HmmModel> getModelById(final String id) {
        switch (id) {
            case "foobars":
            {
                return Optional.of(getFoobars());
            }

            case "existing":
            {
                return Optional.of(getExisting());
            }
            default:
                return Optional.absent();
        }
    }

}
