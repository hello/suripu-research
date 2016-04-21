package com.hello.suripu.research.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.coredw.configuration.DynamoDBConfiguration;
import com.hello.suripu.coredw.configuration.S3BucketConfiguration;
import com.hello.suripu.coredw.configuration.TaimurainHttpClientConfiguration;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.db.DatabaseConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by pangwu on 3/2/15.
 */
public class SuripuResearchConfiguration extends Configuration {
    @Valid
    @NotNull
    @JsonProperty("sensors_db")
    private DatabaseConfiguration sensorsDB = new DatabaseConfiguration();

    public DatabaseConfiguration getSensorsDB() {
        return sensorsDB;
    }

    @Valid
    @NotNull
    @JsonProperty("common_db")
    private DatabaseConfiguration commonDB = new DatabaseConfiguration();

    public DatabaseConfiguration getCommonDB() {
        return commonDB;
    }

    @Valid
    @NotNull
    @JsonProperty("research_db")
    private DatabaseConfiguration researchDB = new DatabaseConfiguration();

    public DatabaseConfiguration getResearchDB() {
        return researchDB;
    }


    @Valid
    @JsonProperty("debug")
    private Boolean debug = Boolean.FALSE;
    public Boolean getDebug() {
        return debug;
    }


    @Valid
    @NotNull
    @JsonProperty("features_db")
    private DynamoDBConfiguration featuresDynamoDBConfiguration;
    public DynamoDBConfiguration getFeaturesDynamoDBConfiguration(){
        return this.featuresDynamoDBConfiguration;
    }


    @Valid
    @NotNull
    @JsonProperty("sleephmm_db")
    private DynamoDBConfiguration sleepHmmDBConfiguration;
    public DynamoDBConfiguration getSleepHmmDBConfiguration(){
        return this.sleepHmmDBConfiguration;
    }


    @Valid
    @NotNull
    @JsonProperty("timeline_log_db")
    private DynamoDBConfiguration timelineLogDBConfiguration;
    public DynamoDBConfiguration getTimelineLogDBConfiguration(){
        return this.timelineLogDBConfiguration;
    }

    @Valid
    @NotNull
    @JsonProperty("online_hmm_models")
    private DynamoDBConfiguration onlineHmmModelsConfiguration;
    public DynamoDBConfiguration getOnlineHmmModelsConfiguration() {
        return this.onlineHmmModelsConfiguration;
    }

    @Valid
    @NotNull
    @JsonProperty("feature_extraction_models")
    private DynamoDBConfiguration featureExtractionConfiguration;
    public DynamoDBConfiguration getFeatureExtractionConfiguration() {
        return this.featureExtractionConfiguration;
    }

    @Valid
    @NotNull
    @JsonProperty("calibration")
    private DynamoDBConfiguration calibrationConfiguration;
    public DynamoDBConfiguration getCalibrationConfiguration() {return this.calibrationConfiguration;}


    @Valid
    @NotNull
    @JsonProperty("timeline_model_ensembles")
    private S3BucketConfiguration timelineModelEnsemblesConfiguration;
    public S3BucketConfiguration getTimelineModelEnsemblesConfiguration() { return timelineModelEnsemblesConfiguration; }

    @Valid
    @NotNull
    @JsonProperty("timeline_seed_model")
    private S3BucketConfiguration timelineSeedModelConfiguration;
    public S3BucketConfiguration getTimelineSeedModelConfiguration() { return timelineSeedModelConfiguration; }

    @NotNull
    @JsonProperty("taimurain_http_client")
    private TaimurainHttpClientConfiguration taimurainHttpClientConfiguration;
    public TaimurainHttpClientConfiguration getTaimurainHttpClientConfiguration() { return taimurainHttpClientConfiguration; }
}
