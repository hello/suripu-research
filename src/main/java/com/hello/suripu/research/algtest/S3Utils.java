package com.hello.suripu.research.algtest;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 * Created by benjo on 2/11/16.
 */
public class S3Utils {
    final static Logger LOGGER = LoggerFactory.getLogger(S3Utils.class);

    public static String getZippedS3Object(final AmazonS3 s3, final String bucket, final String key) {
        LOGGER.info("pulling {}/{}",bucket,key);

        final S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, key));
        try (final InputStream stream = new GZIPInputStream(s3Object.getObjectContent())) {
            final InputStreamReader inputStreamReader = new InputStreamReader(stream);
            return CharStreams.toString(inputStreamReader);
        }
        catch (IOException e) {
            LOGGER.error(e.getMessage());
        }

        return "";
    }

    public static String getRegularS3ObjectAsString(final AmazonS3 s3, final String bucket, final String key) {
        LOGGER.info("pulling {}/{}",bucket,key);

        final S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, key));

        try (final InputStream stream = s3Object.getObjectContent()) {
            final InputStreamReader inputStreamReader = new InputStreamReader(stream);
            return CharStreams.toString(inputStreamReader);

        }
        catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
        return "";

    }

    public static byte [] getRegularS3ObjectAsBytes(final AmazonS3 s3, final String bucket, final String key) {
        LOGGER.info("pulling {}/{}",bucket,key);

        final S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, key));

        try (final InputStream stream = s3Object.getObjectContent()) {
            return ByteStreams.toByteArray(stream);
        }
        catch (IOException e) {
            LOGGER.error(e.getMessage());
        }

        return new byte[0];
    }

    public static void putRegularS3Object(final byte [] data, final AmazonS3 s3, final String bucket, final String key) {

        LOGGER.info("putting to {}/{}",bucket,key);
        final InputStream is = new ByteArrayInputStream(data);

        // content is a passed in InputStream
        byte[] resultByte = DigestUtils.md5(data);
        String streamMD5 = new String(Base64.encodeBase64(resultByte));



        final ObjectMetadata metaData = new ObjectMetadata();
        metaData.setContentLength(data.length);
        metaData.setContentMD5(streamMD5);

        s3.putObject(bucket,key,is,metaData);
    }
}
