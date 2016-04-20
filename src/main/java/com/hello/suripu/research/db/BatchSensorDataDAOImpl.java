package com.hello.suripu.research.db;

import com.hello.suripu.core.db.util.Bucketing;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.research.db.mappers.SenseDataLineMapper;
import com.hello.suripu.research.models.SenseDataLine;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.unstable.BindIn;

import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 4/19/16.  This is for use with Redshift.
 */
@RegisterMapper(SenseDataLineMapper.class)
public abstract class BatchSensorDataDAOImpl implements BatchSenseDataDAO {

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
    public abstract List<SenseDataLine> getSensorDataForAccountsByLocalTime(@Bind("start_ts") final DateTime startDate, @Bind("end_ts") final DateTime endDate, @BindIn("account_list") final List<Long> accounts);

    @Override
    public Map<Long, AllSensorSampleList> getDataInTimeRangeForListOfUsersInLocalTimezone(final DateTime startTimeInclusive,final  DateTime endTimeInclusive,final List<Long> accounts) {
        final List<SenseDataLine> sensorData = getSensorDataForAccountsByLocalTime(startTimeInclusive,endTimeInclusive,accounts);

        DeviceData d = new DeviceData()

        AllSensorSampleList

        return null;
    }
}
