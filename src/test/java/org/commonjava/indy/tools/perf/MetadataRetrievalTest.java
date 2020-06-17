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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Integer.min;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptySet;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

public class MetadataRetrievalTest

{
    private static final int timeout = new Long( TimeUnit.SECONDS.toMillis( 300 ) ).intValue();

    @Test
    public void test() throws Exception
    {
        String indyUrl = System.getProperty( "indyUrl" ); // -DindyUrl
        System.out.println( "Use indyUrl: " + indyUrl );

        // Get artifacts from 1. built-in file, 2. url, e.g., http://orch.psi.redhat.com/pnc-rest/rest/build-records/53917/repour-log
        String artifacts = System.getProperty( "artifacts" );
        System.out.println( "Use artifacts: " + artifacts );

        int limit = Integer.parseInt( System.getProperty( "limit", "10" ) ); // e.g., -Dlimit=20
        System.out.println( "Use limit: " + limit );

        HttpClient client = getHttpClient();

        Set<String> artifactSet = getArtifacts( limit, artifacts, client );
        System.out.println( "Get artifacts, size: " + artifactSet.size() );

        Set<String> metadataPaths = artifactSet.stream()
                                               .map( artifact -> getMetadataPath( artifact ) )
                                               .collect( Collectors.toSet() );

        long begin = currentTimeMillis();
        System.out.println( "Starts: " + new Date( begin ) );
        Set<CompletableFuture<String>> futures = new HashSet<>();
        metadataPaths.forEach( path -> futures.add( supplyAsync( () -> getMetadata( indyUrl, path, client ) ) ) );

        System.out.println( "Futures: " + futures.size() );
        List<String> list = futures.stream().map( f -> f.join() ).collect( Collectors.toList() );

        long end = currentTimeMillis();
        System.out.println( "\nResult:" );
        list.forEach( s -> System.out.println( s ) );
        System.out.println( "\nEnds: " + new Date( end ) );
        System.out.println( "\nElapse(s): " + ( end - begin ) / 1000 );

    }

    private Set<String> getArtifacts( int limit, String resource, HttpClient client ) throws IOException
    {
        if ( resource.startsWith( "http://" ) )
        {
            return fetchArtifactsFromUrl( resource, limit, client );
        }
        else
        {
            return fetchArtifactsFromResource( resource, limit );
        }
    }

    private Set<String> fetchArtifactsFromResource( String resource, int limit ) throws IOException
    {
        Set<String> artifacts = new HashSet<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream( resource ))
        {
            String[] lines = IOUtil.toString( in ).split( "\n" );
            for ( int i = 0; i < min( limit, lines.length ); i++ )
            {
                String line = lines[i];
                if ( isNotBlank( line ) && !line.startsWith( "#" ) )
                {
                    int index = line.indexOf( "=" );
                    if ( index > 0 )
                    {
                        line = line.substring( 0, index );
                    }
                    artifacts.add( line );
                }
            }
        }
        return artifacts;
    }

    private Set<String> fetchArtifactsFromUrl( String url, int limit, HttpClient client ) throws IOException
    {
        HttpGet request = new HttpGet( url );
        HttpResponse response = client.execute( request );
        StatusLine sl = response.getStatusLine();
        String content;
        if ( sl.getStatusCode() == 200 )
        {
            content = entityToString( response );
        }
        else
        {
            System.out.println( "Failed to fetch artifacts, url: " + url );
            return emptySet();
        }

        String[] lines = content.split( "\\n" );
        String artifactsLine = null;
        for ( String line : lines )
        {
            int index = line.indexOf( "REST Client returned" );
            if ( index > 0 )
            {
                artifactsLine = line.substring( line.indexOf( "{" ) + 1, line.indexOf( "}" ) );
                break;
            }
        }

        if ( artifactsLine == null )
        {
            System.out.println( "Not find the line" );
            return emptySet();
        }

        return Arrays.stream( artifactsLine.split( "," ) )
                     .map( s -> s.substring( 0, s.indexOf( "=" ) ) )
                     .collect( Collectors.toSet() );
    }

    private HttpClient getHttpClient()
    {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal( 5 );
        connManager.setDefaultMaxPerRoute( 5 );
        connManager.setDefaultSocketConfig( SocketConfig.custom().setSoTimeout( timeout ).build() );
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager( connManager ).build();
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
        long begin = currentTimeMillis();
        String ret;
        HttpGet request = new HttpGet( indyUrl + "/" + path );
        try
        {
            HttpResponse response = client.execute( request );
            StatusLine sl = response.getStatusLine();
            if ( sl.getStatusCode() == 200 )
            {
                String content = entityToString( response );
                System.out.println( "Got: " + path );
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
        finally
        {
            request.releaseConnection();
        }
        return path + " => " + ret + " (" + ( currentTimeMillis() - begin ) + ")";
    }

    /**
     * Get maven-metadata.xml path of given artifact.
     * e.g., org.keycloak:keycloak-server-spi:2.4.0.Final -> org/keycloak/keycloak-server-spi/maven-metadata.xml
     * org.apache.geronimo.specs:geronimo-j2ee-connector_1.5_spec:2.0.0 -> org/apache/geronimo/specs/geronimo-j2ee-connector_1.5_spec/maven-metadata.xml
     */
    private String getMetadataPath( String artifact )
    {
        String[] toks = artifact.split( ":" );
        String groupId = toks[0];
        String artifactId = toks[1];
        return groupId.replaceAll( "\\.", "/" ) + "/" + artifactId + "/maven-metadata.xml";
    }

}
