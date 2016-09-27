package com.hello.suripu.research;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.collect.Lists;
import com.hello.suripu.core.ObjectGraphRoot;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AccountDAOImpl;
import com.hello.suripu.core.db.ApplicationsDAO;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.CalibrationDynamoDB;
import com.hello.suripu.core.db.DefaultModelEnsembleDAO;
import com.hello.suripu.core.db.DefaultModelEnsembleFromS3;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.FeatureExtractionModelsDAO;
import com.hello.suripu.core.db.FeatureExtractionModelsDAODynamoDB;
import com.hello.suripu.core.db.FeatureStore;
import com.hello.suripu.core.db.FeedbackDAO;
import com.hello.suripu.core.db.OnlineHmmModelsDAO;
import com.hello.suripu.core.db.OnlineHmmModelsDAODynamoDB;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import com.hello.suripu.core.db.RingTimeHistoryDAODynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimelineLogDAO;
import com.hello.suripu.core.db.UserLabelDAO;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.db.colors.SenseColorDAOSQLImpl;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.core.logging.DataLogger;
import com.hello.suripu.core.logging.DataLoggerBatchPayload;
import com.hello.suripu.core.logging.KinesisBatchPutResult;
import com.hello.suripu.core.oauth.stores.PersistentApplicationStore;
import com.hello.suripu.coredropwizard.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredropwizard.configuration.S3BucketConfiguration;
import com.hello.suripu.coredropwizard.db.AccessTokenDAO;
import com.hello.suripu.coredropwizard.db.AuthorizationCodeDAO;
import com.hello.suripu.coredropwizard.db.SleepHmmDAODynamoDB;
import com.hello.suripu.coredropwizard.db.TimelineLogDAODynamoDB;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.AuthDynamicFeature;
import com.hello.suripu.coredropwizard.oauth.AuthValueFactoryProvider;
import com.hello.suripu.coredropwizard.oauth.OAuthAuthenticator;
import com.hello.suripu.coredropwizard.oauth.OAuthAuthorizer;
import com.hello.suripu.coredropwizard.oauth.OAuthCredentialAuthFilter;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowedDynamicFeature;
import com.hello.suripu.coredropwizard.oauth.stores.PersistentAccessTokenStore;
import com.hello.suripu.coredropwizard.util.CustomJSONExceptionMapper;
import com.hello.suripu.research.configuration.SuripuResearchConfiguration;
import com.hello.suripu.research.db.LabelDAO;
import com.hello.suripu.research.db.LabelDAOImpl;
import com.hello.suripu.research.modules.RolloutResearchModule;
import com.hello.suripu.research.resources.v1.AccountInfoResource;
import com.hello.suripu.research.resources.v1.DataScienceResource;
import com.hello.suripu.research.resources.v1.PredictionResource;
import io.dropwizard.Application;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;
import io.dropwizard.server.AbstractServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class SuripuResearch extends Application<SuripuResearchConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuripuResearch.class);

    public static void main(final String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        new SuripuResearch().run(args);
    }

    @Override
    public void initialize(final Bootstrap<SuripuResearchConfiguration> bootstrap) {
        bootstrap.addBundle(new DBIExceptionsBundle());

    }

    @Override
    public void run(SuripuResearchConfiguration configuration, Environment environment) throws Exception {
        final DBIFactory factory = new DBIFactory();
        final DBI commonDB = factory.build(environment, configuration.getCommonDB(), "postgresql");

        commonDB.registerArgumentFactory(new PostgresIntegerArrayArgumentFactory());

        final LabelDAO labelDAO = commonDB.onDemand(LabelDAOImpl.class);
        final AccountDAO accountDAO = commonDB.onDemand(AccountDAOImpl.class);

        final UserLabelDAO userLabelDAO = commonDB.onDemand(UserLabelDAO.class);
        final DeviceDAO deviceDAO = commonDB.onDemand(DeviceDAO.class);
        final ApplicationsDAO applicationsDAO = commonDB.onDemand(ApplicationsDAO.class);
        final AccessTokenDAO accessTokenDAO = commonDB.onDemand(AccessTokenDAO.class);
        final FeedbackDAO feedbackDAO = commonDB.onDemand(FeedbackDAO.class);
        final SenseColorDAO senseColorDAO = commonDB.onDemand(SenseColorDAOSQLImpl.class);
        // TODO: create research DB DAOs here

        final AuthorizationCodeDAO authorizationCodeDAO = commonDB.onDemand(AuthorizationCodeDAO.class);
        final PersistentApplicationStore applicationStore = new PersistentApplicationStore(applicationsDAO);
        final PersistentAccessTokenStore accessTokenStore = new PersistentAccessTokenStore(accessTokenDAO, applicationStore, authorizationCodeDAO);

        final String namespace = (configuration.getDebug()) ? "dev" : "prod";
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);

        final AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();

        final AmazonDynamoDBClientFactory dynamoDBClientFactory = AmazonDynamoDBClientFactory.create(awsCredentialsProvider, clientConfiguration, configuration.dynamoDBConfiguration());

        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);


        final AmazonDynamoDB featureDynamoDB = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURES);
        final String featureNamespace = (configuration.getDebug()) ? "dev" : "prod";
        final FeatureStore featureStore = new FeatureStore(featureDynamoDB, "features", featureNamespace);

        final Map<DynamoDBTableName, String> tables = configuration.dynamoDBConfiguration().tables();
        //sleep HMM protobufs in teh cloud
        final AmazonDynamoDB sleepHmmDynamoDbClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_HMM);
        final String sleepHmmTableName = tables.get(DynamoDBTableName.SLEEP_HMM);
        final SleepHmmDAODynamoDB sleepHmmDAODynamoDB = new SleepHmmDAODynamoDB(sleepHmmDynamoDbClient,sleepHmmTableName);

        final String priorDbTableName = "REPLACE_ME";
        final AmazonDynamoDB priorsDb = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURE_EXTRACTION_MODELS); // CHANGE ME
        final OnlineHmmModelsDAO priorsDAO = OnlineHmmModelsDAODynamoDB.create(priorsDb,priorDbTableName);

        final String modelDbTableName = tables.get(DynamoDBTableName.FEATURE_EXTRACTION_MODELS);
        final AmazonDynamoDB modelsDb = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURE_EXTRACTION_MODELS);
        final FeatureExtractionModelsDAO featureExtractionDAO = new FeatureExtractionModelsDAODynamoDB(modelsDb,modelDbTableName);
        final S3BucketConfiguration timelineModelEnsemblesConfig = configuration.getTimelineModelEnsemblesConfiguration();
        final S3BucketConfiguration seedModelConfig = configuration.getTimelineSeedModelConfiguration();
        final DefaultModelEnsembleDAO defaultModelEnsembleDAO = DefaultModelEnsembleFromS3.create(amazonS3, timelineModelEnsemblesConfig.getBucket(), timelineModelEnsemblesConfig.getKey(), seedModelConfig.getBucket(), seedModelConfig.getKey());

        final AmazonDynamoDB calibrationDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.CALIBRATION);
        final CalibrationDAO calibrationDAO = CalibrationDynamoDB.create(calibrationDynamoDBClient, tables.get(DynamoDBTableName.CALIBRATION));


        //ring time history
        final AmazonDynamoDB ringTimeHistoryDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.RING_TIME_HISTORY);
        final RingTimeHistoryDAODynamoDB ringTimeHistoryDAODynamoDB = new RingTimeHistoryDAODynamoDB(ringTimeHistoryDynamoDBClient,
                tables.get(DynamoDBTableName.RING_TIME_HISTORY));

        //sleep stats

        final AmazonDynamoDB dynamoDBStatsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_STATS);
        final SleepStatsDAODynamoDB sleepStatsDAODynamoDB = new SleepStatsDAODynamoDB(dynamoDBStatsClient,
                tables.get(DynamoDBTableName.SLEEP_STATS),
                configuration.getSleepStatsVersion());


        final AmazonDynamoDB sensorData = dynamoDBClientFactory.getForTable(DynamoDBTableName.DEVICE_DATA);
        final DeviceDataDAODynamoDB deviceDataDAODynamoDB = new DeviceDataDAODynamoDB(sensorData, tables.get(DynamoDBTableName.DEVICE_DATA));

        final AmazonDynamoDB pillData = dynamoDBClientFactory.getForTable(DynamoDBTableName.PILL_DATA);
        final PillDataDAODynamoDB pillDataDAODynamoDB = new PillDataDAODynamoDB(pillData, tables.get(DynamoDBTableName.PILL_DATA));

        /*  Timeline Log dynamo dB stuff */
        final String timelineLogTableName =   tables.get(DynamoDBTableName.TIMELINE_LOG);
        final AmazonDynamoDB timelineLogDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.TIMELINE_LOG);
        final TimelineLogDAO timelineLogDAO = new TimelineLogDAODynamoDB(timelineLogDynamoDBClient,timelineLogTableName);


        final RolloutResearchModule module = new RolloutResearchModule(featureStore, 30);
        ObjectGraphRoot.getInstance().init(module);

        LOGGER.warn("DEBUG MODE = {}", configuration.getDebug());

        DataLogger emptyDataLogger = new DataLogger() {
            @Override
            public void putAsync(String s, byte[] bytes) {

            }

            @Override
            public String put(String s, byte[] bytes) {
                return "foobars";
            }

            @Override
            public String putWithSequenceNumber(String s, byte[] bytes, String s1) {
                return "foobars";
            }

            @Override
            public KinesisBatchPutResult putRecords(List<DataLoggerBatchPayload> list) {
                return new KinesisBatchPutResult(0,0, Lists.<Boolean>newArrayList());
            }
        };


        //Doing this programmatically instead of in config files
        AbstractServerFactory sf = (AbstractServerFactory) configuration.getServerFactory();
        // disable all default exception mappers
        sf.setRegisterDefaultExceptionMappers(false);

        environment.jersey().register(new CustomJSONExceptionMapper(configuration.getDebug()));
        environment.jersey().register(new AuthDynamicFeature(new OAuthCredentialAuthFilter.Builder<AccessToken>()
                .setAuthenticator(new OAuthAuthenticator(accessTokenStore))
                .setAuthorizer(new OAuthAuthorizer())
                .setRealm("SUPER SECRET STUFF")
                .setPrefix("Bearer")
                .setLogger(emptyDataLogger)
                .buildAuthFilter()));
        environment.jersey().register(new ScopesAllowedDynamicFeature(applicationStore));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(AccessToken.class));


        environment.jersey().register(new DataScienceResource(accountDAO, pillDataDAODynamoDB,deviceDataDAODynamoDB, deviceDAO, userLabelDAO, feedbackDAO,timelineLogDAO,labelDAO,senseColorDAO));
        environment.jersey().register(new PredictionResource(accountDAO,pillDataDAODynamoDB, deviceDataDAODynamoDB,deviceDAO, userLabelDAO,sleepHmmDAODynamoDB,feedbackDAO,senseColorDAO,featureExtractionDAO,priorsDAO,defaultModelEnsembleDAO, configuration.getAlgorithmConfiguration()));
        environment.jersey().register(new AccountInfoResource(accountDAO, deviceDAO));
    }
}
