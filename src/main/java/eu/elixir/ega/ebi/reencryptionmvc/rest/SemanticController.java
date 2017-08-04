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
package eu.elixir.ega.ebi.reencryptionmvc.rest;

import eu.elixir.ega.ebi.reencryptionmvc.config.NotFoundException;
import eu.elixir.ega.ebi.reencryptionmvc.dto.ArchiveSource;
import eu.elixir.ega.ebi.reencryptionmvc.service.ArchiveService;
import eu.elixir.ega.ebi.reencryptionmvc.service.SemanticService;
import htsjdk.samtools.SAMFileHeader;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 *
 * @author asenf
 */
@RestController
@RequestMapping("/ga4gh")
public class SemanticController {
    
    // Handle Any Direct Re/Encryption Operations
    @Autowired
    private SemanticService semanticService;

    // Handle Archived File Operations (file identified by Archive ID)
    @Autowired
    private ArchiveService archiveService;    
    
    
    @RequestMapping(value = "/{id}/header", method = GET)
    public SAMFileHeader get(@PathVariable String id,
                      HttpServletRequest request,
                      HttpServletResponse response) {
        // Resolve Archive ID to actual File Path/URL - Needs Organization-Specific Implementation!
        ArchiveSource source = archiveService.getArchiveFile(id);
        if (source==null) {
            throw new NotFoundException("Archive File not found, id", id);
        }
        
        // Extract the header
        return semanticService.getHeader(source,
                                         request,
                                         response);
    }
    
}
