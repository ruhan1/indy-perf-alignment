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

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class MetadataRetrievalTest

{
    private static final String INDY_URL = "http://indy-admin-stage.psi.redhat.com/api/content/maven/group/DA/";

    @Ignore
    @Test
    public void test() throws Exception
    {
        Set<String> artifacts = null;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream( "artifacts" ))
        {
            String[] lines = IOUtil.toString( in ).split( "\n" );
            artifacts = new HashSet<>( Arrays.asList( lines ) );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }

        CloseableHttpClient client = HttpClients.createDefault();

        Set<CompletableFuture<String>> futures = new HashSet<>();
        artifacts.forEach( artifact -> {
            CompletableFuture<String> f = supplyAsync( () -> getMetadataPath( artifact ) ).thenApplyAsync(
                            path -> getMetadata( path, client ) );
            futures.add( f );
        } );
        List<String> list = futures.stream().map( f -> f.join() ).collect( Collectors.toList() );

        list.forEach( s -> System.out.println( s ) );
    }

    private String entityToString( CloseableHttpResponse response ) throws IOException
    {
        HttpEntity entity = response.getEntity();
        if ( entity != null )
        {
            return EntityUtils.toString( entity );
        }
        return null;
    }

    private String getMetadata( String path, CloseableHttpClient client )
    {
        HttpGet request = new HttpGet( INDY_URL + path );
        try
        {
            CloseableHttpResponse response = client.execute( request );
            StatusLine sl = response.getStatusLine();
            if ( sl.getStatusCode() != 200 )
            {
                return sl.toString();
            }
            String content = entityToString( response );
            //logger.debug( "Got metadata: {}", content );
            return content;
        }
        catch ( final IOException e )
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get maven-metadata.xml path of given artifact.
     * e.g., org.keycloak:keycloak-server-spi:2.4.0.Final -> org/keycloak/keycloak-server-spi/maven-metadata.xml
     */
    private String getMetadataPath( String artifact )
    {
        return artifact.substring( 0, artifact.lastIndexOf( ":" ) ).replaceAll( "(\\.|:)", "/" )
                        + "/maven-metadata.xml";
    }

}
