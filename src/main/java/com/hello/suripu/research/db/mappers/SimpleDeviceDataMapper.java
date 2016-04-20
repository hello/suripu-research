package com.hello.suripu.research.db.mappers;

/**
 * Created by benjo on 4/19/16.
 */

import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.util.DataUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.util.DataUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SimpleDeviceDataMapper implements ResultSetMapper<DeviceData> {
    @Override
    public DeviceData map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        // convert light from raw counts to lux -- DVT units or later
        final DateTime dateTime = new DateTime(r.getTimestamp("ts"), DateTimeZone.UTC);
        final int rawLight = r.getInt("ambient_light");
        int lux = rawLight;
        float fLux = (float)rawLight;

        if (dateTime.getYear() > 2014) {
            fLux = DataUtils.convertLightCountsToLux(lux);
            lux = (int)fLux;
        }

        final Integer audioPeakEnergyDB = r.getInt("audio_peak_energy_db");

        final DeviceData deviceData = new DeviceData(
                r.getLong("account_id"),
                0L,
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                lux,
                fLux,
                0,
                0,
                dateTime,
                //new DateTime(r.getTimestamp("local_utc_ts"), DateTimeZone.UTC),
                r.getInt("offset_millis"),
                0,
                r.getInt("wave_count"),
                0,
                r.getInt("audio_num_disturbances"),
                r.getInt("audio_peak_disturbances_db"),
                0,
                audioPeakEnergyDB != null ? audioPeakEnergyDB : 0
        );
        return deviceData;
    }
}
