package com.hello.suripu.research.runners;

import com.hello.suripu.algorithm.hmm.BetaPdf;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelFactory;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;
import com.hello.suripu.algorithm.hmm.HmmDecodedResult;
import com.hello.suripu.algorithm.hmm.HmmPdfInterface;
import com.hello.suripu.algorithm.hmm.PdfCompositeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by benjo on 11/2/16.
 */
public class HmmRunner {

    static double [][] csvToDoubles(final String fileContents) {
        final String [] lines = fileContents.split("\n");
        int N = lines.length;

        if (lines[N-1].length() <= 1) {
            N--;
        }

        double [][] arr = new double[N][];

        for (int irow = 0; irow < N; irow++) {

            final String [] elements = lines[irow].split(",");
            final double [] row = new double[elements.length];

            for (int i = 0; i < elements.length; i++) {
                row[i] = Double.valueOf(elements[i].trim()).doubleValue();
            }

            arr[irow] = row;

        }

        return arr;
    }

    //state transition matrix
    public static final double [][] A = {
    {0.9,0.1,0.0},
    {0.1,0.9,0.0},
    {0.0,0.1,0.9}
    };

    //initial state probababilities
    final static double pi [] = {1.0,0.0,0.0};


    public static void main(String [] args ) throws IOException {

        //data should be of the format [numSensors x numTimeSteps]
        final double [][] data  = csvToDoubles(new String(Files.readAllBytes(Paths.get(args[0]))));

        final double highAlpha = 2.0;
        final double highBeta = 10.0;

        final double medAlpha = 2.0;
        final double medBeta = 2.0;

        final double lowAlpha = 10.0;
        final double lowBeta = 2.0;

        final HmmPdfInterface [] obsModel = {
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

        final Integer [] possibleEndStates = {2};

        final HmmDecodedResult result = hmm.decode(data,possibleEndStates,1e-320);


        //this is the sequence of states given the model and the data result.bestPath;


    }

}
