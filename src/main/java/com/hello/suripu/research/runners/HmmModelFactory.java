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

            default:
                return Optional.absent();
        }
    }

}
