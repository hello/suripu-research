package com.hello.suripu.research.resources.v1;

import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.oauth.AccessToken;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.oauth.Scope;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.research.db.BatchSenseDataDAO;
import com.hello.suripu.research.models.EventsWithLabels;
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
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 4/19/16.
 */

@Path("/v1/batch_prediction")
public class BatchPredictionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchPredictionResource.class);

    final BatchSenseDataDAO batchData;
    final SenseColorDAO senseColorDAO;

    public BatchPredictionResource(final BatchSenseDataDAO batchData, final SenseColorDAO senseColorDAO) {
        this.batchData = batchData;
        this.senseColorDAO = senseColorDAO;
    }

    @GET
    @Path("/sleep_events/{query_date_local_utc}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void getSleepPredictionsByUserAndAlgorithm(  @Scope({OAuthScope.RESEARCH}) final AccessToken accessToken,
                                                        @PathParam("query_date_local_utc") final String strTargetDate,
                                                        @DefaultValue("[]") @QueryParam("hmm_protobuf") final List<Long> accounts) {


          /*  Time stuff */
        final long  currentTimeMillis = DateTime.now().withZone(DateTimeZone.UTC).getMillis();

        final DateTime dateOfNight = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(0);
        final DateTime targetDate = DateTime.parse(strTargetDate, DateTimeFormat.forPattern(DateTimeUtil.DYNAMO_DB_DATE_FORMAT))
                .withZone(DateTimeZone.UTC).withHourOfDay(20);
        final DateTime endDate = targetDate.plusHours(16);

        LOGGER.debug("Target date: {}", targetDate);
        LOGGER.debug("End date: {}", endDate);

        final Map<Long,AllSensorSampleList> sensorData =  this.batchData.getDataInTimeRangeForListOfUsersInLocalTimezone(targetDate,endDate,accounts);

        int foo = 3;
        foo++;
    }
}

