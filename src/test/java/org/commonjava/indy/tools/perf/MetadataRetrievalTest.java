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
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Integer.min;
import static java.util.concurrent.CompletableFuture.supplyAsync;

public class MetadataRetrievalTest

{
    private static final String INDY_URL =
                    "http://indy-admin-stage.psi.redhat.com/api/content/maven/group/DA-temporary-builds";

    @Test
    public void test() throws Exception
    {
        String indyUrl = System.getProperty( "indyUrl", INDY_URL ); // -DindyUrl
        System.out.println( "Use indyUrl: " + indyUrl );

        int artifactsCount =
                        Integer.parseInt( System.getProperty( "artifactsCount", "10" ) ); // e.g., -DartifactsCount=20
        System.out.println( "Use artifactsCount: " + artifactsCount );

        Set<String> artifacts = new HashSet<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream( "artifacts" ))
        {
            String[] lines = IOUtil.toString( in ).split( "\n" );
            for ( int i = 0; i < min( artifactsCount, lines.length ); i++ )
            {
                artifacts.add( lines[i] );
            }
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            return;
        }

        Set<String> metadataPaths =
                        artifacts.stream().map( artifact -> getMetadataPath( artifact ) ).collect( Collectors.toSet() );

        HttpClient client = getHttpClient();

        long begin = System.currentTimeMillis();
        System.out.println( "Starts: " + new Date( begin ) );
        Set<CompletableFuture<String>> futures = new HashSet<>();
        metadataPaths.forEach( path -> futures.add( supplyAsync( () -> getMetadata( indyUrl, path, client ) ) ) );

        List<String> list = futures.stream().map( f -> f.join() ).collect( Collectors.toList() );

        long end = System.currentTimeMillis();
        System.out.println( "\nResult:" );
        list.forEach( s -> System.out.println( s ) );
        System.out.println( "\nEnds: " + new Date( end ) );
        System.out.println( "\nElapse(s): " + ( end - begin ) / 1000 );

    }

    private HttpClient getHttpClient()
    {
        int timeout = new Long( TimeUnit.SECONDS.toMillis( 30 ) ).intValue(); // 30 sec
        RequestConfig requestConfig =
                        RequestConfig.custom().setConnectTimeout( timeout ).setSocketTimeout( timeout ).build();
        HttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig( requestConfig ).build();
        return httpClient;
    }

    private String entityToString( HttpResponse response ) throws IOException
    {
        HttpEntity entity = response.getEntity();
        if ( entity != null )
        {
            return EntityUtils.toString( entity );
        }
        return null;
    }

    private String getMetadata( String indyUrl, String path, HttpClient client )
    {
        String ret;
        HttpGet request = new HttpGet( indyUrl + "/" + path );
        try
        {
            HttpResponse response = client.execute( request );
            StatusLine sl = response.getStatusLine();
            if ( sl.getStatusCode() == 200 )
            {
                String content = entityToString( response );
                System.out.println( "Got metadata: " + path );
            }
            else
            {
                System.out.println( "Failed: " + path );
            }
            ret = sl.toString();
        }
        catch ( final IOException e )
        {
            e.printStackTrace();
            ret = e.getMessage();
        }
        return path + " => " + ret;
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
