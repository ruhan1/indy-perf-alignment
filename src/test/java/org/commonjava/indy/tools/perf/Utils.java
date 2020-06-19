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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Integer.min;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptySet;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

public class Utils
{

    static Set<String> getArtifacts( int limit, String resource, HttpClient client ) throws IOException
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

    private static Set<String> fetchArtifactsFromResource( String resource, int limit ) throws IOException
    {
        Set<String> artifacts = new HashSet<>();
        try (InputStream in = Utils.class.getClassLoader().getResourceAsStream( resource ))
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

    private static Set<String> fetchArtifactsFromUrl( String url, int limit, HttpClient client ) throws IOException
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
                     .map( s -> s.substring( 0, s.indexOf( "=" ) ).trim() )
                     .collect( Collectors.toSet() );
    }

    static HttpClient getHttpClient( int timeout )
    {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal( 5 );
        connManager.setDefaultMaxPerRoute( 5 );
        connManager.setDefaultSocketConfig( SocketConfig.custom().setSoTimeout( timeout ).build() );
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager( connManager ).build();
        return httpClient;
    }

    private static String entityToString( HttpResponse response ) throws IOException
    {
        HttpEntity entity = response.getEntity();
        if ( entity != null )
        {
            return EntityUtils.toString( entity );
        }
        return null;
    }

    static String getMetadata( String indyUrl, String path, HttpClient client )
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
    static String getMetadataPath( String artifact )
    {
        String[] toks = artifact.split( ":" );
        String groupId = toks[0];
        String artifactId = toks[1];
        return groupId.replaceAll( "\\.", "/" ) + "/" + artifactId + "/maven-metadata.xml";
    }

}
