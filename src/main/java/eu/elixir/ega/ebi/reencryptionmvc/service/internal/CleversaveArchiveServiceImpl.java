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
package eu.elixir.ega.ebi.reencryptionmvc.service.internal;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import eu.elixir.ega.ebi.reencryptionmvc.dto.MyFireConfig;
import eu.elixir.ega.ebi.reencryptionmvc.config.NotFoundException;
import eu.elixir.ega.ebi.reencryptionmvc.service.ArchiveService;
import eu.elixir.ega.ebi.reencryptionmvc.dto.ArchiveSource;
import eu.elixir.ega.ebi.reencryptionmvc.dto.EgaFile;
import eu.elixir.ega.ebi.reencryptionmvc.service.KeyService;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
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
@Primary
@Service
@EnableCaching
@EnableDiscoveryClient
public class CleversaveArchiveServiceImpl implements ArchiveService {

    private final String SERVICE_URL = "http://DATA";
    
    @Autowired
    @LoadBalanced
    RestTemplate restTemplate;
    
    @Autowired
    private KeyService keyService;
    
    @Autowired
    private MyFireConfig myFireConfig;
    
    @Override
    @Cacheable
    @HystrixCommand
    public ArchiveSource getArchiveFile(String id) {

        // Get Filename from EgaFile ID - via DATA service (potentially multiple files)
        ResponseEntity<EgaFile[]> forEntity = restTemplate.getForEntity(SERVICE_URL + "/file/{file_id}", EgaFile[].class, id);
        EgaFile[] body = forEntity.getBody();        
        String fileName = (body!=null&&body.length>0)?forEntity.getBody()[0].getFileName():"";
        if ((body==null||body.length==0)) {
            throw new NotFoundException("Can't obtain File data for ID", id);
        }
        if (fileName.startsWith("/fire")) fileName = fileName.substring(16);
        // Guess Encryption Format from File
        String encryptionFormat = fileName.toLowerCase().endsWith("gpg")?"symmetricgpg":"aes256";
        String keyKey = encryptionFormat.toLowerCase().equals("gpg")?"GPG":"AES";
        // Get Cleversafe URL from Filename via Fire
        String[] filePath = getPath(fileName);
        if (filePath==null || filePath[0]== null) {
            throw new NotFoundException("Fire Error in obtaining URL for ", fileName);
        }
        String fileUrlString = filePath[0];
        long size = Long.valueOf(filePath[2]);

        // Get EgaFile encryption Key
        String encryptionKey = keyService.getFileKey(keyKey);

        // Build result object and return it
        return new ArchiveSource(fileUrlString, size, filePath[1], encryptionFormat, encryptionKey);
    }

    /*
     * Helper Functions [Legacy Code!] TODO - Rework this Code!
     */
    
    @HystrixCommand
    @Cacheable
    private String[] getPath(String path) {
        if (path.equalsIgnoreCase("Virtual File")) return new String[]{"Virtual File"};
        
        try {
            String[] result = new String[4]; // [0] name [1] stable_id [2] size [3] rel path
            result[0] = "";
            result[1] = "";
            result[3] = path;
            String path_ = path;

            // Sending Request
            HttpURLConnection connection = null;
            connection = (HttpURLConnection)(new URL(myFireConfig.getFireUrl())).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-FIRE-Archive", myFireConfig.getFireArchive());
            connection.setRequestProperty("X-FIRE-Key", myFireConfig.getFireKey());
            connection.setRequestProperty("X-FIRE-FilePath", path_);

            // Reading Response
            int responseCode = connection.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            ArrayList<String[]> paths = new ArrayList<>();
            
            String location_http = "", 
                   location_http_tag = "", 
                   location_md5 = "";
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.startsWith("HTTP_GET"))
                    location_http = inputLine.substring(inputLine.indexOf("http://")).trim();
                if (inputLine.startsWith("AUTH_BASIC"))
                    location_http_tag = inputLine.substring(inputLine.indexOf(" ")+1).trim();
                if (inputLine.startsWith("FILE_MD5")) {
                    location_md5 = inputLine.substring(inputLine.indexOf(" ")+1).trim();
                    paths.add(new String[]{location_http, location_http_tag, location_md5});
                }
            }
            in.close();

            if (paths.size() > 0) {
                for (int i=0; i<paths.size(); i++) {
                    String[] e = paths.get(i);
                    if (e[1].contains("egaread")) {
                        result[0] = e[0];
                        result[1] = e[1];
                        result[2] = String.valueOf(getLength(new String[]{location_http, location_http_tag}));
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            System.out.println("Path = " + path);
            System.out.println(e.getMessage());
            //e.printStackTrace();
        }            
        
        return null;
    }

    // Get the length of a file, from disk or Cleversafe server
    @HystrixCommand
    @Cacheable
    private long getLength(String[] path) {
        long result = -1;
        
        try {
            if (path[1] != null && path[1].length() == 0) { // Get size of file directly
                File f = new File(path[0]);
                result = f.length();
            } else { // Get file size from HTTP
                // Sending Request
                HttpURLConnection connection = null;
                connection = (HttpURLConnection)(new URL(path[0])).openConnection();
                connection.setRequestMethod("HEAD");

                String userpass = path[1];
                
                // Java bug : http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6459815
                String encoding = new sun.misc.BASE64Encoder().encode (userpass.getBytes());
                encoding = encoding.replaceAll("\n", "");  
                
                String basicAuth = "Basic " + encoding;
                connection.setRequestProperty ("Authorization", basicAuth);
                
                // Reading Response
                int responseCode = connection.getResponseCode();

                String headerField = connection.getHeaderField("content-length");
                String temp = headerField.trim();
                result = Long.parseLong(temp);

                connection.disconnect();
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }            
        
        return result;
    }
 
    // Downstream Helper Function - List supported ReEncryption Formats
    @HystrixCommand
    public String[] getEncryptionFormats() {
        return keyService.getFormats();
    }
}
