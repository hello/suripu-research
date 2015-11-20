package com.hello.suripu.research.resources.v1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.algorithm.core.Segment;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModel;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModelInterface;
import com.hello.suripu.algorithm.hmm.HmmDecodedResult;
import com.hello.suripu.algorithm.sleep.SleepEvents;
import com.hello.suripu.algorithm.sleep.Vote;
import com.hello.suripu.api.datascience.SleepHmmProtos;
import com.hello.suripu.core.algorithmintegration.OneDaysSensorData;
import com.hello.suripu.core.algorithmintegration.OnlineHmm;
import com.hello.suripu.core.algorithmintegration.OnlineHmmSensorDataBinning;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.DefaultModelEnsembleDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAO;
import com.hello.suripu.core.db.FeatureExtractionModelsDAO;
import com.hello.suripu.core.db.FeedbackDAO;
import com.hello.suripu.core.db.OnlineHmmModelsDAO;
import com.hello.suripu.core.db.SleepHmmDAO;
import com.hello.suripu.core.db.TrackerMotionDAO;
import com.hello.suripu.core.db.UserLabelDAO;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.Event;
import com.hello.suripu.core.models.Events.FallingAsleepEvent;
import com.hello.suripu.core.models.Events.InBedEvent;
import com.hello.suripu.core.models.Events.OutOfBedEvent;
import com.hello.suripu.core.models.Events.WakeupEvent;
import com.hello.suripu.core.models.OnlineHmmData;
import com.hello.suripu.core.models.OnlineHmmModelParams;
import com.hello.suripu.core.models.OnlineHmmPriors;
import com.hello.suripu.core.models.OnlineHmmScratchPad;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.models.Timeline;
import com.hello.suripu.core.models.TimelineFeedback;
import com.hello.suripu.core.models.TimelineResult;
import com.hello.suripu.core.models.TrackerMotion;
import com.hello.suripu.core.oauth.AccessToken;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.oauth.Scope;
import com.hello.suripu.core.processors.TimelineProcessor;
import com.hello.suripu.core.resources.BaseResource;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.core.util.FeatureExtractionModelData;
import com.hello.suripu.core.util.FeedbackUtils;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.core.util.MultiLightOutUtils;
import com.hello.suripu.core.util.PartnerDataUtils;
import com.hello.suripu.core.util.SleepHmmWithInterpretation;
import com.hello.suripu.core.util.SoundUtils;
import com.hello.suripu.core.util.TimelineError;
import com.hello.suripu.core.util.TimelineUtils;
import com.hello.suripu.core.util.TrackerMotionUtils;
import com.hello.suripu.research.models.AlphabetsAndLabels;
import com.hello.suripu.research.models.EventsWithLabels;
import com.hello.suripu.research.models.FeedbackAsIndices;
import com.hello.suripu.research.models.GeneratedModel;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Created by benjo on 3/6/15.
 */

@Path("/v1/prediction")
public class PredictionResource extends BaseResource {

    private static final String ALGORITHM_SLEEP_SCORED = "sleep_score";
    private static final String ALGORITHM_VOTING = "voting";
    private static final String ALGORITHM_HIDDEN_MARKOV = "hmm";
    private static final String ALGORITHM_ONLINEHMM = "online";

    private static final Integer MISSING_DATA_DEFAULT_VALUE = 0;
    private static final Integer SLOT_DURATION_MINUTES = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(DataScienceResource.class);
    private final AccountDAO accountDAO;
    private final TrackerMotionDAO trackerMotionDAO;
    private final DeviceDataDAO deviceDataDAO;
    private final DeviceDAO deviceDAO;
    private final UserLabelDAO userLabelDAO;
    private final SleepHmmDAO sleepHmmDAO;
    private final FeedbackDAO feedbackDAO;
    private final TimelineProcessor timelineProcessor;
    private final TimelineUtils timelineUtils;
    private final SenseColorDAO senseColorDAO;
    private final FeatureExtractionModelsDAO featureExtractionModelsDAO;
    private final OnlineHmmModelsDAO priorsDAO;
    private final DefaultModelEnsembleDAO defaultModelEnsembleDAO;

    public PredictionResource(final AccountDAO accountDAO,
                              final TrackerMotionDAO trackerMotionDAO,
                              final DeviceDataDAO deviceDataDAO,
                              final DeviceDAO deviceDAO,
                              final UserLabelDAO userLabelDAO,
                              final SleepHmmDAO sleepHmmDAO,
                              final FeedbackDAO feedbackDAO,
                              final TimelineProcessor timelineProcessor,
                              final SenseColorDAO senseColorDAO,
                              final FeatureExtractionModelsDAO featureExtractionModelsDAO,
                              final OnlineHmmModelsDAO priorsDAO,
                              final DefaultModelEnsembleDAO defaultModelEnsembleDAO) {

        this.accountDAO = accountDAO;
        this.trackerMotionDAO = trackerMotionDAO;
        this.deviceDataDAO = deviceDataDAO;
        this.deviceDAO = deviceDAO;
        this.userLabelDAO = userLabelDAO;
        this.sleepHmmDAO = sleepHmmDAO;
        this.timelineProcessor = timelineProcessor;
        this.timelineUtils = new TimelineUtils();
        this.feedbackDAO = feedbackDAO;
        this.senseColorDAO = senseColorDAO;
        this.featureExtractionModelsDAO = featureExtractionModelsDAO;
        this.priorsDAO = priorsDAO;
        this.defaultModelEnsembleDAO = defaultModelEnsembleDAO;
    }


    private List<TrackerMotion> getPartnerTrackerMotion(final Long accountId, final DateTime startTime, final DateTime endTime) {
        final Optional<Long> optionalPartnerAccountId = this.deviceDAO.getPartnerAccountId(accountId);
        if (optionalPartnerAccountId.isPresent()) {
            final Long partnerAccountId = optionalPartnerAccountId.get();
            LOGGER.debug("partner account {}", partnerAccountId);
            return this.trackerMotionDAO.getBetweenLocalUTC(partnerAccountId, startTime, endTime);
        }
        return Collections.EMPTY_LIST;
    }


    private static ImmutableList<Event> getOnlineHmmEvents(final DateTime dateOfNight, final DateTime startTime, final DateTime endTime, final long accountId,
                                                    final OneDaysSensorData oneDaysSensorData, final DefaultModelEnsembleDAO defaultModelEnsembleDAO,final FeatureExtractionModelsDAO featureExtractionModelsDAO,
                                                           final OnlineHmmModelsDAO priorsDAO,final boolean forceLearning) {

        //get model from DB
        final FeatureExtractionModelData featureExtractor = featureExtractionModelsDAO.getLatestModelForDate(accountId, dateOfNight, Optional.<UUID>absent());

        if (featureExtractor.isValid()) {

            //get priors from DB

            final OnlineHmm onlineHmm = new OnlineHmm(defaultModelEnsembleDAO,featureExtractionModelsDAO,priorsDAO,Optional.<UUID>absent());

            //run the predictor--so the HMMs will decode, the output interpreted and segmented, and then turned into events
            final SleepEvents<Optional<Event>> events = onlineHmm.predictAndUpdateWithLabels(accountId, dateOfNight, startTime, endTime, endTime,oneDaysSensorData, false, forceLearning);

            final List<Event> predictions = Lists.newArrayList();
            for (final Optional<Event> event : events.toList()) {
                if (!event.isPresent()) {
                    continue;
                }

                predictions.add(event.get());
            }

            return ImmutableList.copyOf(predictions);
        }

        return ImmutableList.copyOf(Collections.EMPTY_LIST);

    }


    /*  Get sleep/wake events from the hidden markov model  */
    private ImmutableList<Event> getHmmEvents(final DateTime targetDate, final DateTime endDate,final long  currentTimeMillis,final long accountId,
                                     final AllSensorSampleList allSensorSampleList, final List<TrackerMotion> myMotion,final SleepHmmDAO hmmDAO) {


        LOGGER.info("Using HMM for account {}",accountId);

        final Optional<SleepHmmWithInterpretation> hmmOptional = hmmDAO.getLatestModelForDate(accountId, targetDate.getMillis());

        if (!hmmOptional.isPresent()) {
            return ImmutableList.copyOf(Collections.EMPTY_LIST);

        }

        final Optional<SleepHmmWithInterpretation.SleepHmmResult> optionalHmmPredictions = hmmOptional.get().getSleepEventsUsingHMM(
                    allSensorSampleList, myMotion,targetDate.getMillis(),endDate.getMillis(),currentTimeMillis);

        if (!optionalHmmPredictions.isPresent()) {
            return ImmutableList.copyOf(Collections.EMPTY_LIST);
        }


        SleepHmmWithInterpretation.SleepHmmResult res = optionalHmmPredictions.get();

        return res.sleepEvents;



    }

    private List<Event> getSleepScoreEvents(final DateTime targetDate,
                                            final AllSensorSampleList allSensorSampleList,
                                            final List<TrackerMotion> myMotion) {


        SleepEvents<Optional<Event>> sleepEventsFromAlgorithm = timelineProcessor.fromAlgorithm(targetDate, myMotion,
                allSensorSampleList.get(Sensor.LIGHT),
                allSensorSampleList.get(Sensor.WAVE_COUNT));

        List<Optional<Event>> items = sleepEventsFromAlgorithm.toList();
        List<Event> returnedEvents = new ArrayList<>();

        for (Optional<Event> e : items) {
            if (e.isPresent()) {
                returnedEvents.add(e.get());
            }
        }

        return returnedEvents;

    }


    private List<Event> getVotingEvents(final AllSensorSampleList allSensorSampleList,
                                        final List<TrackerMotion> trackerMotions) {
        // compute lights-out and sound-disturbance events
        final Optional<DateTime> wakeUpWaveTimeOptional = timelineUtils.getFirstAwakeWaveTime(trackerMotions.get(0).timestamp,
                trackerMotions.get(trackerMotions.size() - 1).timestamp,
                allSensorSampleList.get(Sensor.WAVE_COUNT));

        final List<Event> rawLightEvents = timelineUtils.getLightEventsWithMultipleLightOut(allSensorSampleList.get(Sensor.LIGHT));
        final List<Event> smoothedLightEvents = MultiLightOutUtils.smoothLight(rawLightEvents, MultiLightOutUtils.DEFAULT_SMOOTH_GAP_MIN);
        final List<Event> lightOuts = MultiLightOutUtils.getValidLightOuts(smoothedLightEvents, trackerMotions, MultiLightOutUtils.DEFAULT_LIGHT_DELTA_WINDOW_MIN);

        final List<DateTime> lightOutTimes = MultiLightOutUtils.getLightOutTimes(lightOuts);
        final Vote vote = new Vote(TrackerMotionUtils.trackerMotionToAmplitudeData(trackerMotions),
                TrackerMotionUtils.trackerMotionToKickOffCounts(trackerMotions),
                SoundUtils.sampleToAmplitudeData(allSensorSampleList.get(Sensor.SOUND)),
                lightOutTimes, wakeUpWaveTimeOptional);

        final SleepEvents<Segment> sleepEvents = vote.getResult(false);
        final Segment goToBedSegment = sleepEvents.goToBed;
        final Segment fallAsleepSegment = sleepEvents.fallAsleep;
        final Segment wakeUpSegment = sleepEvents.wakeUp;
        final Segment outOfBedSegment = sleepEvents.outOfBed;

        //final int smoothWindowSizeInMillis = smoothWindowSizeInMinutes * DateTimeConstants.MILLIS_PER_MINUTE;
        final Event inBedEvent = new InBedEvent(goToBedSegment.getStartTimestamp(),
                goToBedSegment.getEndTimestamp(),
                goToBedSegment.getOffsetMillis());

        final Event fallAsleepEvent = new FallingAsleepEvent(fallAsleepSegment.getStartTimestamp(),
                fallAsleepSegment.getEndTimestamp(),
                fallAsleepSegment.getOffsetMillis());

        final Event wakeUpEvent = new WakeupEvent(wakeUpSegment.getStartTimestamp(),
                wakeUpSegment.getEndTimestamp(),
                wakeUpSegment.getOffsetMillis());

        final Event outOfBedEvent = new OutOfBedEvent(outOfBedSegment.getStartTimestamp(),
                outOfBedSegment.getEndTimestamp(),
                outOfBedSegment.getOffsetMillis());

        final SleepEvents<Event> events = SleepEvents.create(inBedEvent, fallAsleepEvent, wakeUpEvent, outOfBedEvent);
        final Optional<Event> goToBed = Optional.of(events.goToBed);
        final Optional<Event> sleep = Optional.of(events.fallAsleep);
        final Optional<Event> wakeUp = Optional.of(events.wakeUp);
        final Optional<Event> outOfBed = Optional.of(events.outOfBed);

        final SleepEvents<Optional<Event>> optionalSleepEvents = SleepEvents.create(goToBed, sleep, wakeUp, outOfBed);  //SleepEventSafeGuard.sleepEventsHeuristicFix(events, vote.getAggregatedFeatures());
        final List<Optional<Event>> items = optionalSleepEvents.toList();

        List<Event> returnedEvents = new ArrayList<>();

        for (Optional<Event> e : items) {
            if (e.isPresent()) {
                returnedEvents.add(e.get());
            }
        }

        return returnedEvents;

    }

    /* Takes protobuf data directly and decodes  */
    private class LocalSleepHmmDAO implements SleepHmmDAO {
        final Optional<SleepHmmWithInterpretation> hmm;

        public LocalSleepHmmDAO (final String base64data) {
            Optional<SleepHmmWithInterpretation> sleepHmm = Optional.absent();

            if (base64data.length() > 0) {


                try {

                    final byte[] decodedBytes = Base64.decodeBase64(base64data);

                    final SleepHmmProtos.SleepHmmModelSet proto = SleepHmmProtos.SleepHmmModelSet.parseFrom(decodedBytes);

                    sleepHmm = SleepHmmWithInterpretation.createModelFromProtobuf(proto);


                } catch (IOException e) {
                    LOGGER.debug("failed to decode protobuf");
                }
            }

            hmm = sleepHmm;

        }

        public boolean isValid() {
            return hmm.isPresent();
        }

        @Override
        public Optional<SleepHmmWithInterpretation> getLatestModelForDate(long accountId, long timeOfInterestMillis) {
            return hmm;
        }
    }


    @GET
    @Path("/timeline/{account_id}/{query_date_local_utc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public  ImmutableList<Timeline> getSleepPredictionByUser(  @Scope({OAuthScope.RESEARCH}) final AccessToken accessToken,

                                                   @PathParam("account_id") final  Long accountId,

                                                   @PathParam("query_date_local_utc") final String strTargetDate) {


        final DateTime targetDate = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(20);
        final DateTime endDate = targetDate.plusHours(16);


        TimelineResult result = timelineProcessor.retrieveTimelinesFast(accountId, targetDate, Optional.<TimelineFeedback>absent());


        if (result.timelines.isEmpty()) {
            return ImmutableList.copyOf(Collections.EMPTY_LIST);
        }

        return result.timelines;
    }


    public static class SensorData {
        public final AllSensorSampleList senseSensorData;
        public final List<TrackerMotion> myMotion;
        public final List<TrackerMotion> partnerMotion;
        public final List<TrackerMotion> myMotionFiltered;

        public SensorData(AllSensorSampleList senseSensorData, List<TrackerMotion> myMotion, List<TrackerMotion> partnerMotion, List<TrackerMotion> myMotionFiltered) {
            this.senseSensorData = senseSensorData;
            this.myMotion = myMotion;
            this.partnerMotion = partnerMotion;
            this.myMotionFiltered = myMotionFiltered;
        }
    }


    public SensorData getSensorData(final DateTime targetDate,final DateTime endDate, final Long accountId, boolean usePartnerFilter) {
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);

        if (!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "no sense found")).build());
        }

        /* Get "Pill" data  */
        final List<TrackerMotion> myMotions = trackerMotionDAO.getBetweenLocalUTC(accountId, targetDate, endDate);
        final List<TrackerMotion> partnerMotions = getPartnerTrackerMotion(accountId, targetDate, endDate);

        LOGGER.debug("Length of trackerMotion: {}, partnerTrackerMotion: {}", myMotions.size(),partnerMotions.size());


        if (myMotions.isEmpty()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "no motion data found")).build());
        }


        final int tzOffsetMillis = myMotions.get(0).offsetMillis;


        final List<TrackerMotion> motions = new ArrayList<>();

        if (!partnerMotions.isEmpty() && usePartnerFilter ) {
            try {
                PartnerDataUtils partnerDataUtils = new PartnerDataUtils();

                final ImmutableList<TrackerMotion> myFilteredMotions =
                        partnerDataUtils.partnerFilterWithDurationsDiffHmm(targetDate.minusMillis(tzOffsetMillis),endDate.minusMillis(tzOffsetMillis),ImmutableList.copyOf(myMotions), ImmutableList.copyOf(partnerMotions));

                motions.addAll(myFilteredMotions);
            }
            catch (Exception e) {
                LOGGER.error(e.getMessage());
                motions.addAll(myMotions);
            }
        }
        else {
            motions.addAll(myMotions);
        }


        // get all sensor data, used for light and sound disturbances, and presleep-insights
        AllSensorSampleList sensorData = new AllSensorSampleList();

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);


        sensorData = deviceDataDAO.generateTimeSeriesByUTCTimeAllSensors(
                targetDate.minusMillis(tzOffsetMillis).getMillis(),
                endDate.minusMillis(tzOffsetMillis).getMillis(),
                accountId, deviceIdPair.get().internalDeviceId, SLOT_DURATION_MINUTES, MISSING_DATA_DEFAULT_VALUE, color, Optional.<Calibration>absent());


        return new SensorData(sensorData,myMotions,partnerMotions,motions);

    }

    @GET
    @Path("/alphabet/{account_id}/{query_date_local_utc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    /*  Returns HMM Bayesnet model interpretations  */
    public AlphabetsAndLabels getAlphabetsByUser(  @Scope({OAuthScope.RESEARCH}) final AccessToken accessToken,
                                                         @PathParam("account_id") final  Long accountId,
                                                         @PathParam("query_date_local_utc") final String strTargetDate,
                                                         @DefaultValue("true") @QueryParam("partner_filter") final Boolean usePartnerFilter
    ) {


           /*  Time stuff */
        final long  currentTimeMillis = DateTime.now().withZone(DateTimeZone.UTC).getMillis();

        final DateTime dateOfNight = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(0);
        final DateTime targetDate = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(20);
        final DateTime endDate = targetDate.plusHours(16);

        LOGGER.debug("Target date: {}", targetDate);
        LOGGER.debug("End date: {}", endDate);


        final SensorData allData = getSensorData(targetDate,endDate,accountId,usePartnerFilter);

        if (allData.myMotion.isEmpty()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "no motion data for this user")).build());
        }

        final int tzOffset = allData.myMotion.get(0).offsetMillis;

        final Long startTimeUtc = targetDate.minusMillis(tzOffset).getMillis();
        final Long endTimeUTc = endDate.minusMillis(tzOffset).getMillis();



        //get model from DB
        final FeatureExtractionModelData featureExtractionModelData = featureExtractionModelsDAO.getLatestModelForDate(accountId, targetDate, Optional.<UUID>absent());

        if (!featureExtractionModelData.isValid()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "model data was not valid")).build());
        }

        final Optional<OnlineHmmSensorDataBinning.BinnedData> binnedDataOptional = OnlineHmmSensorDataBinning.getBinnedSensorData(allData.senseSensorData, allData.myMotionFiltered, allData.partnerMotion,
                featureExtractionModelData.getDeserializedData().params, startTimeUtc, endTimeUTc, tzOffset);

        if (!binnedDataOptional.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "unable to get binned data")).build());
        }

        OnlineHmmSensorDataBinning.BinnedData binnedData = binnedDataOptional.get();

        final Map<String,List<Integer>> pathsByModelId = Maps.newHashMap();
        final Map<String,Integer> numStates = Maps.newHashMap();

        final Map<String,HiddenMarkovModelInterface> hmmByModelName = featureExtractionModelData.getDeserializedData().sensorDataReduction.hmmByModelName;
        //DECODE ALL SENSOR DATA INTO DISCRETE "CLASSIFICATIONS"
        for (final String modelName : hmmByModelName.keySet()) {

            final HiddenMarkovModelInterface hmm = hmmByModelName.get(modelName);
            final Integer [] possibleEndStates = {hmm.getNumberOfStates() - 1};

            numStates.put(modelName,hmm.getNumberOfStates());
            final HmmDecodedResult hmmDecodedResult = hmm.decode(binnedData.data, possibleEndStates,1e-320);

            pathsByModelId.put(modelName, hmmDecodedResult.bestPath);
        }

        //get feedback for this day
        final ImmutableList<TimelineFeedback> feedbacks = feedbackDAO.getForNight(accountId, dateOfNight);

        final List<FeedbackUtils.EventWithTime> feedbacksAsEvents = FeedbackUtils.getFeedbackEventsWithOriginalTime(feedbacks.asList(), tzOffset);

        LOGGER.debug("got {} pieces of feedback",feedbacksAsEvents.size());

        final List<FeedbackAsIndices> feedbackAsIndices = Lists.newArrayList();

        for (FeedbackUtils.EventWithTime eventWithTime : feedbacksAsEvents) {
            final int updatedIndex = (int)((eventWithTime.event.getStartTimestamp() - binnedData.t0) / (long)binnedData.numMinutesInWindow / 60000L);
            final int originalIndex = (int)((eventWithTime.time - binnedData.t0) / (long)binnedData.numMinutesInWindow / 60000L);
            feedbackAsIndices.add(new FeedbackAsIndices(originalIndex,updatedIndex,eventWithTime.event.getType().name()));
        }


        return new AlphabetsAndLabels(pathsByModelId,numStates,feedbackAsIndices,accountId,dateOfNight);

    }

    @GET
    @Path("/generate_models/{account_id}/{date_string}/{num_days}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public GeneratedModel generateModelsForUser( @Scope({OAuthScope.RESEARCH}) final AccessToken accessToken,
                                                                   @PathParam("account_id") final  Long accountId,
                                                                   @PathParam("date_string") final  String dateString,
                                                                   @PathParam("num_days") final  Integer numDays,
                                                                   @DefaultValue("true") @QueryParam("partner_filter") final Boolean usePartnerFilter) {


        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);

        if (!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "no sense found")).build());
        }

        if (numDays <= 0) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "number of days must be greater than zero")).build());
        }

        if (numDays > 200) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "limited to doing 200 days at a time, which is completely arbitrary")).build());
        }

        final DateTime dateOfStartNight = DateTime.parse(dateString, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(0);

        final TreeMap<DateTime, OnlineHmmData> priors = Maps.newTreeMap();

        final OnlineHmmModelsDAO inMemoryModelsDao = new OnlineHmmModelsDAO() {
            public final TreeMap<DateTime, OnlineHmmData> priorByDate = priors;

            @Override
            public OnlineHmmData getModelDataByAccountId(Long accountId, DateTime date) {

                final Map.Entry<DateTime,OnlineHmmData> entry = priorByDate.floorEntry(date);

                if (entry == null) {
                    LOGGER.warn("GET got nothing ");
                    return OnlineHmmData.createEmpty();
                }

                int numModels = 0;
                for (final Map.Entry<String, Map<String,OnlineHmmModelParams>> item : entry.getValue().modelPriors.modelsByOutputId.entrySet()) {
                    numModels += item.getValue().size();
                }

                LOGGER.info("GET {} models: {} from date {}",numModels,entry.getValue().modelPriors.modelsByOutputId.keySet(),DateTimeUtil.dateToYmdString(entry.getKey()));

                return entry.getValue();
            }

            @Override
            public boolean updateModelPriorsAndZeroOutScratchpad(Long accountId, DateTime date, OnlineHmmPriors priors) {
                final OnlineHmmData newOnlineHmmData = new OnlineHmmData( priors,OnlineHmmScratchPad.createEmpty());

                int numModels = 0;
                for (final Map.Entry<String, Map<String,OnlineHmmModelParams>> entry : priors.modelsByOutputId.entrySet()) {
                    numModels += entry.getValue().size();
                }

                priorByDate.put(date,newOnlineHmmData);
                LOGGER.info("PUT {} models: {} on date {}",numModels,priors.modelsByOutputId.keySet(),DateTimeUtil.dateToYmdString(date));
                return true;
            }

            @Override
            public boolean updateScratchpad(Long accountId, DateTime date, OnlineHmmScratchPad scratchPad) {
                final DateTime key  =  priorByDate.floorKey(date);

                if (key != null) {
                    LOGGER.info("PUT scratchpad items {} on date {}",scratchPad.paramsByOutputId.keySet(),DateTimeUtil.dateToYmdString(date));
                    priorByDate.put(key,new OnlineHmmData(priorByDate.get(key).modelPriors,scratchPad));
                    return true;
                }

                return false;
            }
        };

        final FeatureExtractionModelData featureExtractionModels = featureExtractionModelsDAO.getLatestModelForDate(accountId, dateOfStartNight, Optional.<UUID>absent());

        final FeatureExtractionModelsDAO inMemoryFeatureExtraction = new FeatureExtractionModelsDAO() {
            @Override
            public FeatureExtractionModelData getLatestModelForDate(Long accountId, DateTime dateTimeLocalUTC, Optional<UUID> uuidForLogger) {
                return featureExtractionModels;
            }
        };

        for (int iDay = 0; iDay < numDays; iDay++) {
            final DateTime night = dateOfStartNight.plusDays(iDay);
            final DateTime startTime = night.withHourOfDay(20);
            final DateTime endTime = startTime.plusHours(16);

            final Optional<OneDaysSensorData> oneDaysSensorDataOptional = getOneDaysSensorData(accountId,night,startTime,endTime,usePartnerFilter,true);

            if (!oneDaysSensorDataOptional.isPresent()) {
                LOGGER.info("skipping {} because no feedback found or is invalid night",DateTimeUtil.dateToYmdString(night));
                continue;
            }


            final OneDaysSensorData oneDaysSensorData = oneDaysSensorDataOptional.get();

            LOGGER.info("Found {} pieces of feedback",oneDaysSensorData.feedbackList.size());

            //this should automatically update the database for the user
            getOnlineHmmEvents(night, startTime, endTime, accountId,oneDaysSensorData,defaultModelEnsembleDAO,inMemoryFeatureExtraction, inMemoryModelsDao,true);

        }

        final OnlineHmmData data = inMemoryModelsDao.getModelDataByAccountId(accountId, dateOfStartNight.plusDays(numDays));

        int numModels = 0;
        for (final Map.Entry<String, Map<String,OnlineHmmModelParams>> entry : data.modelPriors.modelsByOutputId.entrySet()) {
            numModels += entry.getValue().size();
        }

        return new GeneratedModel(
                accountId,
                DateTimeUtil.dateToYmdString(dateOfStartNight),
                numDays,
                numModels,
                Base64.encodeBase64String(data.modelPriors.serializeToProtobuf()));
    }

    private List<TrackerMotion> getPartnerMotionData(final DateTime timeStartLocalUtc, final DateTime timeEndLocalUtc,final int tzOffsetMillis, final List<TrackerMotion> myMotion, final List<TrackerMotion> partnerMotion) {
        final List<TrackerMotion> motions = Lists.newArrayList();
        final PartnerDataUtils partnerDataUtils = new PartnerDataUtils();

        try {
            motions.addAll(
                    partnerDataUtils.partnerFilterWithDurationsDiffHmm(
                            timeStartLocalUtc.minusMillis(tzOffsetMillis),
                            timeEndLocalUtc.minusMillis(tzOffsetMillis),
                            ImmutableList.copyOf(myMotion),
                            ImmutableList.copyOf(partnerMotion)));

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            motions.addAll(myMotion);
        }

        return motions;
    }

    private Optional<OneDaysSensorData> getOneDaysSensorData(final long accountId,final DateTime dateOfEvening, final DateTime startTimeLocalUtc, final DateTime endTimeLocalUtc, boolean usePartnerFilter,boolean failIfNofeedback) {

        LOGGER.info("Getting sensor data for account_id={},date={}",accountId,DateTimeUtil.dateToYmdString(dateOfEvening));
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);

        if (!deviceIdPair.isPresent()) {
            LOGGER.warn("no sense found");
            return Optional.absent();
        }


        //get feedback for this day
        final ImmutableList<TimelineFeedback> feedbacks = feedbackDAO.getForNight(accountId, dateOfEvening);

        if (feedbacks.isEmpty() && failIfNofeedback) {
            return Optional.absent();
        }

        /* Get "Pill" data  */
        final List<TrackerMotion> myMotions = trackerMotionDAO.getBetweenLocalUTC(accountId, startTimeLocalUtc, endTimeLocalUtc);
        final List<TrackerMotion> partnerMotions = getPartnerTrackerMotion(accountId, startTimeLocalUtc, endTimeLocalUtc);

        LOGGER.debug("Length of trackerMotion: {}, partnerTrackerMotion: {}", myMotions.size(),partnerMotions.size());

        if (myMotions.isEmpty()) {
            LOGGER.warn("no motion data found");
            return Optional.absent();
        }

        final int tzOffsetMillis = myMotions.get(0).offsetMillis;

        final List<TrackerMotion> motions = new ArrayList<>();

        if (!partnerMotions.isEmpty() && usePartnerFilter ) {
            motions.addAll(getPartnerMotionData(startTimeLocalUtc, endTimeLocalUtc, tzOffsetMillis, myMotions, partnerMotions));
        }
        else {
            motions.addAll(myMotions);
        }

        if (TimelineProcessor.isValidNight(accountId,myMotions,motions) != TimelineError.NO_ERROR) {
            LOGGER.warn("not a valid night");
            return Optional.absent();
        };

        // get all sensor data, used for light and sound disturbances, and presleep-insights
        AllSensorSampleList allSensorSampleList = new AllSensorSampleList();

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);


        allSensorSampleList = deviceDataDAO.generateTimeSeriesByUTCTimeAllSensors(
                startTimeLocalUtc.minusMillis(tzOffsetMillis).getMillis(),
                endTimeLocalUtc.minusMillis(tzOffsetMillis).getMillis(),
                accountId, deviceIdPair.get().internalDeviceId, SLOT_DURATION_MINUTES, MISSING_DATA_DEFAULT_VALUE,color, Optional.<Calibration>absent());

        return Optional.of(new OneDaysSensorData(allSensorSampleList,ImmutableList.copyOf(motions),ImmutableList.copyOf(partnerMotions),feedbacks,tzOffsetMillis));

    }


    @GET
    @Path("/sleep_events/{account_id}/{query_date_local_utc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public EventsWithLabels getSleepPredictionsByUserAndAlgorithm(
            @Scope({OAuthScope.RESEARCH}) final AccessToken accessToken,
            @PathParam("account_id") final  Long accountId,
            @PathParam("query_date_local_utc") final String strTargetDate,
            @DefaultValue(ALGORITHM_HIDDEN_MARKOV) @QueryParam("algorithm") final String algorithm,
            @DefaultValue("") @QueryParam("hmm_protobuf") final String protobuf,
            @DefaultValue("true") @QueryParam("partner_filter") final Boolean usePartnerFilter,
            @DefaultValue("false") @QueryParam("force_learning") final Boolean forceLearning

    ) {

        /*  default return */
        List<Event> events = new ArrayList<Event>();


        /* deal with proto  */
        SleepHmmDAO hmmDAO = this.sleepHmmDAO;

        LocalSleepHmmDAO localSleepHmmDAO = new LocalSleepHmmDAO(protobuf);

        if (localSleepHmmDAO.isValid()) {
            hmmDAO = localSleepHmmDAO;
        }


        /*  Time stuff */
        final long  currentTimeMillis = DateTime.now().withZone(DateTimeZone.UTC).getMillis();

        final DateTime dateOfNight = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(0);
        final DateTime targetDate = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(20);
        final DateTime endDate = targetDate.plusHours(16);

        LOGGER.debug("Target date: {}", targetDate);
        LOGGER.debug("End date: {}", endDate);



        final Optional<OneDaysSensorData> oneDaysSensorDataOptional = getOneDaysSensorData(accountId,dateOfNight,targetDate,endDate,usePartnerFilter,forceLearning);

        if (!oneDaysSensorDataOptional.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                    .entity(new JsonError(204, "failed to get sensor data for the day")).build());
        }

        final OneDaysSensorData oneDaysSensorData = oneDaysSensorDataOptional.get();


         /*  pull out algorithm type */

        switch (algorithm) {
            case ALGORITHM_VOTING:
                events = getVotingEvents(oneDaysSensorData.allSensorSampleList, oneDaysSensorData.trackerMotions);
                break;
            case ALGORITHM_SLEEP_SCORED:
                events = getSleepScoreEvents(targetDate, oneDaysSensorData.allSensorSampleList, oneDaysSensorData.trackerMotions);
                break;

            case ALGORITHM_HIDDEN_MARKOV:
                events = getHmmEvents(targetDate,endDate,currentTimeMillis,accountId,oneDaysSensorData.allSensorSampleList,oneDaysSensorData.trackerMotions,hmmDAO);
                break;

            case ALGORITHM_ONLINEHMM:
                events = getOnlineHmmEvents(dateOfNight, targetDate, endDate, accountId,oneDaysSensorData,defaultModelEnsembleDAO,featureExtractionModelsDAO,priorsDAO,forceLearning);
                break;

            default:
                throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT)
                        .entity(new JsonError(204, "bad alg specified")).build());

        }




        final List<FeedbackUtils.EventWithTime> feedbacksAsEvents = FeedbackUtils.getFeedbackEventsWithOriginalTime(oneDaysSensorData.feedbackList.asList(), oneDaysSensorData.timezoneOffsetMillis);

        LOGGER.debug("got {} pieces of feedback",feedbacksAsEvents.size());

        List<Event> feedbackEvents = Lists.newArrayList();
        for (FeedbackUtils.EventWithTime eventWithTime : feedbacksAsEvents) {
            feedbackEvents.add(eventWithTime.event);
        }

        final EventsWithLabels eventsWithLabels = new EventsWithLabels(events,Lists.newArrayList(feedbackEvents));

        return eventsWithLabels;

    }
}
