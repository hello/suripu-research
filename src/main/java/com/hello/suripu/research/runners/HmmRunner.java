package com.hello.suripu.research.runners;

import com.google.common.base.Optional;
import com.hello.suripu.algorithm.hmm.HmmDecodedResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by benjo on 11/2/16.
 */
public class HmmRunner {

    //untested
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


    public static void main(String [] args ) throws IOException {

        //data should be of the format [numSensors x numTimeSteps]
        final double [][] data  = csvToDoubles(new String(Files.readAllBytes(Paths.get(args[0]))));

        Optional<HmmModel> hmmOptional = HmmModelFactory.getModelById(args[1]);

        if (!hmmOptional.isPresent()) {
            //todo error message
            return;
        }

        HmmModel model  = hmmOptional.get();

        final HmmDecodedResult result = model.hmm.decode(data,model.possibleEndStates,model.minLikelihood);


        //this is the sequence of states given the model and the data result.bestPath;


    }

}
