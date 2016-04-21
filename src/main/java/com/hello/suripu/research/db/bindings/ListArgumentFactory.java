package com.hello.suripu.research.db.bindings;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by benjo on 4/20/16.
 */
public class ListArgumentFactory implements ArgumentFactory<List> {
    @Override
    public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
        return value instanceof List;
    }

    @Override
    public Argument build(Class<?> expectedType, final List value, StatementContext ctx) {
        return new Argument() {
            @Override
            public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
                String type = null;
                if(value.get(0).getClass() == String.class){
                    type = "varchar";
                } else if(value.get(0).getClass() == Long.class){
                   type = "int8";
                } else if(value.get(0).getClass() == Integer.class){
                    type = "int4[]";
                } else {
                    throw new SQLException("unrecognized class");
                }

                try {
                    Array array = ctx.getConnection().createArrayOf(type, value.toArray());
                    statement.setArray(position, array);
                }
                catch (SQLException e) {
                    int foo = 3;
                    foo++;
                }
            }
        };
    }


    /*


    71     private static final Object types[][] = {
72         {"int2", new Integer(Oid.INT2), new Integer(Types.SMALLINT), "java.lang.Integer", new Integer(Oid.INT2_ARRAY)},
73         {"int4", new Integer(Oid.INT4), new Integer(Types.INTEGER), "java.lang.Integer", new Integer(Oid.INT4_ARRAY)},
74         {"oid", new Integer(Oid.OID), new Integer(Types.BIGINT), "java.lang.Long", new Integer(Oid.OID_ARRAY)},
75         {"int8", new Integer(Oid.INT8), new Integer(Types.BIGINT), "java.lang.Long", new Integer(Oid.INT8_ARRAY)},
76         {"money", new Integer(Oid.MONEY), new Integer(Types.DOUBLE), "java.lang.Double", new Integer(Oid.MONEY_ARRAY)},
77         {"numeric", new Integer(Oid.NUMERIC), new Integer(Types.NUMERIC), "java.math.BigDecimal", new Integer(Oid.NUMERIC_ARRAY)},
78         {"float4", new Integer(Oid.FLOAT4), new Integer(Types.REAL), "java.lang.Float", new Integer(Oid.FLOAT4_ARRAY)},
79         {"float8", new Integer(Oid.FLOAT8), new Integer(Types.DOUBLE), "java.lang.Double", new Integer(Oid.FLOAT8_ARRAY)},
80         {"char", new Integer(Oid.CHAR), new Integer(Types.CHAR), "java.lang.String", new Integer(Oid.CHAR_ARRAY)},
81         {"bpchar", new Integer(Oid.BPCHAR), new Integer(Types.CHAR), "java.lang.String", new Integer(Oid.BPCHAR_ARRAY)},
82         {"varchar", new Integer(Oid.VARCHAR), new Integer(Types.VARCHAR), "java.lang.String", new Integer(Oid.VARCHAR_ARRAY)},
83         {"text", new Integer(Oid.TEXT), new Integer(Types.VARCHAR), "java.lang.String", new Integer(Oid.TEXT_ARRAY)},
84         {"name", new Integer(Oid.NAME), new Integer(Types.VARCHAR), "java.lang.String", new Integer(Oid.NAME_ARRAY)},
85         {"bytea", new Integer(Oid.BYTEA), new Integer(Types.BINARY), "[B", new Integer(Oid.BYTEA_ARRAY)},
86         {"bool", new Integer(Oid.BOOL), new Integer(Types.BIT), "java.lang.Boolean", new Integer(Oid.BOOL_ARRAY)},
87         {"bit", new Integer(Oid.BIT), new Integer(Types.BIT), "java.lang.Boolean", new Integer(Oid.BIT_ARRAY)},
88         {"date", new Integer(Oid.DATE), new Integer(Types.DATE), "java.sql.Date", new Integer(Oid.DATE_ARRAY)},
89         {"time", new Integer(Oid.TIME), new Integer(Types.TIME), "java.sql.Time", new Integer(Oid.TIME_ARRAY)},
90         {"timetz", new Integer(Oid.TIMETZ), new Integer(Types.TIME), "java.sql.Time", new Integer(Oid.TIMETZ_ARRAY)},
91         {"timestamp", new Integer(Oid.TIMESTAMP), new Integer(Types.TIMESTAMP), "java.sql.Timestamp", new Integer(Oid.TIMESTAMP_ARRAY)},
92         {"timestamptz", new Integer(Oid.TIMESTAMPTZ), new Integer(Types.TIMESTAMP), "java.sql.Timestamp", new Integer(Oid.TIMESTAMPTZ_ARRAY)},
93     };
     */
}


