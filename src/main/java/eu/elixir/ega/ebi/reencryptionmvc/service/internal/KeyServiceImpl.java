/*
 * Copyright 2017 ELIXIR EBI
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
package eu.elixir.ega.ebi.reencryptionmvc.service.internal;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import eu.elixir.ega.ebi.reencryptionmvc.service.KeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author asenf
 */
@Service
@Primary
public class KeyServiceImpl implements KeyService {

    private final String SERVICE_URL = "http://KEYSERVICE";
    
    @Autowired
    @LoadBalanced
    RestTemplate restTemplate;
    
    @Override
    @Cacheable
    @HystrixCommand
    public String getFileKey(String fileId) {
        
        ResponseEntity<String> forEntity = restTemplate.getForEntity(SERVICE_URL + "/keys/filekeys/{file_id}", String.class, fileId);
        String body = forEntity.getBody();        
        
        return body;
    }

    @Override
    @Cacheable
    @HystrixCommand
    public String[] getFormats() {
        
        ResponseEntity<String[]> forEntity = restTemplate.getForEntity(SERVICE_URL + "/keys/formats", String[].class);
        String[] body = forEntity.getBody();        
        
        return body;
    }
    
    @Override
    @Cacheable
    @HystrixCommand
    public String[] getKeyPath(String key) {
        
        ResponseEntity<String[]> forEntity = restTemplate.getForEntity(SERVICE_URL + "/keys/paths/{key}", String[].class, key);
        String[] body = forEntity.getBody();        
        
        return body;
    }

}
