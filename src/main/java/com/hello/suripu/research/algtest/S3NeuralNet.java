package com.hello.suripu.research.algtest;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.base.Optional;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Created by benjo on 2/17/16.
 */
public class S3NeuralNet {
    final static Logger LOGGER = LoggerFactory.getLogger(S3NeuralNet.class);

    public static Optional<NeuralNetAndInfo> getNet(final String bucket, final String keybase) {
        final AWSCredentialsProvider awsCredentialsProvider= new DefaultAWSCredentialsProviderChain();
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

        final String confKey = keybase + ".config";
        final String paramKey = keybase + ".params";

        final String confData = S3Utils.getRegularS3ObjectAsString(amazonS3,bucket,confKey);
        final byte [] paramsBinData = S3Utils.getRegularS3ObjectAsBytes(amazonS3,bucket,paramKey);

        if (confData.isEmpty()) {
            LOGGER.error("conf file {}/{} is empty",bucket,confKey);
            return Optional.absent();

        }

        if (paramsBinData.length == 0) {
            LOGGER.error("params file {}/{} is empty",bucket,paramKey);
            return Optional.absent();
        }

        final MultiLayerConfiguration confFromJson = MultiLayerConfiguration.fromJson(confData);
        final MultiLayerNetwork net = new MultiLayerNetwork(confFromJson);

        try {

            //final InputStream ios = Files.newInputStream(Paths.get("/Users/benjo/Downloads/foo.params"));
            final ByteArrayInputStream bis = new ByteArrayInputStream(paramsBinData);
            final DataInputStream dis = new DataInputStream(bis);

            final INDArray params = Nd4j.read(dis);
            net.init();
            net.setParameters(params);

            return Optional.of(new NeuralNetAndInfo(net,confFromJson));

        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e.getMessage());
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }




        return Optional.absent();

    }

}
