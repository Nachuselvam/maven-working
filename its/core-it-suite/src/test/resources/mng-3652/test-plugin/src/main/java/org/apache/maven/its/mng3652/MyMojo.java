package org.apache.maven.its.mng3652;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

/**
 * Goal which attempts to download a dummy artifact from a repository on localhost
 * at the specified port. This is used to allow the unit test class to record the
 * User-Agent HTTP header in use. It will also write the maven version in use to
 * a file in the output directory, for comparison in the unit tests assertions.
 *
 * @goal touch
 * 
 * @phase validate
 */
public class MyMojo
    extends AbstractMojo
{
    
    private static final String LS = System.getProperty( "line.separator" );
    
    /**
     * @parameter default-value="${project.build.directory}/touch.txt"
     */
    private File touchFile;
    
    /**
     * @component
     */
    private ArtifactResolver resolver;
    
    /**
     * @component
     */
    private ArtifactFactory artifactFactory;
    
    /**
     * @component
     */
    private ArtifactRepositoryFactory repositoryFactory;
    
    /**
     * @component
     */
    private ArtifactRepositoryLayout layout;
    
    /**
     * @component
     */
    private RuntimeInformation runtimeInformation;
    
    /**
     * @parameter expression="${testProtocol}" default-value="http"
     * @required
     */
    private String testProtocol;
    
    /**
     * @parameter expression="${testPort}"
     * @required
     */
    private String testPort;
    
    /**
     * @parameter default-value="${project.build.directory}/local-repo"
     * @required
     * @readonly
     */
    private File localRepoDir;
    
    public void execute()
        throws MojoExecutionException
    {
        ArtifactRepositoryPolicy policy = new ArtifactRepositoryPolicy();
        policy.setChecksumPolicy( ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE );
        policy.setUpdatePolicy( ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS );
        policy.setEnabled( true );

        ArtifactRepository remote =
            repositoryFactory.createArtifactRepository( "test", testProtocol + "://127.0.0.1:" + testPort, layout,
                                                        policy, policy );
        
        Artifact artifact = artifactFactory.createArtifact( "bad.group", "missing-artifact", "1", null, "jar" );

        try
        {
            FileUtils.deleteDirectory( localRepoDir );

            ArtifactRepository local =
                repositoryFactory.createArtifactRepository( "local", localRepoDir.toURL().toExternalForm(), layout,
                                                            null, null );
        
            getLog().info( "Retrieving " + artifact + " from " + remote + " to " + local );

            resolver.resolveAlways( artifact, Collections.singletonList( remote ), local );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        try
        {
            String content = FileUtils.fileRead( artifact.getFile() );
            if ( !content.equals( "some content\n\n" ) )
            {
                throw new MojoExecutionException( "Expected 'some content\n\n' but was '" + content + "'" );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        
        String artifactVersion;
        InputStream resourceAsStream = null;
        try
        {
            Properties properties = new Properties();
            resourceAsStream = Artifact.class.getClassLoader().getResourceAsStream( "META-INF/maven/org.apache.maven/maven-artifact/pom.properties" );
            if ( resourceAsStream == null )
            {
                resourceAsStream = Artifact.class.getClassLoader().getResourceAsStream( "META-INF/maven/org.apache.maven.artifact/maven-artifact/pom.properties" );
            }
            properties.load( resourceAsStream );

            artifactVersion = properties.getProperty( "version" );
            if ( artifactVersion == null )
            {
                throw new MojoExecutionException( "Artifact version not found" );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to read properties file from maven-core", e );
        }
        finally
        {
            IOUtil.close( resourceAsStream );
        }
        
        FileWriter w = null;
        try
        {
            touchFile.getParentFile().mkdirs();
            w = new FileWriter( touchFile );
            
            w.write( runtimeInformation.getApplicationVersion().toString() );
            w.write( LS );
            w.write( System.getProperty( "java.version" ) );
            w.write( LS );
            w.write( System.getProperty( "os.name" ) );
            w.write( LS );
            w.write( System.getProperty( "os.version" ) );
            w.write( LS );
            w.write( artifactVersion );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Failed to write touch-file: " + touchFile, e );
        }
        finally
        {
            IOUtil.close( w );
        }
    }
}
