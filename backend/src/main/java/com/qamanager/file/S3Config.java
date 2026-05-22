package com.qamanager.file;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    private final S3Properties props;

    public S3Config(S3Properties props) {
        this.props = props;
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (props.accessKey() != null && !props.accessKey().isBlank()
            && props.secretKey() != null && !props.secretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client(AwsCredentialsProvider credsProvider) {
        return S3Client.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(credsProvider)
            .build();
    }

    @Bean
    public S3Presigner s3Presigner(AwsCredentialsProvider credsProvider) {
        return S3Presigner.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(credsProvider)
            .build();
    }
}
