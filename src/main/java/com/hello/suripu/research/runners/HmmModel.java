package com.hello.suripu.research.runners;

import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;

/**
 * Created by benjo on 11/2/16.
 */
public class HmmModel {

    public final HiddenMarkovModelInterface hmm;
    public final Integer [] possibleEndStates;
    public final double minLikelihood;

    public HmmModel(final HiddenMarkovModelInterface hmm, final Integer [] possibleEndStates, final double minLikelihood) {
        this.hmm = hmm;
        this.possibleEndStates = possibleEndStates;
        this.minLikelihood = minLikelihood;
    }
}
