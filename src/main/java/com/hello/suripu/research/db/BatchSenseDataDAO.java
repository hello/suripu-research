package com.hello.suripu.research.db;

import com.hello.suripu.core.models.AllSensorSampleList;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 4/19/16.
 */
public interface BatchSenseDataDAO {
    Map<Long,AllSensorSampleList> getDataInTimeRangeForListOfUsersInLocalTimezone(final DateTime startTimeInclusive, final DateTime endTimeInclusive, final List<Long> accounts);
}
