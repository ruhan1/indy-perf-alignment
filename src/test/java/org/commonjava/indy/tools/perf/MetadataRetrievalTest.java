/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.tools.perf;

import org.apache.http.client.HttpClient;
import org.junit.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.commonjava.indy.tools.perf.Utils.getArtifacts;
import static org.commonjava.indy.tools.perf.Utils.getHttpClient;
import static org.commonjava.indy.tools.perf.Utils.getMetadataPath;
import static org.commonjava.indy.tools.perf.Utils.getMetadata;

public class MetadataRetrievalTest

{
    private static final int so_timeout = new Long( TimeUnit.SECONDS.toMillis( 300 ) ).intValue();

    @Test
    public void test() throws Exception
    {
        String indyUrl = System.getProperty( "indyUrl" ); // -DindyUrl
        System.out.println( "Use indyUrl: " + indyUrl );

        String artifacts = getArtifactsBy( System.getProperty( "artifacts" ) );

        int limit = Integer.parseInt( System.getProperty( "limit", "10" ) ); // e.g., -Dlimit=20
        System.out.println( "Use limit: " + limit );

        HttpClient client = getHttpClient( so_timeout );

        Set<String> artifactSet = getArtifacts( limit, artifacts, client );
        System.out.println( "Get artifacts, size: " + artifactSet.size() );

        Set<String> metadataPaths = artifactSet.stream()
                                               .map( artifact -> getMetadataPath( artifact ) )
                                               .collect( Collectors.toSet() );

        long begin = currentTimeMillis();
        System.out.println( "Starts: " + new Date( begin ) );
        List<String> list = retrieveAll( metadataPaths, indyUrl, client );
        long end = currentTimeMillis();
        System.out.println( "\nResult:" );
        list.forEach( s -> System.out.println( s ) );
        System.out.println( "\nEnds: " + new Date( end ) );
        System.out.println( "\nElapse(s): " + ( end - begin ) / 1000 );

        // Retry failed
        Set<String> failedPaths = list.stream()
                                      .filter( s -> !s.contains( "200" ) )
                                      .map( s -> s.substring( 0, s.indexOf( "=" ) ).trim() )
                                      .collect( Collectors.toSet() );
        if ( !failedPaths.isEmpty() )
        {
            System.out.println( "\nRetry failed (" + failedPaths.size() + "):" );
            list = retrieveAll( failedPaths, indyUrl, client );
            System.out.println( "\nResult:" );
            list.forEach( s -> System.out.println( s ) );
        }
    }

    // Get artifacts from 1. built-in file, 2. build id, 3. full url
    private String getArtifactsBy( String artifacts )
    {
        try
        {
            int buildId = Integer.parseInt( artifacts );
            artifacts = String.format( "http://orch.psi.redhat.com/pnc-rest/rest/build-records/%s/repour-log",
                                       buildId );
        }
        catch ( NumberFormatException ex )
        {
            // not build id
        }
        System.out.println( "Use artifacts: " + artifacts );
        return artifacts;
    }

    private List<String> retrieveAll( Set<String> metadataPaths, String indyUrl, HttpClient client )
    {
        Set<CompletableFuture<String>> futures = new HashSet<>();
        metadataPaths.forEach( path -> futures.add( supplyAsync( () -> getMetadata( indyUrl, path, client ) ) ) );
        System.out.println( "Futures: " + futures.size() );
        return futures.stream().map( f -> f.join() ).collect( Collectors.toList() );
    }

}
