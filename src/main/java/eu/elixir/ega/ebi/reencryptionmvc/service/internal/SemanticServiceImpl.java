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

import eu.elixir.ega.ebi.egacipher.EgaFakeSeekableStream;
import eu.elixir.ega.ebi.egacipher.EgaGpgStream;
import eu.elixir.ega.ebi.egacipher.EgaSeekableCipherStream;
import eu.elixir.ega.ebi.egacipher.EgaSeekableHTTPStream;
import eu.elixir.ega.ebi.reencryptionmvc.config.GeneralStreamingException;
import eu.elixir.ega.ebi.reencryptionmvc.dto.ArchiveSource;
import eu.elixir.ega.ebi.reencryptionmvc.service.KeyService;
import eu.elixir.ega.ebi.reencryptionmvc.service.SemanticService;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.seekablestream.SeekableHTTPStream;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchProviderException;
import java.security.Security;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author asenf
 */
@Service
public class SemanticServiceImpl implements SemanticService {

    @Autowired
    private KeyService keyService;
    
    /**
     * Size of a byte buffer to read/write file (for Random Stream)
     */
    private static final int BUFFER_SIZE = 4096;

    @Override
    public SAMFileHeader getHeader(ArchiveSource source,
                            HttpServletRequest request,
                            HttpServletResponse response) {
        
        SAMFileHeader fileHeader = null;
        
        // Extract the Header, Return it...
        try {
            SeekableStream cIn = getSource(source.getEncryptionFormat(),
                                           source.getEncryptionKey(),
                                           source.getFileUrl(),
                                           source.getAuth(),
                                           source.getSize());
            if (cIn==null) {
                throw new GeneralStreamingException("Input Stream (Decryption Stage) Null", 1);
            }
            
            // Open Reader with decrypted Input Stream
            SamReaderFactory samReaderFactory = 
                    SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT);
            SamInputResource samInputResource = SamInputResource.of(cIn);
            SamReader samReader = samReaderFactory.open(samInputResource);
            
            // Get Header
            fileHeader = samReader.getFileHeader();
            
        } catch (Throwable t) {}

        return fileHeader;
    }

    // Return Unencrypted Seekable Stream from Source
    private SeekableStream getSource(String sourceFormat, 
                                String sourceKey, 
                                String fileLocation,
                                String httpAuth,
                                long fileSize) {

        SeekableStream fileIn = null; // Source of File
        SeekableStream plainIn = null; // Return Stream - a Decrypted File
        try {
            // Obtain Input Stream - from a File or an HTTP server
            if (!fileLocation.toLowerCase().startsWith("http")) {
                fileLocation = "file://" + fileLocation;
                Path filePath = Paths.get(new URI(fileLocation));
                fileIn = new SeekablePathStream(filePath);
            } else { // Need Basic Auth here!
                URL url = new URL(fileLocation);
                fileIn = httpAuth==null?new SeekableHTTPStream(url):
                                        new EgaSeekableHTTPStream(url, null, httpAuth, fileSize);
            }
            
            // Obtain Plain Input Stream
            if (sourceFormat.equalsIgnoreCase("plain")) {
                plainIn = fileIn; // No Decryption Necessary
            } else if (sourceFormat.equalsIgnoreCase("aes128")) {
                plainIn = new EgaSeekableCipherStream(fileIn, sourceKey.toCharArray(), BUFFER_SIZE, 128);
            } else if (sourceFormat.equalsIgnoreCase("aes256")) {
                plainIn = new EgaSeekableCipherStream(fileIn, sourceKey.toCharArray(), BUFFER_SIZE, 256);
            } else if (sourceFormat.equalsIgnoreCase("symmetricgpg")) {
                plainIn = getSymmetricGPGDecryptingInputStream(fileIn, sourceKey);
            }
        } catch (IOException | URISyntaxException ex) {
            System.out.println(" ** " + ex.toString());
        }

        return plainIn;
    }
    

    /*
     * Archive Related Helper Functions -- GPG
     */
    
    private SeekableStream getSymmetricGPGDecryptingInputStream(InputStream c_in, String sourceKey) {
        Security.addProvider(new BouncyCastleProvider());
        InputStream in = c_in;

        try {
            // Load key, if not provided. Details in config XML file
            if (sourceKey==null||sourceKey.length()==0) {
                String[] keyPath = keyService.getKeyPath("SymmetricGPG");
                BufferedReader br = new BufferedReader(new FileReader(keyPath[0]));
                sourceKey = br.readLine();
                br.close();
            }
        
            in = EgaGpgStream.getDecodingGPGInoutStream(in, sourceKey.toCharArray());
            
        } catch (IOException | PGPException | NoSuchProviderException ex) {
            System.out.println("GPG Error " + ex.toString());
        }
        
        return new EgaFakeSeekableStream(in);
    }
}
