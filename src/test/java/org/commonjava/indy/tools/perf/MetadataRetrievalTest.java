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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
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
import static org.junit.Assert.assertTrue;

public class MetadataRetrievalTest

{
    private final int so_timeout = new Long( TimeUnit.SECONDS.toMillis( 300 ) ).intValue();

    private final int limit = Integer.MAX_VALUE;

    private String indyUrl;

    private HttpClient client;

    @Before
    public void prepare()
    {
        indyUrl = System.getProperty( "indyUrl" ); // -DindyUrl
        client = getHttpClient( so_timeout );
    }

    @Test
    public void test() throws Exception
    {
        System.out.println( "Use indyUrl: " + indyUrl );

        List<String> artifacts = getArtifactsBy( System.getProperty( "artifacts" ) );

        boolean success = true;
        List<String> ret = new ArrayList<>();
        for ( String artifact : artifacts )
        {
            if ( run( artifact ) )
            {
                ret.add( "OK " + artifact );
            }
            else
            {
                ret.add( "FAILED " + artifact );
                success = false;
            }
        }
        System.out.println( "\n------ Bottom line ------\n" );
        for ( String s : ret )
        {
            System.out.println( s );
        }
        assertTrue( success );
    }

    // consider pass if no one failed, or failed on same paths after retry
    private boolean run( String artifact ) throws IOException
    {
        System.out.println( "\n\n Run " + artifact );

        Set<String> artifactSet = getArtifacts( limit, artifact, client );
        System.out.println( "Get artifacts, size: " + artifactSet.size() );

        Set<String> metadataPaths = artifactSet.stream().map( s -> getMetadataPath( s ) ).collect( Collectors.toSet() );

        long begin = currentTimeMillis();
        //System.out.println( "Starts: " + new Date( begin ) );
        List<String> list = retrieveAll( metadataPaths );
        long end = currentTimeMillis();
        System.out.println( "\nResult:" );
        list.forEach( s -> System.out.println( s ) );
        //System.out.println( "\nEnds: " + new Date( end ) );
        System.out.println( "\nElapse(s): " + ( end - begin ) / 1000 );

        // Retry failed
        Set<String> failedPaths = list.stream()
                                      .filter( s -> !s.contains( "200" ) )
                                      .map( s -> s.substring( 0, s.indexOf( "=" ) ).trim() )
                                      .collect( Collectors.toSet() );
        if ( !failedPaths.isEmpty() )
        {
            System.out.println( "\nRetry failed (" + failedPaths.size() + "):" );
            list = retrieveAll( failedPaths );
            System.out.println( "\nResult:" );
            list.forEach( s -> System.out.println( s ) );
        }

        return failedPaths.isEmpty() || failedPaths.size() == list.size();
    }

    // Get artifacts from 1. built-in file, 2. build id, 3. full url. Or a group of them separated by comma.
    private List<String> getArtifactsBy( String artifacts )
    {
        List<String> ret = new ArrayList<>();
        String[] toks = artifacts.split( "," );
        for ( String s : toks )
        {
            s = s.trim();
            try
            {
                int buildId = Integer.parseInt( s );
                s = String.format( "http://orch.psi.redhat.com/pnc-rest/rest/build-records/%s/repour-log", buildId );
            }
            catch ( NumberFormatException ex )
            {
                // not build id
            }
            ret.add( s );
        }
        System.out.println( "Use artifacts: " + ret );
        return ret;
    }

    private List<String> retrieveAll( Set<String> metadataPaths )
    {
        Set<CompletableFuture<String>> futures = new HashSet<>();
        metadataPaths.forEach( path -> futures.add( supplyAsync( () -> getMetadata( indyUrl, path, client ) ) ) );
        System.out.println( "Futures: " + futures.size() );
        return futures.stream().map( f -> f.join() ).collect( Collectors.toList() );
    }

}
