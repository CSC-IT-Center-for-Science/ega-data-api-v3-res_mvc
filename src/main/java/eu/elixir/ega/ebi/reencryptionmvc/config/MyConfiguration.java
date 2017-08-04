/*
 * Copyright 2016 ELIXIR EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.elixir.ega.ebi.reencryptionmvc.config;

import eu.elixir.ega.ebi.reencryptionmvc.dto.MyArchiveConfig;
import eu.elixir.ega.ebi.reencryptionmvc.dto.MyFireConfig;
import eu.elixir.ega.ebi.reencryptionmvc.dto.MyAwsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author asenf
 */
@Configuration
public class MyConfiguration {
    @Value("${ega.ebi.fire.url}") String fireUrl;
    @Value("${ega.ebi.fire.archive}") String fireArchive;
    @Value("${ega.ebi.fire.key}") String fireKey;

    @Value("${ega.ebi.aws.access.key}") String awsKey;
    @Value("${ega.ebi.aws.access.secret}") String awsSecretKey;
    
    @Value("${service.archive.class}") String archiveImplBean;
    
    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public MyFireConfig MyCipherConfig() {
        return new MyFireConfig(fireUrl,
                                fireArchive,
                                fireKey);
    }
    
    @Bean
    public MyAwsConfig MyAwsCipherConfig() {
        return new MyAwsConfig(awsKey,
                                awsSecretKey);
    }

    @Bean
    public MyArchiveConfig MyArchiveConfig() {
        return new MyArchiveConfig(archiveImplBean);
    }
        
}
