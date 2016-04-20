package com.hello.suripu.research.db;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.util.Bucketing;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.AllSensorSampleMap;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.research.db.mappers.SimpleDeviceDataMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 4/19/16.  This is for use with Redshift.
 */

@RegisterMapper(SimpleDeviceDataMapper.class)
public abstract class BatchSensorDataDAOImpl implements BatchSenseDataDAO {

    /*
    @QueryTimeOut(10000)
    @SqlQuery("SELECT COUNT(1) FROM device_sensors_master")
    @RegisterMapper(LongMapper.class)
    public abstract List<Long> testConnection();
*/

    @SqlQuery("SELECT account_id, ts, offset_millis, ambient_light, wave_count, audio_num_disturbances, audio_peak_disturbances_db, audio_peak_energy_db FROM device_sensors_master " +
            "WHERE local_utc_ts >= \'2016-02-02 00:00:00\' AND local_utc_ts <= \'2016-02-02 01:00:00\' AND account_id IN (1002,1012) ORDER BY account_id,ts ASC")
    public abstract List<DeviceData> getSensorDataForAccountsByLocalTime(@Bind("start_ts") final DateTime startDate, @Bind("end_ts") final DateTime endDate, @Bind("account_list") final String accounts);


    @SqlQuery("SELECT * FROM device_sensors_master " +
            "WHERE local_utc_ts >= :start_ts " +
            "AND local_utc_ts <= :end_ts " +
            "AND account_id IN (:accounts) " +
            "ORDER BY account_id,ts ASC")
    public abstract List<DeviceData> getSensorDataForAccountsByLocalTime3(@Bind("start_ts") final DateTime startDate,@Bind("end_ts") final DateTime endDate,@Bind("accounts") final String accounts);

    final private static int SLOT_DURATION_MINUTES = 1;
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BatchSensorDataDAOImpl.class);

    private static AllSensorSampleList getListFromDeviceData(final List<DeviceData> deviceDataList) {
        final AllSensorSampleList sensorDataResults = new AllSensorSampleList();
        final AllSensorSampleMap allSensorSampleMap = Bucketing.populateMapAll(deviceDataList,Optional.<Device.Color>absent(),Optional.<Calibration>absent(),false);

        if (deviceDataList.isEmpty()) {
            return sensorDataResults;
        }

        final DeviceData last = deviceDataList.get(deviceDataList.size()-1);
        final DeviceData first = deviceDataList.get(0);


        final int numberOfBuckets =(int)(last.localTime().withZone(DateTimeZone.UTC).getMillis() - first.localTime().withZone(DateTimeZone.UTC).getMillis()) / DateTimeConstants.MILLIS_PER_MINUTE;

        for (final Sensor sensor : Sensor.values()) {
            final Map<Long, Sample> sensorMap = allSensorSampleMap.get(sensor);
            if (!sensorMap.isEmpty()) {

                final Map<Long, Sample> map = Bucketing.generateEmptyMap(numberOfBuckets, first.localTime(), SLOT_DURATION_MINUTES, 0);

                // Override map with values from DB
                final Map<Long, Sample> merged = Bucketing.mergeResults(map, sensorMap);


                final List<Sample> sortedList = Bucketing.sortResults(merged, first.offsetMillis);
                sensorDataResults.add(sensor, sortedList);
            }
        }

        return sensorDataResults;
    }

    @Override
    public Map<Long, AllSensorSampleList> getDataInTimeRangeForListOfUsersInLocalTimezone(final DateTime startTimeInclusive,final  DateTime endTimeInclusive,final List<Long> accounts) {
        final Map<Long, AllSensorSampleList> results = Maps.newHashMap();

        String accountsStr = "";

        for (final Long l : accounts) {
            if (!accountsStr.isEmpty()) {
                accountsStr += ",";
            }
            accountsStr += l.toString();
        }

        try {

            LOGGER.info("running query between {} and {} for {} accounts",startTimeInclusive,endTimeInclusive,accounts.size());

            final List<DeviceData> sensorData = getSensorDataForAccountsByLocalTime3(startTimeInclusive, startTimeInclusive.plusHours(1), accountsStr);

            LOGGER.info("got {} rows",sensorData.size());

            final DeviceData theLast = sensorData.get(sensorData.size() - 1);
            DeviceData lastData = null;
            List<DeviceData> dataFromAccount = Lists.newArrayList();
            for (final DeviceData data : sensorData) {

                if ((lastData != null && !lastData.accountId.equals(data.accountId)) || data.equals(theLast)) {
                    results.put(lastData.accountId, getListFromDeviceData(dataFromAccount));
                    dataFromAccount = Lists.newArrayList();
                }

                dataFromAccount.add(data);

                lastData = data;
            }
        }
        catch (final Exception e) {
            LOGGER.error(e.getMessage());
        }

        return results;
    }
}
