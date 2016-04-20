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
import com.hello.suripu.research.models.SenseDataLine;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.unstable.BindIn;

import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 4/19/16.  This is for use with Redshift.
 */
@RegisterMapper(SimpleDeviceDataMapper.class)
public abstract class BatchSensorDataDAOImpl implements BatchSenseDataDAO {
    final private static int SLOT_DURATION_MINUTES = 1;

    /*
    @SqlQuery("SELECT DISTINCT account_id FROM timeline_feedback " +
            "WHERE date_of_night >= :start_ts AND date_of_night < :end_ts AND  account_id IN " +
            "(SELECT account_id FROM account_device_map " +
            "JOIN " +
            "(SELECT device_name,COUNT(*) FROM account_device_map GROUP BY device_name HAVING COUNT(*) > 1) as partner_devices " +
            "on account_device_map.device_name = partner_devices.device_name)")
    public abstract List<Long> getPartnerAccountsThatHadFeedback(@Bind("start_ts") final DateTime startDate, @Bind("end_ts") final DateTime endDate);
    */

    @SqlQuery("SELECT account_id,ts,offset_millis,ambient_light,wave_count,audio_num_disturbances,audio_peak_disturbances_db" +
            "WHERE local_utc_ts >= :start_ts AND local_utc_ts <= :end_ts AND account_id IN (<account_list>) " +
            "ORDER BY account_id,ts ASC")
    public abstract List<DeviceData> getSensorDataForAccountsByLocalTime(@Bind("start_ts") final DateTime startDate, @Bind("end_ts") final DateTime endDate, @BindIn("account_list") final List<Long> accounts);


    private static AllSensorSampleList getListFromDeviceData(final List<DeviceData> deviceDataList) {
        final AllSensorSampleList sensorDataResults = new AllSensorSampleList();
        final AllSensorSampleMap allSensorSampleMap = Bucketing.populateMapAll(deviceDataList,Optional.<Device.Color>absent(),Optional.<Calibration>absent(),false);

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
        final List<DeviceData> sensorData = getSensorDataForAccountsByLocalTime(startTimeInclusive,endTimeInclusive,accounts);

        final DeviceData theLast = sensorData.get(sensorData.size() - 1);
        DeviceData lastData = null;
        List<DeviceData> dataFromAccount = Lists.newArrayList();
        for (final DeviceData data : sensorData) {

            if ((lastData != null && !lastData.accountId.equals(data.accountId)) || data.equals(theLast)) {
                results.put(lastData.accountId,getListFromDeviceData(dataFromAccount));
                dataFromAccount = Lists.newArrayList();
            }


            lastData = data;
        }

        return results;
    }
}
