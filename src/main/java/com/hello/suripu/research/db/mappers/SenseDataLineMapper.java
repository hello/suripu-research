package com.hello.suripu.research.db.mappers;

import com.hello.suripu.research.models.SenseDataLine;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by benjo on 4/19/16.
 */
public class SenseDataLineMapper implements ResultSetMapper<SenseDataLine> {
    @Override
    public SenseDataLine map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
        return new SenseDataLine();
    }
}
